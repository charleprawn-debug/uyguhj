package com.proxyplatform.browser

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Socks5ProxyBridgeTest {
    @Test
    fun authenticatedUpstreamReceivesCredentialsAndTunnelsTraffic() {
        val upstreamServer = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val serverFailure = AtomicReference<Throwable?>(null)
        val upstreamThread = thread(name = "fake-socks5-upstream") {
            try {
                upstreamServer.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val input = DataInputStream(socket.getInputStream())
                    val output = DataOutputStream(socket.getOutputStream())

                    assertEquals(5, input.readUnsignedByte())
                    val methods = ByteArray(input.readUnsignedByte()).also(input::readFully)
                    assertTrue(2 in methods.map { it.toInt() and 0xff })
                    output.write(byteArrayOf(5, 2))
                    output.flush()

                    assertEquals(1, input.readUnsignedByte())
                    val username = ByteArray(input.readUnsignedByte()).also(input::readFully).toString(Charsets.UTF_8)
                    val password = ByteArray(input.readUnsignedByte()).also(input::readFully).toString(Charsets.UTF_8)
                    assertEquals("kun-test-user", username)
                    assertEquals("kun-test-password", password)
                    output.write(byteArrayOf(1, 0))
                    output.flush()

                    val request = ByteArray(4).also(input::readFully)
                    assertArrayEquals(byteArrayOf(5, 1, 0, 3), request)
                    val hostLength = input.readUnsignedByte()
                    val host = ByteArray(hostLength).also(input::readFully).toString(Charsets.US_ASCII)
                    assertEquals("example.com", host)
                    assertEquals(443, input.readUnsignedShort())
                    output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0x12, 0x34))
                    output.flush()

                    val payload = ByteArray(5).also(input::readFully)
                    assertEquals("hello", payload.toString(Charsets.US_ASCII))
                    output.write("world".toByteArray(Charsets.US_ASCII))
                    output.flush()
                }
            } catch (failure: Throwable) {
                serverFailure.set(failure)
            }
        }

        val bridge = Socks5ProxyBridge("127.0.0.1", upstreamServer.localPort, "kun-test-user", "kun-test-password")
        var client: Socket? = null
        try {
            val localPort = bridge.start()
            client = Socket("127.0.0.1", localPort).apply { soTimeout = 5_000 }
            val input = DataInputStream(client.getInputStream())
            val output = DataOutputStream(client.getOutputStream())

            output.write(byteArrayOf(5, 1, 0))
            output.flush()
            assertArrayEquals(byteArrayOf(5, 0), ByteArray(2).also(input::readFully))

            val host = "example.com".toByteArray(Charsets.US_ASCII)
            output.write(byteArrayOf(5, 1, 0, 3, host.size.toByte()) + host + byteArrayOf(1, 0xbb.toByte()))
            output.flush()
            val replyHeader = ByteArray(4).also(input::readFully)
            assertArrayEquals("bridge=${bridge.failureMessageFor("example.com")}; upstream=${serverFailure.get()}", byteArrayOf(5, 0, 0, 1), replyHeader)
            ByteArray(6).also(input::readFully) // bound IPv4 address and port

            output.write("hello".toByteArray(Charsets.US_ASCII))
            output.flush()
            assertEquals("world", ByteArray(5).also(input::readFully).toString(Charsets.US_ASCII))
            client.close()
            client = null
            upstreamThread.join(5_000)
            assertFalse("fake proxy should finish", upstreamThread.isAlive)
            assertNull("fake upstream error: ${serverFailure.get()}", serverFailure.get())
            assertNull(bridge.failureMessageFor("example.com"))
            assertNull(bridge.failureMessageFor("other.example"))
        } finally {
            runCatching { client?.close() }
            bridge.close()
            upstreamServer.close()
            upstreamThread.join(1_000)
        }
    }

    @Test
    fun rejectedCredentialsAreReportedOnlyForTheRequestedHost() {
        val upstreamServer = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val serverFailure = AtomicReference<Throwable?>(null)
        val upstreamThread = thread(name = "fake-socks5-auth-rejection") {
            try {
                upstreamServer.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val input = DataInputStream(socket.getInputStream())
                    val output = DataOutputStream(socket.getOutputStream())
                    assertEquals(5, input.readUnsignedByte())
                    val methods = ByteArray(input.readUnsignedByte()).also(input::readFully)
                    assertTrue(2 in methods.map { it.toInt() and 0xff })
                    output.write(byteArrayOf(5, 2))
                    output.flush()

                    assertEquals(1, input.readUnsignedByte())
                    val username = ByteArray(input.readUnsignedByte()).also(input::readFully)
                    val password = ByteArray(input.readUnsignedByte()).also(input::readFully)
                    assertTrue(username.isNotEmpty())
                    assertTrue(password.isNotEmpty())
                    output.write(byteArrayOf(1, 1))
                    output.flush()
                }
            } catch (failure: Throwable) {
                serverFailure.set(failure)
            }
        }

        val bridge = Socks5ProxyBridge("127.0.0.1", upstreamServer.localPort, "bad-user", "bad-password")
        var client: Socket? = null
        try {
            client = Socket("127.0.0.1", bridge.start()).apply { soTimeout = 5_000 }
            val input = DataInputStream(client.getInputStream())
            val output = DataOutputStream(client.getOutputStream())
            output.write(byteArrayOf(5, 1, 0))
            output.flush()
            assertArrayEquals(byteArrayOf(5, 0), ByteArray(2).also(input::readFully))

            val host = "example.com".toByteArray(Charsets.US_ASCII)
            output.write(byteArrayOf(5, 1, 0, 3, host.size.toByte()) + host + byteArrayOf(1, 0xbb.toByte()))
            output.flush()
            assertArrayEquals(byteArrayOf(5, 1, 0, 1, 0, 0, 0, 0, 0, 0), ByteArray(10).also(input::readFully))
            upstreamThread.join(5_000)
            assertFalse("fake proxy should finish", upstreamThread.isAlive)
            assertNull("fake upstream error: ${serverFailure.get()}", serverFailure.get())
            assertTrue(bridge.failureMessageFor("example.com")?.contains("بيانات الدخول") == true)
            assertNull(bridge.failureMessageFor("another.example"))
        } finally {
            runCatching { client?.close() }
            bridge.close()
            upstreamServer.close()
            upstreamThread.join(1_000)
        }
    }
}
