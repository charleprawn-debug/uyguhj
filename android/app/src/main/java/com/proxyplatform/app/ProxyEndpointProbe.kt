package com.proxyplatform.app

import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Checks both the proxy listener and (when requested) a real upstream tunnel before Android routes traffic. */
internal object ProxyEndpointProbe {
    const val DEFAULT_TIMEOUT_MS = 5_000
    private const val TARGET_HOST = "1.1.1.1"
    private const val TARGET_PORT = 443

    /** Backwards-compatible reachability check for callers that only need to test an open TCP port. */
    fun check(host: String, port: Int, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Result<Unit> = runCatching {
        validateEndpoint(host, port, timeoutMs)
        Socket().use { it.connect(InetSocketAddress(host.trim(), port), timeoutMs) }
    }

    /** Performs the actual proxy handshake and asks it to open an internet-facing TLS tunnel. */
    fun check(
        host: String,
        port: Int,
        protocol: String,
        username: String,
        password: String,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): Result<Unit> = runCatching {
        validateEndpoint(host, port, timeoutMs)
        require(protocol == "http" || protocol == "socks5") { "بروتوكول البروكسي غير مدعوم." }
        require((username.isBlank() && password.isBlank()) || (username.isNotBlank() && password.isNotEmpty())) {
            "أدخل اسم المستخدم وكلمة المرور معًا، أو أوقف المصادقة."
        }
        Socket().use { socket ->
            socket.soTimeout = timeoutMs
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host.trim(), port), timeoutMs)
            when (protocol) {
                "http" -> checkHttpConnect(socket, username, password)
                "socks5" -> checkSocks5Connect(socket, username, password)
                else -> error("بروتوكول البروكسي غير مدعوم.")
            }
        }
    }

    private fun validateEndpoint(host: String, port: Int, timeoutMs: Int) {
        require(host.isNotBlank()) { "أدخل عنوان خادم البروكسي." }
        require(port in 1..65535) { "أدخل منفذًا صحيحًا للبروكسي." }
        require(timeoutMs > 0) { "مهلة الاتصال غير صالحة." }
    }

    private fun checkHttpConnect(socket: Socket, username: String, password: String) {
        val credentials = if (username.isNotBlank()) {
            val encoded = Base64.getEncoder().encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))
            "Proxy-Authorization: Basic $encoded\r\n"
        } else {
            ""
        }
        val request = buildString {
            append("CONNECT $TARGET_HOST:$TARGET_PORT HTTP/1.1\r\n")
            append("Host: $TARGET_HOST:$TARGET_PORT\r\n")
            append(credentials)
            append("Proxy-Connection: Keep-Alive\r\n\r\n")
        }
        socket.getOutputStream().apply {
            write(request.toByteArray(StandardCharsets.ISO_8859_1))
            flush()
        }
        val statusLine = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1)).readLine()
            ?: error("أغلق خادم البروكسي الاتصال قبل تأكيد HTTP CONNECT.")
        val statusCode = statusLine.trim().split(Regex("\\s+"))
            .getOrNull(1)?.toIntOrNull()
            ?: error("لم يُرجع خادم البروكسي استجابة HTTP صالحة.")
        when (statusCode) {
            in 200..299 -> Unit
            407 -> error("رفض البروكسي بيانات المصادقة. تحقق من اسم المستخدم وكلمة المرور.")
            403 -> error("رفض البروكسي إنشاء نفق إنترنت إلى $TARGET_HOST:$TARGET_PORT.")
            else -> error("فشل اختبار البروكسي HTTP (الاستجابة $statusCode).")
        }
    }

    private fun checkSocks5Connect(socket: Socket, username: String, password: String) {
        val hasCredentials = username.isNotBlank()
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())

        // Offer only the method that will be used by sing-box for this profile.
        output.write(byteArrayOf(0x05, 0x01, (if (hasCredentials) 0x02 else 0x00).toByte()))
        output.flush()
        val version = input.readUnsignedByte()
        val method = input.readUnsignedByte()
        if (version != 0x05) error("الخادم لا يتحدث بروتوكول SOCKS5.")
        if (method == 0xFF) error("لم يقبل خادم SOCKS5 طريقة المصادقة المحددة.")
        if (hasCredentials && method != 0x02) error("خادم SOCKS5 لم يقبل مصادقة اسم المستخدم وكلمة المرور.")
        if (!hasCredentials && method != 0x00) error("يتطلب هذا البروكسي اسم مستخدم وكلمة مرور؛ فعّل خيار المصادقة.")

        if (method == 0x02) {
            val userBytes = username.toByteArray(StandardCharsets.UTF_8)
            val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
            require(userBytes.size in 1..255 && passwordBytes.size in 1..255) {
                "بيانات مصادقة SOCKS5 يجب ألا تتجاوز 255 بايت لكل حقل."
            }
            output.writeByte(0x01)
            output.writeByte(userBytes.size)
            output.write(userBytes)
            output.writeByte(passwordBytes.size)
            output.write(passwordBytes)
            output.flush()
            val authVersion = input.readUnsignedByte()
            val authStatus = input.readUnsignedByte()
            if (authVersion != 0x01 || authStatus != 0x00) {
                error("رفض خادم SOCKS5 اسم المستخدم أو كلمة المرور.")
            }
        }

        // CONNECT 1.1.1.1:443 without relying on device DNS or sending user traffic.
        output.write(byteArrayOf(
            0x05, 0x01, 0x00, 0x01,
            1, 1, 1, 1,
            (TARGET_PORT ushr 8).toByte(), TARGET_PORT.toByte(),
        ))
        output.flush()
        val replyVersion = input.readUnsignedByte()
        val reply = input.readUnsignedByte()
        input.readUnsignedByte() // reserved
        val addressType = input.readUnsignedByte()
        if (replyVersion != 0x05) error("رد SOCKS5 غير صالح أثناء اختبار الاتصال.")
        if (reply != 0x00) error("تعذر على بروكسي SOCKS5 فتح نفق إنترنت (رمز $reply).")
        when (addressType) {
            0x01 -> input.readFully(ByteArray(4))
            0x03 -> input.readFully(ByteArray(input.readUnsignedByte()))
            0x04 -> input.readFully(ByteArray(16))
            else -> error("أعاد بروكسي SOCKS5 عنوانًا غير صالح.")
        }
        input.readUnsignedShort() // bound port
    }
}
