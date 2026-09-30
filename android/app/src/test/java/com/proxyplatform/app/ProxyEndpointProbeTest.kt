package com.proxyplatform.app

import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyEndpointProbeTest {
    @Test
    fun acceptsAReachableLocalEndpoint() {
        ServerSocket(0).use { server ->
            val accept = Thread { server.accept().close() }.apply { isDaemon = true; start() }
            val result = ProxyEndpointProbe.check("127.0.0.1", server.localPort, 1_000)
            accept.join(1_000)
            assertTrue(result.isSuccess)
        }
    }

    @Test
    fun acceptsASuccessfulSocks5InternetTunnel() {
        withMockProxy({ client ->
            val input = DataInputStream(client.getInputStream())
            val output = DataOutputStream(client.getOutputStream())
            assertEquals(5, input.readUnsignedByte())
            assertEquals(1, input.readUnsignedByte())
            assertEquals(0, input.readUnsignedByte())
            output.write(byteArrayOf(5, 0))
            output.flush()
            val connect = ByteArray(10)
            input.readFully(connect)
            assertEquals(1, connect[1].toInt())
            output.write(byteArrayOf(5, 0, 0, 1, 1, 1, 1, 1, 1, 0xBB.toByte()))
            output.flush()
        }) { port ->
            assertTrue(ProxyEndpointProbe.check("127.0.0.1", port, "socks5", "", "", 1_000).isSuccess)
        }
    }

    @Test
    fun acceptsAnAuthenticatedHttpConnectTunnel() {
        val expectedAuth = "Basic " + java.util.Base64.getEncoder()
            .encodeToString("test-user:test-password".toByteArray(StandardCharsets.UTF_8))
        withMockProxy({ client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1))
            assertTrue(reader.readLine().startsWith("CONNECT 1.1.1.1:443 HTTP/1."))
            var authHeader: String? = null
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
                if (header.startsWith("Proxy-Authorization:", ignoreCase = true)) {
                    authHeader = header.substringAfter(':').trim()
                }
            }
            assertEquals(expectedAuth, authHeader)
            client.getOutputStream().write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            client.getOutputStream().flush()
        }) { port ->
            assertTrue(
                ProxyEndpointProbe.check("127.0.0.1", port, "http", "test-user", "test-password", 1_000).isSuccess
            )
        }
    }

    @Test
    fun reportsHttpProxyAuthenticationRejectionWithoutExposingCredentials() {
        withMockProxy({ client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1))
            while (reader.readLine()?.isNotEmpty() == true) Unit
            client.getOutputStream().write("HTTP/1.1 407 Proxy Authentication Required\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            client.getOutputStream().flush()
        }) { port ->
            val result = ProxyEndpointProbe.check("127.0.0.1", port, "http", "private-user", "private-password", 1_000)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("المصادقة") == true)
            assertFalse(result.exceptionOrNull()?.message?.contains("private-password") == true)
        }
    }

    @Test
    fun rejectsInvalidPortsWithoutOpeningASocket() {
        assertTrue(ProxyEndpointProbe.check("127.0.0.1", 0, 1_000).isFailure)
        assertTrue(ProxyEndpointProbe.check("127.0.0.1", 65_536, 1_000).isFailure)
    }

    private fun withMockProxy(handler: (Socket) -> Unit, action: (port: Int) -> Unit) {
        val server = ServerSocket(0)
        val workerFailure = AtomicReference<Throwable?>()
        val worker = Thread {
            try {
                server.accept().use(handler)
            } catch (failure: Throwable) {
                workerFailure.set(failure)
            } finally {
                runCatching { server.close() }
            }
        }.apply { isDaemon = true; start() }
        try {
            action(server.localPort)
        } finally {
            runCatching { server.close() }
            worker.join(2_000)
        }
        assertFalse("Mock proxy did not finish", worker.isAlive)
        workerFailure.get()?.let { throw AssertionError("Mock proxy failed", it) }
    }
}
