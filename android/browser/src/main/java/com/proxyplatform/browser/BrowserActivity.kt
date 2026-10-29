package com.proxyplatform.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
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
import java.io.File
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

private const val EXTRA_PROXY_MODE = "kun.browser.proxy_mode"
private const val EXTRA_LOCAL_PORT = "kun.browser.local_port"
private const val EXTRA_LANGUAGE = "kun.browser.language"
private const val EXTRA_TIME_ZONE = "kun.browser.time_zone"

private data class BrowserLaunchConfig(
    val mode: String,
    val localPort: Int,
    val language: String,
    val timeZone: String,
) {
    val connectionReady: Boolean
        get() = mode in setOf("vpn", "advanced") && localPort in 1024..65535

    fun signature(): String = "$mode|$localPort|$language|$timeZone"

    companion object {
        fun from(intent: Intent?): BrowserLaunchConfig = BrowserLaunchConfig(
            mode = intent?.getStringExtra(EXTRA_PROXY_MODE).orEmpty(),
            localPort = intent?.getIntExtra(EXTRA_LOCAL_PORT, 0) ?: 0,
            language = intent?.getStringExtra(EXTRA_LANGUAGE)?.takeIf(String::isNotBlank)
                ?: Locale.getDefault().toLanguageTag(),
            timeZone = intent?.getStringExtra(EXTRA_TIME_ZONE)?.takeIf(String::isNotBlank)
                ?: TimeZone.getDefault().id,
        )
    }
}

private class BrowserTab(
    val id: String,
    val session: GeckoSession,
    val privateMode: Boolean,
    val profileId: String,
) {
    var title by mutableStateOf(if (privateMode) "تبويب خاص" else "تبويب جديد")
    var uri by mutableStateOf("")
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
}

private class BrowserController(private val activity: BrowserActivity) {
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
    var connectionLabel by mutableStateOf("افتح المتصفح من KUN بعد تشغيل الاتصال")
        private set
    var languageLabel by mutableStateOf(Locale.getDefault().toLanguageTag())
        private set
    var timeZoneLabel by mutableStateOf(TimeZone.getDefault().id)
        private set
    var configError by mutableStateOf<String?>(null)
        private set

    private var runtime: GeckoRuntime? = null
    private var configSignature: String? = null
    private val preferences = activity.getSharedPreferences("kun_browser", 0)

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
        connectionReady = config.connectionReady
        connectionLabel = when (config.mode) {
            "vpn" -> "اتصال KUN VPN"
            "advanced" -> "بروكسي KUN المحلي"
            else -> "افتح المتصفح من KUN بعد تشغيل الاتصال"
        }
        languageLabel = config.language
        timeZoneLabel = config.timeZone
        configSignature = config.signature()
        try {
            val configFile = writeRuntimeConfig(config)
            val settings = GeckoRuntimeSettings.Builder()
                .configFilePath(configFile.absolutePath)
                .globalPrivacyControlEnabled(true)
                .build()
            runtime = GeckoRuntime.create(activity.applicationContext, settings)
            configError = null
            newTab(privateMode = false)
        } catch (failure: Exception) {
            configError = failure.message ?: "تعذر تهيئة محرك المتصفح."
            connectionReady = false
        }
    }

    fun newTab(privateMode: Boolean = false) {
        createTab(privateMode, activeProfileId, "about:blank")
    }

    private fun createTab(privateMode: Boolean, profileId: String, initialUri: String): BrowserTab? {
        val currentRuntime = runtime ?: return null
        val settingsBuilder = GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .useTrackingProtection(true)
        if (!privateMode) settingsBuilder.contextId(profileId)
        val session = GeckoSession(settingsBuilder.build())
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
                recordVisit(url.orEmpty(), tab.privateMode)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
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
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.loading = false
            }
        })
        session.open(currentRuntime)
        tabs.add(tab)
        selectTab(tab.id)
        if (connectionReady) session.loadUri(initialUri)
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
        tab.uri = target
        tab.session.loadUri(target)
    }

    fun goBack() { selectedTab?.session?.goBack() }
    fun goForward() { selectedTab?.session?.goForward() }
    fun reload() { selectedTab?.session?.reload() }
    fun stop() { selectedTab?.session?.stop() }

    fun addBookmark(): Boolean {
        val uri = selectedTab?.uri?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return false
        val old = preferences.getStringSet("bookmarks", emptySet()).orEmpty().toMutableSet()
        old.add(uri)
        preferences.edit().putStringSet("bookmarks", old).apply()
        return true
    }

    fun bookmarks(): List<String> = preferences.getStringSet("bookmarks", emptySet()).orEmpty().sorted()

    fun removeBookmark(uri: String) {
        val old = preferences.getStringSet("bookmarks", emptySet()).orEmpty().toMutableSet()
        old.remove(uri)
        preferences.edit().putStringSet("bookmarks", old).apply()
    }

    fun history(): List<String> = preferences.getStringSet("history", emptySet()).orEmpty().toList().sortedDescending()

    fun clearHistory() { preferences.edit().remove("history").apply() }

    private fun recordVisit(uri: String, privateMode: Boolean) {
        if (privateMode || !(uri.startsWith("https://") || uri.startsWith("http://"))) return
        val entries = preferences.getStringSet("history", emptySet()).orEmpty().toMutableSet()
        entries.add(uri)
        while (entries.size > 150) entries.remove(entries.first())
        preferences.edit().putStringSet("history", entries).apply()
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
        runCatching { storage.clearData(StorageController.ClearFlags.ALL) }
        clearHistory()
        newTab()
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

    private fun writeRuntimeConfig(config: BrowserLaunchConfig): File {
        val file = File(activity.filesDir, "gecko-runtime.yaml")
        val safeLanguage = config.language.filter { it.isLetterOrDigit() || it in "-_, " }.ifBlank { "en-US,en" }
        val proxyPrefs = if (config.connectionReady) {
            listOf(
                "network.proxy.type: 1",
                "network.proxy.socks: \"127.0.0.1\"",
                "network.proxy.socks_port: ${config.localPort}",
                "network.proxy.socks_version: 5",
                "network.proxy.socks_remote_dns: true",
                "network.proxy.failover_DIRECT: false",
                "media.peerconnection.ice.proxy_only: true",
            )
        } else {
            listOf(
                "network.proxy.type: 1",
                "network.proxy.socks: \"127.0.0.1\"",
                "network.proxy.socks_port: 1",
                "network.proxy.socks_version: 5",
                "network.proxy.socks_remote_dns: true",
                "network.proxy.failover_DIRECT: false",
                "media.peerconnection.ice.proxy_only: true",
            )
        }
        val yaml = (listOf("prefs:", "  intl.accept_languages: \"$safeLanguage\"") + proxyPrefs.map { "  $it" }).joinToString("\n", postfix = "\n")
        file.writeText(yaml)
        return file
    }

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
        controller.applyLaunchConfig(BrowserLaunchConfig.from(intent))
    }

    override fun onDestroy() {
        if (isFinishing) {
            controller.tabs.toList().forEach { runCatching { it.session.close() } }
        }
        super.onDestroy()
    }
}

@Composable
private fun BrowserScreen(controller: BrowserController) {
    val activeTab = controller.selectedTab
    var address by remember(activeTab?.id) { mutableStateOf(activeTab?.uri.orEmpty()) }
    var showProfiles by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeTab?.uri) { address = activeTab?.uri.orEmpty() }
    BackHandler(enabled = activeTab?.canGoBack == true) { controller.goBack() }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF4F7FC)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("KUN Browser", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF15233B))
                        Text(controller.connectionLabel, style = MaterialTheme.typography.labelSmall, color = if (controller.connectionReady) Color(0xFF16866F) else Color(0xFFBE3C45))
                    }
                    TextButton(onClick = { showProfiles = true }) { Text(controller.activeProfileName, fontWeight = FontWeight.Bold) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { if (activeTab?.canGoBack == true) controller.goBack() }) { Text("‹") }
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("ابحث أو اكتب عنوان موقع") },
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { controller.goToAddress(address) }),
                        colors = TextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White),
                    )
                    TextButton(onClick = { controller.goToAddress(address) }) { Text("اذهب", fontWeight = FontWeight.Bold) }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 1.dp)) {
                    items(controller.tabs, key = { it.id }) { tab ->
                        FilterChip(
                            selected = tab.id == activeTab?.id,
                            onClick = { controller.selectTab(tab.id) },
                            label = { Text((if (tab.privateMode) "خاص · " else "") + tab.title.take(18), maxLines = 1) },
                            trailingIcon = { TextButton(onClick = { controller.closeTab(tab.id) }, modifier = Modifier.size(28.dp)) { Text("×") } },
                        )
                    }
                    item { TextButton(onClick = { controller.newTab() }) { Text("+ تبويب") } }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (activeTab != null) {
                    key(activeTab.id) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context -> GeckoView(context).apply { setSession(activeTab.session) } },
                        )
                    }
                }
                if (!controller.connectionReady || controller.configError != null) {
                    Surface(Modifier.fillMaxSize(), color = Color(0xFFF4F7FC).copy(alpha = 0.96f)) {
                        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("المتصفح جاهز", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                            Text(controller.configError ?: "شغّل اتصال البروكسي في KUN ثم افتح المتصفح من صفحة الاتصال.", modifier = Modifier.padding(top = 8.dp), color = Color(0xFF68768C))
                            Text("لن يفتح المتصفح المواقع باتصال مباشر عند غياب اتصال KUN.", modifier = Modifier.padding(top = 8.dp), color = Color(0xFFBE3C45), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (activeTab?.loading == true) {
                    CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(8.dp).size(22.dp), strokeWidth = 2.dp)
                }
            }

            Surface(color = Color.White, tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = activeTab?.canGoBack == true, onClick = { controller.goBack() }) { Text("رجوع") }
                    TextButton(enabled = activeTab?.canGoForward == true, onClick = { controller.goForward() }) { Text("التالي") }
                    TextButton(onClick = { if (activeTab?.loading == true) controller.stop() else controller.reload() }) { Text(if (activeTab?.loading == true) "إيقاف" else "تحديث") }
                    TextButton(onClick = { feedback = if (controller.addBookmark()) "أُضيفت العلامة" else "افتح صفحة أولًا") }) { Text("حفظ") }
                    TextButton(onClick = { showBookmarks = true }) { Text("المحفوظات") }
                    TextButton(onClick = { showMenu = true }) { Text("المزيد") }
                }
            }
        }
    }

    if (showProfiles) {
        AlertDialog(
            onDismissRequest = { showProfiles = false },
            title = { Text("ملفات التصفح") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    controller.profiles.forEach { (id, name) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { controller.selectProfile(id); showProfiles = false }) { Text(if (id == controller.activeProfileId) "✓ $name" else name) }
                            if (id != "profile-default") TextButton(onClick = { controller.deleteProfile(id) }) { Text("حذف") }
                        }
                    }
                    OutlinedTextField(value = profileName, onValueChange = { profileName = it }, label = { Text("اسم ملف جديد") }, singleLine = true)
                    Text("كل ملف يحتفظ ببيانات مواقع مستقلة عن الملفات الأخرى.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF68768C))
                }
            },
            confirmButton = { TextButton(onClick = { controller.createProfile(profileName); profileName = ""; showProfiles = false }) { Text("إنشاء") } },
            dismissButton = { TextButton(onClick = { showProfiles = false }) { Text("إغلاق") } },
        )
    }

    if (showBookmarks) {
        AlertDialog(
            onDismissRequest = { showBookmarks = false },
            title = { Text("المحفوظات") },
            text = {
                if (controller.bookmarks().isEmpty()) Text("لا توجد صفحات محفوظة بعد.")
                else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    controller.bookmarks().forEach { uri ->
                        TextButton(onClick = { controller.goToAddress(uri); address = uri; showBookmarks = false }) { Text(uri, maxLines = 1) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBookmarks = false }) { Text("تم") } },
        )
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text("إعدادات التصفح") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اللغة: ${controller.languageLabel}")
                    Text("المنطقة الزمنية: ${controller.timeZoneLabel}")
                    Text("حماية التتبع وGlobal Privacy Control مفعّلان.", style = MaterialTheme.typography.bodySmall)
                    Text("اتصال المتصفح يتبع اتصال KUN النشط. إعدادات اللغة/الوقت لا تضمن إخفاء بصمة الجهاز.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF68768C))
                    OutlinedButton(onClick = { showHistory = true; showMenu = false }) { Text("سجل التصفح") }
                    OutlinedButton(onClick = { controller.newTab(privateMode = true); showMenu = false }) { Text("تبويب خاص جديد") }
                    OutlinedButton(onClick = { controller.clearBrowsingData(); showMenu = false }) { Text("مسح بيانات التصفح") }
                }
            },
            confirmButton = { TextButton(onClick = { showMenu = false }) { Text("إغلاق") } },
        )
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("سجل التصفح") },
            text = {
                if (controller.history().isEmpty()) Text("لا يوجد سجل محفوظ.")
                else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    controller.history().forEach { uri ->
                        TextButton(onClick = { controller.goToAddress(uri); address = uri; showHistory = false }) { Text(uri, maxLines = 1) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { controller.clearHistory(); showHistory = false }) { Text("مسح السجل") } },
            dismissButton = { TextButton(onClick = { showHistory = false }) { Text("إغلاق") } },
        )
    }

    if (feedback != null) {
        AlertDialog(onDismissRequest = { feedback = null }, title = { Text("KUN Browser") }, text = { Text(feedback.orEmpty()) }, confirmButton = { TextButton(onClick = { feedback = null }) { Text("حسنًا") } })
    }
}
