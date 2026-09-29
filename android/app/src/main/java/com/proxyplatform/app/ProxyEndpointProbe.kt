package com.proxyplatform.app

import java.net.InetSocketAddress
import java.net.Socket

/** Verifies that the selected upstream endpoint is reachable before routing traffic to it. */
internal object ProxyEndpointProbe {
    const val DEFAULT_TIMEOUT_MS = 5_000

    fun check(host: String, port: Int, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Result<Unit> = runCatching {
        require(host.isNotBlank()) { "أدخل عنوان خادم البروكسي." }
        require(port in 1..65535) { "أدخل منفذًا صحيحًا للبروكسي." }
        require(timeoutMs > 0) { "مهلة الاتصال غير صالحة." }
        Socket().use { it.connect(InetSocketAddress(host.trim(), port), timeoutMs) }
    }
}
