package com.proxyplatform.app

import java.net.ServerSocket
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyEndpointProbeTest {
    @Test
    fun acceptsAReachableLocalEndpoint() {
        ServerSocket(0).use { server ->
            val result = ProxyEndpointProbe.check("127.0.0.1", server.localPort, 1_000)
            assertTrue(result.isSuccess)
        }
    }

    @Test
    fun rejectsInvalidPortsWithoutOpeningASocket() {
        assertTrue(ProxyEndpointProbe.check("127.0.0.1", 0, 1_000).isFailure)
        assertTrue(ProxyEndpointProbe.check("127.0.0.1", 65_536, 1_000).isFailure)
    }
}
