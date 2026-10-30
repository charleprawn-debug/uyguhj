package com.proxyplatform.browser

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class AccountProxy(
    val id: String,
    val label: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val status: String,
)

internal data class BrowserProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val proxyId: String = "manual",
    val protocol: String,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val languageMode: String = "auto",
    val language: String = "",
    val timeZoneMode: String = "auto",
    val timeZone: String = "",
    val locationMode: String = "auto",
    val latitude: String = "",
    val longitude: String = "",
    val detectedCountry: String = "",
    val detectedCountryCode: String = "",
    val detectedLocale: String = "",
    val detectedTimeZone: String = "",
    val detectedLatitude: String = "",
    val detectedLongitude: String = "",
) {
    val authenticated: Boolean get() = username.isNotBlank() || password.isNotBlank()
}

/** Auth and account proxy calls share KUN's production API, but keep a separate app-scoped session. */
internal class BrowserApi(context: Context) {
    private val prefs = context.getSharedPreferences("kun_browser_account", Context.MODE_PRIVATE)
    private val client = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    val isSignedIn: Boolean get() = !accessToken().isNullOrBlank()
    val email: String get() = prefs.getString("email", "").orEmpty()

    fun signIn(email: String, password: String) {
        val data = request("/auth/login", "POST", JSONObject().put("email", email.trim()).put("password", password), authenticated = false)
            .getJSONObject("data")
        prefs.edit()
            .putString("access_token_enc", SecretCipher.encrypt(data.getString("accessToken")))
            .putString("refresh_token_enc", SecretCipher.encrypt(data.optString("refreshToken")))
            .putString("email", email.trim().lowercase())
            .remove("access_token")
            .remove("refresh_token")
            .apply()
    }

    fun signUp(name: String, email: String, password: String) {
        request("/auth/register", "POST", JSONObject().put("email", email.trim()).put("password", password).put("fullName", name.trim()), authenticated = false)
        // The KUN API confirms new accounts at creation, then exposes access tokens from /auth/login.
        signIn(email, password)
    }

    fun loadProxies(): List<AccountProxy> {
        val rows = request("/me/proxies").optJSONArray("data") ?: JSONArray()
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            val location = row.opt("location")
            val locationName = if (location is JSONObject) {
                listOf(location.optString("country_name"), location.optString("country"), location.optString("city"))
                    .filter { it.isNotBlank() }.distinct().joinToString(" · ")
            } else {
                location as? String ?: ""
            }
            AccountProxy(
                id = row.optString("id"),
                label = listOf(locationName, row.optString("host"), row.optString("status")).filter { it.isNotBlank() }.joinToString(" · "),
                protocol = row.optString("protocol", "socks5").lowercase().let { if (it == "http") "http" else "socks5" },
                host = row.optString("host"),
                port = row.optInt("port"),
                username = row.optString("username"),
                password = row.optString("password"),
                status = row.optString("status", "active"),
            )
        }
    }

    fun signOut() { prefs.edit().clear().apply() }

    private fun accessToken(): String? {
        val encrypted = prefs.getString("access_token_enc", null)
        if (!encrypted.isNullOrBlank()) return SecretCipher.decrypt(encrypted)
        val legacy = prefs.getString("access_token", null)?.takeIf(String::isNotBlank) ?: return null
        prefs.edit().putString("access_token_enc", SecretCipher.encrypt(legacy)).remove("access_token").apply()
        return legacy
    }

    private fun request(path: String, method: String = "GET", body: JSONObject? = null, authenticated: Boolean = true, allowRefresh: Boolean = true): JSONObject {
        val url = BuildConfig.API_BASE_URL.trimEnd('/') + path
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        if (authenticated) accessToken()?.let { builder.header("Authorization", "Bearer $it") }
        if (body == null) builder.method(method, null) else builder.method(method, body.toString().toRequestBody(jsonType))
        val result = client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = if (raw.isBlank()) JSONObject() else runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            Triple(response.code, response.isSuccessful, json)
        }
        if (result.first == 401 && authenticated && allowRefresh && refreshSession()) {
            return request(path, method, body, authenticated = true, allowRefresh = false)
        }
        if (!result.second) {
            val serverMessage = result.third.optJSONObject("error")?.optString("message").orEmpty()
            val message = when {
                result.first == 401 -> "انتهت الجلسة. سجّل الدخول من جديد."
                result.first == 409 -> serverMessage.ifBlank { "هذا الحساب موجود مسبقًا." }
                result.first == 400 && path == "/auth/register" -> "تحقق من البريد الإلكتروني وكلمة المرور (8 أحرف على الأقل)."
                else -> serverMessage.ifBlank { "تعذر الاتصال بالخادم (${result.first}). حاول مجددًا." }
            }
            throw IllegalStateException(message)
        }
        return result.third
    }

    private fun refreshSession(): Boolean = runCatching {
        val encrypted = prefs.getString("refresh_token_enc", null).orEmpty()
        if (encrypted.isBlank()) return@runCatching false
        val refreshToken = SecretCipher.decrypt(encrypted)
        val data = request(
            "/auth/refresh", "POST", JSONObject().put("refreshToken", refreshToken),
            authenticated = false, allowRefresh = false,
        ).getJSONObject("data")
        prefs.edit()
            .putString("access_token_enc", SecretCipher.encrypt(data.getString("accessToken")))
            .putString("refresh_token_enc", SecretCipher.encrypt(data.optString("refreshToken")))
            .apply()
        true
    }.getOrDefault(false)
}

/** Per-account profile storage; proxy secrets are encrypted with a device-bound Android Keystore key. */
internal class BrowserProfileStore(context: Context, accountEmail: String) {
    private val prefs = context.getSharedPreferences("kun_browser_profiles", Context.MODE_PRIVATE)
    private val storageKey = "profiles_" + Base64.encodeToString(accountEmail.trim().lowercase().toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)

    @Synchronized fun load(): List<BrowserProfile> {
        val rows = runCatching { JSONArray(prefs.getString(storageKey, "[]")) }.getOrElse { JSONArray() }
        return (0 until rows.length()).mapNotNull { i -> runCatching { decode(rows.getJSONObject(i)) }.getOrNull() }
    }

    @Synchronized fun save(profile: BrowserProfile): List<BrowserProfile> {
        require(profile.name.isNotBlank()) { "سمّ ملف التصفح أولًا." }
        if (profile.proxyId != "direct") {
            require(profile.host.isNotBlank()) { "أدخل عنوان البروكسي." }
            require(profile.port in 1..65535) { "رقم المنفذ يجب أن يكون بين 1 و65535." }
            require(profile.protocol in setOf("http", "socks5")) { "اختر HTTP أو SOCKS5." }
        }
        val rows = load().toMutableList()
        val index = rows.indexOfFirst { it.id == profile.id }
        if (index >= 0) rows[index] = profile else rows.add(0, profile)
        persist(rows)
        return rows
    }

    @Synchronized fun delete(id: String): List<BrowserProfile> {
        val rows = load().filterNot { it.id == id }
        persist(rows)
        return rows
    }

    private fun persist(profiles: List<BrowserProfile>) {
        val rows = JSONArray()
        profiles.forEach { rows.put(encode(it)) }
        prefs.edit().putString(storageKey, rows.toString()).apply()
    }

    private fun encode(p: BrowserProfile) = JSONObject()
        .put("id", p.id).put("name", p.name).put("proxyId", p.proxyId)
        .put("protocol", p.protocol).put("host", p.host).put("port", p.port)
        .put("username", SecretCipher.encrypt(p.username)).put("password", SecretCipher.encrypt(p.password))
        .put("languageMode", p.languageMode).put("language", p.language)
        .put("timeZoneMode", p.timeZoneMode).put("timeZone", p.timeZone)
        .put("locationMode", p.locationMode).put("latitude", p.latitude).put("longitude", p.longitude)
        .put("detectedCountry", p.detectedCountry).put("detectedCountryCode", p.detectedCountryCode).put("detectedLocale", p.detectedLocale)
        .put("detectedTimeZone", p.detectedTimeZone).put("detectedLatitude", p.detectedLatitude).put("detectedLongitude", p.detectedLongitude)

    private fun decode(j: JSONObject) = BrowserProfile(
        id = j.getString("id"), name = j.getString("name"), proxyId = j.optString("proxyId", "manual"),
        protocol = j.optString("protocol", "socks5"), host = j.getString("host"), port = j.getInt("port"),
        username = SecretCipher.decrypt(j.optString("username")), password = SecretCipher.decrypt(j.optString("password")),
        languageMode = j.optString("languageMode", "auto"), language = j.optString("language"),
        timeZoneMode = j.optString("timeZoneMode", "auto"), timeZone = j.optString("timeZone"),
        locationMode = j.optString("locationMode", "auto"), latitude = j.optString("latitude"), longitude = j.optString("longitude"),
        detectedCountry = j.optString("detectedCountry"), detectedCountryCode = j.optString("detectedCountryCode"), detectedLocale = j.optString("detectedLocale"),
        detectedTimeZone = j.optString("detectedTimeZone"), detectedLatitude = j.optString("detectedLatitude"), detectedLongitude = j.optString("detectedLongitude"),
    )
}

private object SecretCipher {
    private const val KEY_ALIAS = "kun-browser-proxy-secret-v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12

    @Synchronized fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    @Synchronized fun decrypt(value: String): String {
        if (value.isEmpty()) return ""
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES) { "بيانات البروكسي المحفوظة غير صالحة." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, IV_BYTES)))
        return cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)).toString(Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build())
            generateKey()
        }
    }
}


internal data class ProxyGeoInfo(
    val country: String,
    val countryCode: String,
    val timeZone: String,
    val latitude: Double,
    val longitude: Double,
) {
    val suggestedLanguage: String get() = BrowserEnvironment.proxyAcceptLanguages(countryCode).substringBefore(',')
}

/** Queries IP geolocation over the configured upstream proxy only; never falls back to a direct socket. */
internal object ProxyGeoProbe {
    private const val GEO_HOST = "ipwho.is"
    private const val GEO_PORT = 443

    fun detect(profile: BrowserProfile): ProxyGeoInfo {
        require(profile.host.isNotBlank() && profile.port in 1..65535) { "أدخل بروكسيًا صالحًا أولًا." }
        val socket = java.net.Socket()
        socket.connect(java.net.InetSocketAddress(profile.host, profile.port), 12_000)
        socket.soTimeout = 12_000
        try {
            when (profile.protocol) {
                "socks5" -> openSocksTunnel(socket, profile)
                "http" -> openHttpTunnel(socket, profile)
                else -> throw IllegalArgumentException("استخدم HTTP أو SOCKS5.")
            }
            val factory = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
            val tls = factory.createSocket(socket, GEO_HOST, GEO_PORT, true) as javax.net.ssl.SSLSocket
            tls.soTimeout = 15_000
            tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            tls.startHandshake()
            tls.outputStream.write("GET / HTTP/1.0\r\nHost: $GEO_HOST\r\nAccept: application/json\r\nAccept-Encoding: identity\r\nConnection: close\r\nUser-Agent: KUN-Browser/1\r\n\r\n".toByteArray(Charsets.US_ASCII))
            tls.outputStream.flush()
            val input = java.io.BufferedInputStream(tls.inputStream)
            val status = readAsciiLine(input)
            if (!status.contains(" 200 ")) throw IllegalStateException("خدمة تحديد موقع البروكسي ردّت: ${status.take(90)}")
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = readAsciiLine(input)
                if (line.isEmpty()) break
                val split = line.indexOf(':')
                if (split > 0) headers[line.substring(0, split).trim().lowercase()] = line.substring(split + 1).trim()
            }
            val bodyBytes = if (headers["transfer-encoding"]?.contains("chunked", true) == true) readChunkedBody(input) else input.readBytes()
            val body = bodyBytes.toString(Charsets.UTF_8)
            tls.close()
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) throw IllegalStateException(json.optString("message").ifBlank { "تعذر تحديد موقع البروكسي." })
            val timezone = BrowserEnvironment.normalizeTimeZone(json.optJSONObject("timezone")?.optString("id").orEmpty()).orEmpty()
            val latitude = json.optDouble("latitude", Double.NaN)
            val longitude = json.optDouble("longitude", Double.NaN)
            if (timezone.isBlank() || !latitude.isFinite() || !longitude.isFinite()) throw IllegalStateException("لم تُرجع خدمة تحديد الموقع بيانات كافية لهذا البروكسي.")
            return ProxyGeoInfo(
                country = json.optString("country", ""),
                countryCode = json.optString("country_code", ""),
                timeZone = timezone,
                latitude = latitude,
                longitude = longitude,
            )
        } catch (failure: Exception) {
            runCatching { socket.close() }
            throw IllegalStateException(failure.message ?: "تعذر اختبار البروكسي. تحقق من العنوان والمنفذ وبيانات الدخول.", failure)
        }
    }

    private fun openSocksTunnel(socket: java.net.Socket, profile: BrowserProfile) {
        val out = socket.getOutputStream()
        val input = socket.getInputStream()
        val user = profile.username.toByteArray(Charsets.UTF_8)
        val pass = profile.password.toByteArray(Charsets.UTF_8)
        val methods = if (profile.username.isNotBlank() || profile.password.isNotBlank()) byteArrayOf(0, 2) else byteArrayOf(0)
        out.write(byteArrayOf(5, methods.size.toByte()) + methods)
        out.flush()
        val hello = readBytes(input, 2)
        if (hello[0].toInt() != 5 || hello[1].toInt() == 0xff) throw IllegalStateException("البروكسي لا يقبل مصادقة SOCKS5 المطلوبة.")
        when (hello[1].toInt()) {
            0 -> Unit
            2 -> {
                if (user.isEmpty() || user.size > 255 || pass.size > 255) throw IllegalStateException("بيانات مصادقة SOCKS5 غير صالحة.")
                out.write(byteArrayOf(1, user.size.toByte()) + user + byteArrayOf(pass.size.toByte()) + pass)
                out.flush()
                val auth = readBytes(input, 2)
                if (auth[1].toInt() != 0) throw IllegalStateException("رفض بروكسي SOCKS5 بيانات الدخول.")
            }
            else -> throw IllegalStateException("طريقة مصادقة SOCKS5 غير مدعومة.")
        }
        val host = GEO_HOST.toByteArray(Charsets.US_ASCII)
        out.write(byteArrayOf(5, 1, 0, 3, host.size.toByte()) + host + byteArrayOf((GEO_PORT shr 8).toByte(), GEO_PORT.toByte()))
        out.flush()
        val reply = readBytes(input, 4)
        if (reply[1].toInt() != 0) throw IllegalStateException("فشل فتح مسار عبر بروكسي SOCKS5 (رمز ${reply[1].toInt()}).")
        val addressLength = when (reply[3].toInt()) {
            1 -> 4
            3 -> readBytes(input, 1)[0].toInt() and 0xff
            4 -> 16
            else -> throw IllegalStateException("رد SOCKS5 غير صالح.")
        }
        readBytes(input, addressLength + 2)
    }

    private fun openHttpTunnel(socket: java.net.Socket, profile: BrowserProfile) {
        val auth = if (profile.username.isNotBlank()) {
            val encoded = Base64.encodeToString("${profile.username}:${profile.password}".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            "Proxy-Authorization: Basic $encoded\r\n"
        } else ""
        socket.getOutputStream().write("CONNECT $GEO_HOST:$GEO_PORT HTTP/1.1\r\nHost: $GEO_HOST:$GEO_PORT\r\n${auth}Proxy-Connection: keep-alive\r\n\r\n".toByteArray(Charsets.US_ASCII))
        socket.getOutputStream().flush()
        val input = java.io.BufferedInputStream(socket.getInputStream())
        val status = readAsciiLine(input)
        if (!status.contains(" 200 ")) throw IllegalStateException("رفض بروكسي HTTP إنشاء نفق للموقع (تحقق من CONNECT والمصادقة).")
        while (true) {
            val line = readAsciiLine(input)
            if (line.isEmpty()) break
        }
    }

    private fun readAsciiLine(input: java.io.InputStream): String {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value < 0 || value == 10) break
            if (value != 13) bytes.write(value)
            if (bytes.size() > 8192) throw IllegalStateException("رد البروكسي أكبر من المتوقع.")
        }
        return bytes.toString(Charsets.US_ASCII.name())
    }

    private fun readBytes(input: java.io.InputStream, count: Int): ByteArray {
        val result = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(result, offset, count - offset)
            if (read < 0) throw IllegalStateException("انقطع الاتصال أثناء تفاوض البروكسي.")
            offset += read
        }
        return result
    }

    private fun readChunkedBody(input: java.io.InputStream): ByteArray {
        val body = java.io.ByteArrayOutputStream()
        while (true) {
            val sizeLine = readAsciiLine(input).substringBefore(';')
            val size = sizeLine.trim().toIntOrNull(16) ?: break
            if (size == 0) {
                while (readAsciiLine(input).isNotEmpty()) { }
                break
            }
            body.write(readBytes(input, size))
            readAsciiLine(input)
        }
        return body.toByteArray()
    }
}
