package com.proxyplatform.app

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * Optional user-controlled mock provider. Android requires the user to select
 * this app as the mock-location app in Developer options; the app never bypasses that gate.
 */
class ProxyLocationController(private val context: Context) {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val thread = HandlerThread("proxy-location").apply { start() }
    private val handler = Handler(thread.looper)
    private var providerInstalled = false
    private var running = false

    fun startAuto(protocol: String, host: String, port: Int, username: String, password: String) {
        start { fetchProxyLocation(protocol, host, port, username, password) }
    }

    fun startManual(latitude: Double, longitude: Double) {
        start { GeoPoint(latitude, longitude) }
    }

    private fun start(source: () -> GeoPoint) {
        stop()
        running = true
        handler.post {
            runCatching {
                installProvider()
                publish(source())
                handler.postDelayed(object : Runnable {
                    override fun run() {
                        if (!running) return
                        runCatching { publish(source()) }
                        handler.postDelayed(this, REFRESH_MS)
                    }
                }, REFRESH_MS)
            }
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        if (providerInstalled) {
            runCatching { locationManager.removeTestProvider(PROVIDER) }
            providerInstalled = false
        }
    }

    fun close() {
        stop()
        thread.quitSafely()
    }

    private fun installProvider() {
        runCatching { locationManager.removeTestProvider(PROVIDER) }
        locationManager.addTestProvider(
            PROVIDER,
            false,
            true,
            false,
            false,
            true,
            true,
            true,
            ProviderProperties.POWER_USAGE_LOW,
            ProviderProperties.ACCURACY_FINE,
        )
        locationManager.setTestProviderEnabled(PROVIDER, true)
        providerInstalled = true
    }

    private fun publish(point: GeoPoint) {
        val location = Location(PROVIDER).apply {
            latitude = point.latitude
            longitude = point.longitude
            accuracy = 25f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) verticalAccuracyMeters = 50f
        }
        locationManager.setTestProviderLocation(PROVIDER, location)
    }

    private fun fetchProxyLocation(protocol: String, host: String, port: Int, username: String, password: String): GeoPoint {
        val proxyType = if (protocol == "http") Proxy.Type.HTTP else Proxy.Type.SOCKS
        val proxy = Proxy(proxyType, InetSocketAddress.createUnresolved(host, port))
        val builder = OkHttpClient.Builder().proxy(proxy).connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        if (username.isNotBlank()) {
            builder.proxyAuthenticator(Authenticator { _, response -> response.request.newBuilder().header("Proxy-Authorization", Credentials.basic(username, password)).build() })
        }
        val request = Request.Builder().url("https://ipwho.is/").header("Accept", "application/json").build()
        builder.build().newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Proxy GeoIP request failed (${response.code})")
            val json = JSONObject(response.body?.string().orEmpty())
            if (!json.optBoolean("success", true)) error("Proxy GeoIP lookup failed")
            return GeoPoint(json.getDouble("latitude"), json.getDouble("longitude"))
        }
    }

    private data class GeoPoint(val latitude: Double, val longitude: Double)

    companion object {
        private const val PROVIDER = LocationManager.GPS_PROVIDER
        private const val REFRESH_MS = 15 * 60 * 1000L
    }
}
