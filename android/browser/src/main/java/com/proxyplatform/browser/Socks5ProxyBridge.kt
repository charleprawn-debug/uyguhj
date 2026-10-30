package com.proxyplatform.browser

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Presents an unauthenticated loopback SOCKS5 endpoint to GeckoView and forwards
 * every CONNECT through the configured upstream SOCKS5 server. Authentication
 * is performed here because GeckoView does not provide SOCKS5 user/password UI.
 */
internal class Socks5ProxyBridge(
    private val upstreamHost: String,
    private val upstreamPort: Int,
    private val username: String,
    private val password: String,
) : AutoCloseable {
    enum class Failure { UPSTREAM, AUTHENTICATION, DESTINATION, PROTOCOL }

    private val running = AtomicBoolean(false)
    private val lifecycle = Any()
    private val io = Executors.newCachedThreadPool()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val failuresByHost = ConcurrentHashMap<String, Failure>()
    @Volatile private var server: ServerSocket? = null

    val localPort: Int get() = server?.localPort ?: 0

    fun start(): Int = synchronized(lifecycle) {
        check(server == null && !io.isShutdown) { "SOCKS5 bridge already started or closed" }
        val listener = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress("127.0.0.1", 0), 32)
        }
        server = listener
        running.set(true)
        io.execute { acceptLoop(listener) }
        listener.localPort
    }

    fun clearFailures() = failuresByHost.clear()

    fun failureMessageFor(host: String?): String? {
        val key = host?.trim()?.trim('[', ']')?.lowercase()?.takeIf(String::isNotBlank) ?: return null
        return when (failuresByHost[key]) {
            Failure.UPSTREAM -> "تعذّر الوصول إلى خادم SOCKS5. تحقق من عنوان البروكسي والمنفذ والإنترنت."
            Failure.AUTHENTICATION -> "يرفض بروكسي SOCKS5 بيانات الدخول أو يتطلب اسم مستخدم وكلمة مرور. راجع إعدادات الملف."
            Failure.DESTINATION -> "اتصل المتصفح بالبروكسي، لكن البروكسي رفض الموقع المطلوب. جرّب موقعًا آخر أو راجع مزود البروكسي."
            Failure.PROTOCOL -> "ردّ البروكسي بصيغة غير متوافقة مع SOCKS5. تحقق من نوع البروكسي المختار."
            null -> null
        }
    }

    private fun acceptLoop(listener: ServerSocket) {
        while (running.get()) {
            try {
                val client = listener.accept().apply { tcpNoDelay = true }
                synchronized(lifecycle) {
                    if (!running.get()) {
                        runCatching { client.close() }
                    } else {
                        sockets.add(client)
                        try {
                            io.execute { handleClient(client) }
                        } catch (_: RejectedExecutionException) {
                            sockets.remove(client)
                            runCatching { client.close() }
                        }
                    }
                }
            } catch (_: SocketException) {
                break
            } catch (_: Exception) {
                if (!running.get()) break
            }
        }
    }

    private fun handleClient(client: Socket) {
        var upstream: Socket? = null
        var localNegotiated = false
        var localReplySent = false
        var currentFailure = Failure.UPSTREAM
        var destinationHost: String? = null
        try {
            client.soTimeout = HANDSHAKE_TIMEOUT_MS
            val clientIn = DataInputStream(BufferedInputStream(client.getInputStream()))
            val clientOut = DataOutputStream(BufferedOutputStream(client.getOutputStream()))

            val greetingVersion = clientIn.readUnsignedByte()
            val methodCount = clientIn.readUnsignedByte()
            val methods = ByteArray(methodCount)
            clientIn.readFully(methods)
            if (greetingVersion != SOCKS_VERSION || NO_AUTH.toByte() !in methods) {
                clientOut.write(byteArrayOf(SOCKS_VERSION.toByte(), NO_ACCEPTABLE_METHOD.toByte()))
                clientOut.flush()
                return
            }
            clientOut.write(byteArrayOf(SOCKS_VERSION.toByte(), NO_AUTH.toByte()))
            clientOut.flush()
            localNegotiated = true

            val requestVersion = clientIn.readUnsignedByte()
            val command = clientIn.readUnsignedByte()
            val reserved = clientIn.readUnsignedByte()
            val addressType = clientIn.readUnsignedByte()
            if (requestVersion != SOCKS_VERSION || reserved != 0 || command != CONNECT) {
                writeLocalReply(clientOut, if (command == CONNECT) GENERAL_FAILURE else COMMAND_NOT_SUPPORTED)
                localReplySent = true
                currentFailure = Failure.PROTOCOL
                return
            }
            val destination = readAddress(clientIn, addressType)
            destinationHost = decodeDestinationHost(addressType, destination)
            val destinationPort = clientIn.readUnsignedShort()
            val request = byteArrayOf(SOCKS_VERSION.toByte(), CONNECT.toByte(), 0, addressType.toByte()) +
                destination + byteArrayOf((destinationPort ushr 8).toByte(), destinationPort.toByte())

            currentFailure = Failure.UPSTREAM
            upstream = Socket().apply { tcpNoDelay = true }
            sockets.add(upstream)
            upstream.connect(InetSocketAddress(upstreamHost, upstreamPort), CONNECT_TIMEOUT_MS)
            upstream.soTimeout = HANDSHAKE_TIMEOUT_MS
            val upstreamIn = DataInputStream(BufferedInputStream(upstream.getInputStream()))
            val upstreamOut = DataOutputStream(BufferedOutputStream(upstream.getOutputStream()))

            val hasCredentials = username.isNotEmpty() || password.isNotEmpty()
            val offeredMethods = if (hasCredentials) {
                byteArrayOf(USER_PASSWORD.toByte(), NO_AUTH.toByte())
            } else {
                byteArrayOf(NO_AUTH.toByte())
            }
            upstreamOut.write(byteArrayOf(SOCKS_VERSION.toByte(), offeredMethods.size.toByte()) + offeredMethods)
            upstreamOut.flush()
            val upstreamVersion = upstreamIn.readUnsignedByte()
            val selectedMethod = upstreamIn.readUnsignedByte()
            if (upstreamVersion != SOCKS_VERSION) {
                currentFailure = Failure.PROTOCOL
                throw BridgeException()
            }
            if (selectedMethod == NO_ACCEPTABLE_METHOD) {
                currentFailure = if (hasCredentials) Failure.PROTOCOL else Failure.AUTHENTICATION
                throw BridgeException()
            }
            when (selectedMethod) {
                NO_AUTH -> Unit
                USER_PASSWORD -> {
                    currentFailure = Failure.AUTHENTICATION
                    val userBytes = username.toByteArray(Charsets.UTF_8)
                    val passwordBytes = password.toByteArray(Charsets.UTF_8)
                    if (!hasCredentials || userBytes.isEmpty() || userBytes.size > 255 || passwordBytes.size > 255) throw BridgeException()
                    upstreamOut.writeByte(AUTH_VERSION)
                    upstreamOut.writeByte(userBytes.size)
                    upstreamOut.write(userBytes)
                    upstreamOut.writeByte(passwordBytes.size)
                    upstreamOut.write(passwordBytes)
                    upstreamOut.flush()
                    if (upstreamIn.readUnsignedByte() != AUTH_VERSION || upstreamIn.readUnsignedByte() != 0) throw BridgeException()
                }
                else -> {
                    currentFailure = Failure.PROTOCOL
                    throw BridgeException()
                }
            }

            currentFailure = Failure.PROTOCOL
            upstreamOut.write(request)
            upstreamOut.flush()
            val reply = readReply(upstreamIn)
            clientOut.write(reply)
            clientOut.flush()
            localReplySent = true
            if ((reply[1].toInt() and 0xff) != SUCCEEDED) {
                recordFailure(destinationHost, Failure.DESTINATION)
                return
            }

            normalizeHost(destinationHost)?.let(failuresByHost::remove)
            client.soTimeout = 0
            upstream.soTimeout = 0
            currentFailure = Failure.DESTINATION
            relay(client, upstream)
        } catch (_: Exception) {
            recordFailure(destinationHost, currentFailure)
            if (localNegotiated && !localReplySent) {
                runCatching {
                    writeLocalReply(DataOutputStream(BufferedOutputStream(client.getOutputStream())), GENERAL_FAILURE)
                }
            }
        } finally {
            sockets.remove(client)
            runCatching { client.close() }
            upstream?.let {
                sockets.remove(it)
                runCatching { it.close() }
            }
        }
    }

    /** Returns the encoded address payload only, preserving domain names for upstream DNS. */
    private fun readAddress(input: DataInputStream, addressType: Int): ByteArray = when (addressType) {
        IPV4 -> ByteArray(4).also(input::readFully)
        DOMAIN -> {
            val length = input.readUnsignedByte()
            if (length == 0) throw BridgeException()
            byteArrayOf(length.toByte()) + ByteArray(length).also(input::readFully)
        }
        IPV6 -> ByteArray(16).also(input::readFully)
        else -> throw BridgeException()
    }

    private fun decodeDestinationHost(addressType: Int, payload: ByteArray): String = when (addressType) {
        IPV4 -> InetAddress.getByAddress(payload).hostAddress
        DOMAIN -> String(payload, 1, payload[0].toInt() and 0xff, Charsets.US_ASCII)
        IPV6 -> InetAddress.getByAddress(payload).hostAddress
        else -> throw BridgeException()
    }

    private fun readReply(input: DataInputStream): ByteArray {
        val header = ByteArray(4)
        input.readFully(header)
        if ((header[0].toInt() and 0xff) != SOCKS_VERSION || (header[2].toInt() and 0xff) != 0) throw BridgeException()
        val address = when (header[3].toInt() and 0xff) {
            IPV4 -> ByteArray(4).also(input::readFully)
            DOMAIN -> ByteArray(1).also(input::readFully).let { length -> length + ByteArray(length[0].toInt() and 0xff).also(input::readFully) }
            IPV6 -> ByteArray(16).also(input::readFully)
            else -> throw BridgeException()
        }
        val port = ByteArray(2).also(input::readFully)
        return header + address + port
    }

    private fun writeLocalReply(output: DataOutputStream, status: Int) {
        output.write(byteArrayOf(SOCKS_VERSION.toByte(), status.toByte(), 0, IPV4.toByte(), 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun recordFailure(host: String?, failure: Failure) {
        if (running.get()) normalizeHost(host)?.let { failuresByHost[it] = failure }
    }

    private fun normalizeHost(host: String?): String? = host?.trim()?.trim('[', ']')?.lowercase()?.takeIf(String::isNotBlank)

    private fun relay(client: Socket, upstream: Socket) {
        val reverse = io.submit {
            upstream.getInputStream().copyTo(client.getOutputStream())
            runCatching { client.shutdownOutput() }
        }
        client.getInputStream().copyTo(upstream.getOutputStream())
        runCatching { upstream.shutdownOutput() }
        runCatching { reverse.get() }
    }

    override fun close() {
        synchronized(lifecycle) {
            val wasActive = running.getAndSet(false) || server != null
            runCatching { server?.close() }
            server = null
            sockets.toList().forEach { runCatching { it.close() } }
            sockets.clear()
            failuresByHost.clear()
            if (wasActive || !io.isShutdown) io.shutdownNow()
        }
    }

    private class BridgeException : Exception()

    private companion object {
        const val SOCKS_VERSION = 5
        const val NO_AUTH = 0
        const val USER_PASSWORD = 2
        const val AUTH_VERSION = 1
        const val NO_ACCEPTABLE_METHOD = 0xff
        const val CONNECT = 1
        const val IPV4 = 1
        const val DOMAIN = 3
        const val IPV6 = 4
        const val SUCCEEDED = 0
        const val GENERAL_FAILURE = 1
        const val COMMAND_NOT_SUPPORTED = 7
        const val CONNECT_TIMEOUT_MS = 12_000
        const val HANDSHAKE_TIMEOUT_MS = 20_000
    }
}
