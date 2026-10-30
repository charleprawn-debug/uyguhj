package com.proxyplatform.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebRequestError
import java.io.File
import java.util.UUID

private const val EXTRA_PROXY_MODE = "kun.browser.proxy_mode"
private const val EXTRA_LOCAL_PORT = "kun.browser.local_port"
private const val EXTRA_LANGUAGE = "kun.browser.language"
private const val EXTRA_PROXY_PROTOCOL = "kun.browser.proxy_protocol"
private const val EXTRA_PROXY_HOST = "kun.browser.proxy_host"
private const val EXTRA_PROXY_USERNAME = "kun.browser.proxy_username"
private const val EXTRA_PROXY_PASSWORD = "kun.browser.proxy_password"
private const val EXTRA_PROFILE_NAME = "kun.browser.profile_name"
internal const val EXTRA_PROFILE_ID = "kun.browser.profile_id"
private const val EXTRA_LOCATION_MODE = "kun.browser.location_mode"
private const val EXTRA_LATITUDE = "kun.browser.latitude"
private const val EXTRA_LONGITUDE = "kun.browser.longitude"
private const val PROFILE_EXTENSION_URI = "resource://android/assets/kun-profile/"
private const val PROFILE_EXTENSION_ID = "kun-profile-environment@kun.app"
internal const val ACTION_CLEAR_PROFILE = "com.proxyplatform.browser.action.CLEAR_PROFILE"

internal data class BrowserLaunchConfig(
    val mode: String,
    val localPort: Int,
    val proxyProtocol: String,
    val proxyHost: String,
    val proxyUsername: String,
    val proxyPassword: String,
    val language: String,
    val profileName: String,
    val profileId: String,
    val locationMode: String,
    val latitude: String,
    val longitude: String,
) {
    val connectionReady: Boolean
        get() = mode == "direct" ||
            (mode in setOf("vpn", "advanced") && localPort in 1024..65535) ||
            (mode == "profile" && proxyHost.isNotBlank() && localPort in 1..65535 && proxyProtocol in setOf("http", "socks5"))

    fun signature(): String = "$mode|$localPort|$proxyProtocol|$proxyHost|$proxyUsername|$proxyPassword|$language|$profileId|$locationMode|$latitude|$longitude"

    companion object {
        fun from(intent: Intent?): BrowserLaunchConfig = BrowserLaunchConfig(
            mode = intent?.getStringExtra(EXTRA_PROXY_MODE).orEmpty(),
            localPort = intent?.getIntExtra(EXTRA_LOCAL_PORT, 0) ?: 0,
            proxyProtocol = intent?.getStringExtra(EXTRA_PROXY_PROTOCOL).orEmpty().lowercase(),
            proxyHost = intent?.getStringExtra(EXTRA_PROXY_HOST).orEmpty(),
            proxyUsername = intent?.getStringExtra(EXTRA_PROXY_USERNAME).orEmpty(),
            proxyPassword = intent?.getStringExtra(EXTRA_PROXY_PASSWORD).orEmpty(),
            language = BrowserEnvironment.normalizeAcceptLanguages(
                intent?.getStringExtra(EXTRA_LANGUAGE).orEmpty(),
            ) ?: "en-US",
            profileName = intent?.getStringExtra(EXTRA_PROFILE_NAME)?.takeIf(String::isNotBlank) ?: "الافتراضي",
            profileId = intent?.getStringExtra(EXTRA_PROFILE_ID)?.takeIf(String::isNotBlank) ?: "profile-default",
            locationMode = intent?.getStringExtra(EXTRA_LOCATION_MODE).orEmpty().ifBlank { "auto" },
            latitude = intent?.getStringExtra(EXTRA_LATITUDE).orEmpty(),
            longitude = intent?.getStringExtra(EXTRA_LONGITUDE).orEmpty(),
        )
    }
}

internal class BrowserTab(
    val id: String,
    val session: GeckoSession,
    val privateMode: Boolean,
    val profileId: String,
) {
    var title by mutableStateOf(if (privateMode) "تبويب خاص" else "تبويب جديد")
    var uri by mutableStateOf("")
    var loading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
}

internal class BrowserController(private val activity: BrowserActivity) {
    val tabs = mutableStateListOf<BrowserTab>()
    val profiles = mutableStateListOf<Pair<String, String>>()
    var selectedTabId by mutableStateOf<String?>(null)
        private set
    var activeProfileId by mutableStateOf("profile-default")
        private set
    var activeProfileName by mutableStateOf("الافتراضي")
        private set
    var connectionReady by mutableStateOf(false)
        private set
    var directConnection by mutableStateOf(false)
        private set
    var connectionLabel by mutableStateOf("افتح المتصفح من KUN بعد تشغيل الاتصال")
        private set
    var languageLabel by mutableStateOf("en-US")
        private set
    var timeZoneLabel by mutableStateOf(BrowserEnvironment.PRIVACY_TIME_ZONE)
        private set
    var configError by mutableStateOf<String?>(null)
        private set

    private var runtime: GeckoRuntime? = null
    private var socks5Bridge: Socks5ProxyBridge? = null
    private var configSignature: String? = null
    private var launchConfig: BrowserLaunchConfig? = null
    private val preferences = activity.getSharedPreferences("kun_browser", 0)
    private val mainHandler = Handler(Looper.getMainLooper())

    val selectedTab: BrowserTab? get() = tabs.firstOrNull { it.id == selectedTabId }

    init {
        loadProfiles()
        applyLaunchConfig(BrowserLaunchConfig.from(activity.intent))
    }

    fun applyLaunchConfig(config: BrowserLaunchConfig) {
        if (runtime != null && config.signature() == configSignature) return
        closeSessions()
        runCatching { runtime?.shutdown() }
        runtime = null
        socks5Bridge?.close()
        socks5Bridge = null
        connectionReady = config.connectionReady
        directConnection = config.mode == "direct"
        connectionLabel = when (config.mode) {
            "vpn" -> "اتصال KUN VPN"
            "advanced" -> "بروكسي KUN المحلي"
            "direct" -> "اتصال مباشر • بدون بروكسي"
            "profile" -> "ملف: ${config.profileName} • ${config.proxyProtocol.uppercase()}"
            else -> "اختر ملفًا لبدء التصفح"
        }
        languageLabel = config.language
        timeZoneLabel = BrowserEnvironment.PRIVACY_TIME_ZONE
        if (config.mode in setOf("profile", "direct")) {
            activeProfileId = config.profileId
            activeProfileName = config.profileName
        }
        launchConfig = config
        configSignature = config.signature()
        try {
            val socks5BridgePort = if (config.connectionReady && config.mode == "profile" && config.proxyProtocol == "socks5") {
                Socks5ProxyBridge(config.proxyHost, config.localPort, config.proxyUsername, config.proxyPassword)
                    .also { socks5Bridge = it }
                    .start()
            } else 0
            val configFile = writeRuntimeConfig(config, socks5BridgePort)
            val requestedLocales = BrowserEnvironment.requestedLocales(config.language)
            val settings = GeckoRuntimeSettings.Builder()
                .configFilePath(configFile.absolutePath)
                .locales(requestedLocales)
                .globalPrivacyControlEnabled(true)
                .build()
            settings.setFingerprintingProtection(true)
            settings.setFingerprintingProtectionPrivateBrowsing(true)
            settings.setFingerprintingProtectionOverrides(
                BrowserEnvironment.FINGERPRINTING_PROTECTION_OVERRIDES,
            )
            settings.setBaselineFingerprintingProtection(true)
            settings.setBaselineFingerprintingProtectionOverrides(
                BrowserEnvironment.FINGERPRINTING_PROTECTION_OVERRIDES,
            )
            runtime = GeckoRuntime.create(activity.applicationContext, settings)
            configError = null
            newTab(privateMode = false)
        } catch (failure: Exception) {
            socks5Bridge?.close()
            socks5Bridge = null
            configError = failure.message ?: "تعذر تهيئة محرك المتصفح."
            connectionReady = false
        }
    }

    fun retryLaunchConfig() {
        val config = launchConfig ?: return
        configSignature = null
        applyLaunchConfig(config)
    }

    fun newTab(privateMode: Boolean = false) {
        createTab(privateMode, activeProfileId, "")
    }

    private fun createTab(privateMode: Boolean, profileId: String, initialUri: String): BrowserTab? {
        val currentRuntime = runtime ?: return null
        val settingsBuilder = GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .useTrackingProtection(true)
        if (!privateMode) settingsBuilder.contextId(profileId)
        val session = GeckoSession(settingsBuilder.build())
        configureProfileEnvironment(session)
        val tab = BrowserTab(UUID.randomUUID().toString(), session, privateMode, profileId)
        session.setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title?.takeIf(String::isNotBlank) ?: if (tab.privateMode) "تبويب خاص" else "تبويب جديد"
            }
        })
        session.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                tab.uri = url.orEmpty()
                if (!url.isNullOrBlank() && url != "about:blank") recordVisit(url, tab.privateMode)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
            }

            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                tab.loading = false
                val destinationHost = runCatching { uri?.let { Uri.parse(it).host } }.getOrNull()
                tab.errorMessage = socks5Bridge?.failureMessageFor(destinationHost) ?: when (error.code) {
                    WebRequestError.ERROR_UNKNOWN_HOST, WebRequestError.ERROR_UNKNOWN_PROXY_HOST -> "تعذّر العثور على الموقع أو خادم البروكسي. تحقق من العنوان وإعداد الاتصال."
                    WebRequestError.ERROR_PROXY_CONNECTION_REFUSED, WebRequestError.ERROR_CONNECTION_REFUSED -> "رفض خادم البروكسي الاتصال. تحقق من بيانات البروكسي أو اختر اتصالًا مباشرًا."
                    WebRequestError.ERROR_NET_TIMEOUT -> "انتهت مهلة الاتصال. حاول مجددًا أو افحص البروكسي."
                    WebRequestError.ERROR_SECURITY_SSL, WebRequestError.ERROR_SECURITY_BAD_CERT, WebRequestError.ERROR_BAD_HSTS_CERT -> "تعذّر التحقق من شهادة أمان الموقع. لم نفتح الصفحة لحمايتك."
                    else -> "تعذّر تحميل الصفحة. تحقق من الإنترنت والبروكسي ثم أعد المحاولة."
                }
                return null
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (!connectionReady) return null
                val safeUri = uri.takeIf { it == "about:blank" || Uri.parse(it).scheme in setOf("http", "https") } ?: "about:blank"
                val popupTab = createTab(tab.privateMode, tab.profileId, safeUri) ?: return null
                return GeckoResult.fromValue(popupTab.session)
            }
        })
        session.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.loading = true
                tab.uri = url
                tab.errorMessage = null
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.loading = false
            }
        })
        session.open(currentRuntime)
        tabs.add(tab)
        selectTab(tab.id)
        if (connectionReady && initialUri.isNotBlank() && initialUri != "about:blank") session.loadUri(initialUri)
        return tab
    }

    fun selectTab(id: String) {
        val old = selectedTab
        if (old?.id != id) old?.session?.setActive(false)
        selectedTabId = id
        tabs.firstOrNull { it.id == id }?.session?.setActive(true)
    }

    fun closeTab(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val closing = tabs[index]
        tabs.removeAt(index)
        runCatching { closing.session.close() }
        if (selectedTabId == id) {
            val next = tabs.getOrNull((index - 1).coerceAtLeast(0)) ?: tabs.firstOrNull()
            if (next == null) newTab() else selectTab(next.id)
        }
    }

    fun goToAddress(raw: String) {
        val tab = selectedTab ?: return
        if (!connectionReady) return
        val target = normalizeAddress(raw) ?: return
        socks5Bridge?.clearFailures()
        tab.uri = target
        tab.session.loadUri(target)
    }

    fun goBack() { selectedTab?.session?.goBack() }
    fun goForward() { selectedTab?.session?.goForward() }
    fun reload() { socks5Bridge?.clearFailures(); selectedTab?.session?.reload() }
    fun stop() { selectedTab?.session?.stop() }
    fun goHome() { selectedTab?.session?.loadUri("about:blank") }

    fun addBookmark(): Boolean {
        val uri = selectedTab?.uri?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return false
        val key = profileStorageKey("bookmarks")
        val old = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        old.add(uri)
        preferences.edit().putStringSet(key, old).apply()
        return true
    }

    fun bookmarks(): List<String> = preferences.getStringSet(profileStorageKey("bookmarks"), emptySet()).orEmpty().sorted()

    fun removeBookmark(uri: String) {
        val key = profileStorageKey("bookmarks")
        val old = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        old.remove(uri)
        preferences.edit().putStringSet(key, old).apply()
    }

    fun history(): List<String> = preferences.getStringSet(profileStorageKey("history"), emptySet()).orEmpty().toList().sortedDescending()

    fun clearHistory() { preferences.edit().remove(profileStorageKey("history")).apply() }

    private fun recordVisit(uri: String, privateMode: Boolean) {
        if (privateMode || !(uri.startsWith("https://") || uri.startsWith("http://"))) return
        val key = profileStorageKey("history")
        val entries = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        entries.add(uri)
        while (entries.size > 150) entries.remove(entries.first())
        preferences.edit().putStringSet(key, entries).apply()
    }

    fun createProfile(name: String) {
        val clean = name.trim().take(32)
        if (clean.isBlank()) return
        val id = "profile-${UUID.randomUUID()}"
        val current = profiles.toMutableList()
        current.add(id to clean)
        saveProfiles(current)
        selectProfile(id)
    }

    fun selectProfile(id: String) {
        val profile = profiles.firstOrNull { it.first == id } ?: return
        activeProfileId = profile.first
        activeProfileName = profile.second
        preferences.edit().putString("active_profile", activeProfileId).apply()
        newTab()
    }

    fun deleteProfile(id: String) {
        if (id == "profile-default") return
        val removedTabs = tabs.filter { it.profileId == id }
        val selectedRemoved = removedTabs.any { it.id == selectedTabId }
        removedTabs.forEach { runCatching { it.session.close() } }
        tabs.removeAll(removedTabs.toSet())
        if (selectedRemoved) {
            selectedTabId = tabs.firstOrNull()?.id
            selectedTab?.session?.setActive(true)
        }
        runCatching { runtime?.storageController?.clearDataForSessionContext(id) }
        val updated = profiles.filterNot { it.first == id }
        saveProfiles(updated)
        if (activeProfileId == id) selectProfile("profile-default")
    }

    fun clearBrowsingData() {
        val storage = runtime?.storageController ?: return
        closeSessions()
        val contextId = activeProfileId
        runCatching { storage.clearDataForSessionContext(contextId) }
        awaitStorageBarrier(storage) {
            if (!activity.isFinishing && !activity.isDestroyed) {
                preferences.edit().remove("history_$contextId").remove("bookmarks_$contextId").apply()
                newTab()
            }
        }
    }

    private fun profileStorageKey(name: String): String = "${name}_${activeProfileId}"

    fun clearProfileData(profileId: String, onComplete: () -> Unit) {
        tabs.filter { it.profileId == profileId }.toList().forEach { tab ->
            tabs.remove(tab)
            runCatching { tab.session.close() }
        }
        selectedTabId = tabs.firstOrNull()?.id
        preferences.edit().remove("history_$profileId").remove("bookmarks_$profileId").apply()
        val storage = runtime?.storageController
        if (storage == null) {
            onComplete()
        } else {
            runCatching { storage.clearDataForSessionContext(profileId) }
            awaitStorageBarrier(storage) {
                if (!activity.isFinishing && !activity.isDestroyed) onComplete()
            }
        }
    }

    private fun awaitStorageBarrier(storage: StorageController, onComplete: () -> Unit) {
        val completion = { mainHandler.postDelayed(onComplete, 600L) }
        runCatching { storage.getAllPermissions() }
            .onSuccess { it.accept({ completion() }, { completion() }) }
            .onFailure { completion() }
    }

    fun shutdown() {
        closeSessions()
        runCatching { runtime?.shutdown() }
        runtime = null
        socks5Bridge?.close()
        socks5Bridge = null
    }

    private fun configureProfileEnvironment(session: GeckoSession) {
        val currentRuntime = runtime ?: return
        val config = launchConfig ?: return
        session.setPromptDelegate(object : GeckoSession.PromptDelegate {
            override fun onAuthPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AuthPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                val flags = prompt.authOptions.flags
                val isProxyPrompt = flags and GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.PROXY != 0
                if (!isProxyPrompt || config.mode != "profile" || config.proxyUsername.isBlank() && config.proxyPassword.isBlank()) return null
                if (flags and GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.PREVIOUS_FAILED != 0) {
                    return GeckoResult.fromValue(prompt.dismiss())
                }
                val response = if (flags and GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD != 0) {
                    prompt.confirm(config.proxyPassword)
                } else {
                    prompt.confirm(config.proxyUsername, config.proxyPassword)
                }
                return GeckoResult.fromValue(response)
            }
        })
        currentRuntime.webExtensionController.ensureBuiltIn(PROFILE_EXTENSION_URI, PROFILE_EXTENSION_ID).accept(
            { extension ->
                val delegate = object : WebExtension.MessageDelegate {
                    override fun onMessage(
                        nativeApp: String,
                        message: Any,
                        sender: WebExtension.MessageSender,
                    ): GeckoResult<Any>? {
                        val action = (message as? org.json.JSONObject)?.optString("action")
                        if (nativeApp != "kun.browser" || action != "profile-environment") return null
                        return GeckoResult.fromValue(
                            org.json.JSONObject()
                            .put("enabled", config.mode in setOf("profile", "direct"))
                                .put("locationMode", config.locationMode)
                                .put("latitude", config.latitude.toDoubleOrNull() ?: org.json.JSONObject.NULL)
                                .put("longitude", config.longitude.toDoubleOrNull() ?: org.json.JSONObject.NULL),
                        )
                    }
                }
                if (extension != null) {
                    activity.runOnUiThread {
                        session.webExtensionController.setMessageDelegate(extension, delegate, "kun.browser")
                    }
                }
            },
            { _ -> },
        )
    }

    private fun loadProfiles() {
        val stored = preferences.getString("profiles", null)
        val parsed = stored.orEmpty().split("\n").mapNotNull { line ->
            val split = line.split("|", limit = 2)
            if (split.size == 2 && split[0].isNotBlank() && split[1].isNotBlank()) split[0] to split[1] else null
        }.toMutableList()
        if (parsed.none { it.first == "profile-default" }) parsed.add(0, "profile-default" to "الافتراضي")
        profiles.addAll(parsed)
        val selected = preferences.getString("active_profile", "profile-default")
        val profile = profiles.firstOrNull { it.first == selected } ?: profiles.first()
        activeProfileId = profile.first
        activeProfileName = profile.second
    }

    private fun saveProfiles(value: List<Pair<String, String>>) {
        profiles.clear()
        profiles.addAll(value)
        preferences.edit().putString("profiles", value.joinToString("\n") { "${it.first}|${it.second}" }).apply()
    }

    private fun closeSessions() {
        tabs.toList().forEach { runCatching { it.session.close() } }
        tabs.clear()
        selectedTabId = null
    }

    private fun writeRuntimeConfig(config: BrowserLaunchConfig, socks5BridgePort: Int): File {
        val file = File(activity.filesDir, "gecko-runtime.yaml")
        val safeLanguage = config.language.filter { it.isLetterOrDigit() || it in "-_, " }.ifBlank { "en-US,en" }
        val proxyPrefs = if (config.mode == "direct") {
            listOf(
                "network.proxy.type: 0",
                "media.peerconnection.ice.proxy_only: false",
            )
        } else if (config.connectionReady && config.mode == "profile" && config.proxyProtocol == "http") {
            val host = yamlString(config.proxyHost)
            listOf(
                "network.proxy.type: 1",
                "network.proxy.http: $host",
                "network.proxy.http_port: ${config.localPort}",
                "network.proxy.ssl: $host",
                "network.proxy.ssl_port: ${config.localPort}",
                "network.proxy.share_proxy_settings: true",
                "network.proxy.failover_direct: false",
                "media.peerconnection.ice.proxy_only: true",
            )
        } else if (config.connectionReady && config.mode == "profile" && config.proxyProtocol == "socks5") {
            require(socks5BridgePort in 1..65535) { "تعذّر تشغيل وسيط SOCKS5 المحلي." }
            listOf(
                "network.proxy.type: 1",
                "network.proxy.socks: \"127.0.0.1\"",
                "network.proxy.socks_port: $socks5BridgePort",
                "network.proxy.socks_version: 5",
                "network.proxy.socks_remote_dns: true",
                "network.proxy.failover_direct: false",
                "media.peerconnection.ice.proxy_only: true",
            )
        } else if (config.connectionReady) {
            listOf(
                "network.proxy.type: 1",
                "network.proxy.socks: \"127.0.0.1\"",
                "network.proxy.socks_port: ${config.localPort}",
                "network.proxy.socks_version: 5",
                "network.proxy.socks_remote_dns: true",
                "network.proxy.failover_direct: false",
                "media.peerconnection.ice.proxy_only: true",
            )
        } else {
            listOf(
                "network.proxy.type: 1",
                "network.proxy.socks: \"127.0.0.1\"",
                "network.proxy.socks_port: 1",
                "network.proxy.socks_version: 5",
                "network.proxy.socks_remote_dns: true",
                "network.proxy.failover_direct: false",
                "media.peerconnection.ice.proxy_only: true",
            )
        }
        val yaml = (
            listOf(
                "prefs:",
                "  intl.accept_languages: ${yamlString(safeLanguage)}",
                "  privacy.firstparty.isolate: true",
            ) + proxyPrefs.map { "  $it" }
        ).joinToString("\n", postfix = "\n")
        file.writeText(yaml)
        return file
    }

    private fun yamlString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")}\""

    private fun normalizeAddress(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val candidate = when {
            value.startsWith("https://", true) || value.startsWith("http://", true) -> value
            value.contains(' ') || !value.contains('.') -> "https://www.google.com/search?q=${Uri.encode(value)}"
            else -> "https://$value"
        }
        val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https")) return null
        return candidate
    }
}

class BrowserActivity : ComponentActivity() {
    private lateinit var controller: BrowserController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = BrowserController(this)
        if (intent.action == ACTION_CLEAR_PROFILE) {
            controller.clearProfileData(intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty()) { finish() }
            return
        }
        setContent {
            MaterialTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    BrowserScreen(controller)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_CLEAR_PROFILE) {
            controller.clearProfileData(intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty()) { finish() }
        } else {
            controller.applyLaunchConfig(BrowserLaunchConfig.from(intent))
        }
    }

    override fun onDestroy() {
        if (isFinishing && ::controller.isInitialized) controller.shutdown()
        super.onDestroy()
    }
}
