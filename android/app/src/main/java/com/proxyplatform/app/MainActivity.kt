package com.proxyplatform.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.proxyplatform.app.adb.AdbPairingNotifier
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal data class Product(
    val id: String,
    val name: String,
    val description: String,
    val protocol: String,
    val country: String,
    val city: String,
    val ipType: String,
    val daily: String,
    val weekly: String,
    val monthly: String,
    val featured: Boolean,
)
internal data class Profile(val email: String, val name: String, val role: String, val verified: Boolean)
internal data class Subscription(val status: String, val expires: String, val product: String, val protocol: String)
internal enum class Screen { MARKET, SUBSCRIPTIONS, PROXY, PROFILE }

private val SuccessGreen = KunPalette.Success
private val SuccessGreenContainer = KunPalette.SuccessSoft
private val OnSuccessGreenContainer = Color(0xFF14624F)

@Composable private fun ProxyTheme(content: @Composable () -> Unit) = KUNTheme(content)

private fun formatProductPrice(value: Double, period: String): String {
    if (!value.isFinite() || value < 0.0) return ""
    val amount = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(value)
    return "$amount دولار / $period"
}

private class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("proxy_session", Context.MODE_PRIVATE)
    var accessToken: String? get() = prefs.getString("access_token", null); set(value) { prefs.edit().putString("access_token", value).apply() }
    var refreshToken: String? get() = prefs.getString("refresh_token", null); set(value) { prefs.edit().putString("refresh_token", value).apply() }
    fun clear() = prefs.edit().clear().apply()
}

internal class ApiClient(context: Context) {
    private val appContext = context.applicationContext
    private val store = SessionStore(appContext)
    private val client = OkHttpClient()
    private val jsonType = "application/json".toMediaType()
    private fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        AdvancedOperationLog.info(appContext, "HTTP $method $path — بيانات الاعتماد ومتن الطلب لا تُسجّل.")
        val builder = Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + path).header("Accept", "application/json")
        store.accessToken?.let { builder.header("Authorization", "Bearer " + it) }
        if (body != null) builder.method(method, body.toString().toRequestBody(jsonType)) else builder.method(method, null)
        return try {
            client.newCall(builder.build()).execute().use { response ->
                AdvancedOperationLog.info(appContext, "HTTP $method $path -> ${response.code}.")
                val raw = response.body?.string().orEmpty(); val result = if (raw.isBlank()) JSONObject() else JSONObject(raw)
                if (!response.isSuccessful) {
                    AdvancedOperationLog.error(appContext, "HTTP request failed with status ${response.code}.")
                    throw IllegalStateException(result.optJSONObject("error")?.optString("message") ?: "فشل الطلب (" + response.code + ")")
                }
                result
            }
        } catch (failure: Exception) {
            AdvancedOperationLog.error(appContext, "HTTP $method $path exception: ${failure.javaClass.simpleName}.")
            throw failure
        }
    }
    fun login(email: String, password: String) { val d = request("/auth/login", "POST", JSONObject().put("email", email.trim()).put("password", password)).getJSONObject("data"); store.accessToken = d.getString("accessToken"); store.refreshToken = d.optString("refreshToken") }
    fun register(email: String, password: String, name: String) { request("/auth/register", "POST", JSONObject().put("email", email.trim()).put("password", password).put("fullName", name.trim())) }
    fun products(): List<Product> {
        val rows = request("/products").optJSONArray("data")
            ?: throw IllegalStateException("استجابة السوق غير صالحة.")
        return (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            val country = listOf(item.optString("country_name"), item.optString("country_code"))
                .map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(" • ")
            Product(
                id = item.getString("id"),
                name = item.getString("name"),
                description = item.optString("description").trim(),
                protocol = item.optString("protocol").uppercase(),
                country = country,
                city = item.optString("city").trim(),
                ipType = item.optString("ip_type").trim(),
                daily = formatProductPrice(item.optDouble("price_daily", Double.NaN), "يوم"),
                weekly = formatProductPrice(item.optDouble("price_weekly", Double.NaN), "أسبوع"),
                monthly = formatProductPrice(item.optDouble("price_monthly", Double.NaN), "شهر"),
                featured = item.optBoolean("is_featured"),
            )
        }
    }
    fun profile(): Profile { val x = request("/me").getJSONObject("data"); return Profile(x.optString("email"), x.optString("full_name", "بدون اسم"), x.optString("role", "user"), x.optBoolean("is_email_verified")) }
    fun subscriptions(): List<Subscription> { val a = request("/me/subscriptions").getJSONArray("data"); return (0 until a.length()).map { val x = a.getJSONObject(it); val p = x.optJSONObject("proxy_products"); Subscription(x.optString("status"), x.optString("expires_at"), p?.optString("name", "البروكسي") ?: "البروكسي", p?.optString("protocol", "") ?: "") } }
    fun loggedIn() = store.accessToken != null
    fun logout() = store.clear()
}

internal class AppViewModel(private val api: ApiClient) : ViewModel() {
    var loggedIn by mutableStateOf(api.loggedIn()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    var products by mutableStateOf<List<Product>>(emptyList()); private set
    var productsLoading by mutableStateOf(false); private set
    var productsError by mutableStateOf<String?>(null); private set

    var profile by mutableStateOf<Profile?>(null); private set
    var profileLoading by mutableStateOf(false); private set
    var profileError by mutableStateOf<String?>(null); private set

    var subscriptions by mutableStateOf<List<Subscription>>(emptyList()); private set
    var subscriptionsLoading by mutableStateOf(false); private set
    var subscriptionsError by mutableStateOf<String?>(null); private set

    fun login(email: String, password: String) {
        loading = true
        error = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                api.login(email, password)
                withContext(Dispatchers.Main) {
                    loggedIn = true
                    loading = false
                    loadProducts()
                }
            } catch (failure: Exception) {
                withContext(Dispatchers.Main) {
                    error = failure.message ?: "تعذر تسجيل الدخول. تحقق من بياناتك وحاول مجددًا."
                    loading = false
                }
            }
        }
    }

    fun register(email: String, password: String, name: String, done: () -> Unit) {
        loading = true
        error = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                api.register(email, password, name)
                withContext(Dispatchers.Main) {
                    loading = false
                    error = "تم إنشاء الحساب. سجّل الدخول للمتابعة."
                    done()
                }
            } catch (failure: Exception) {
                withContext(Dispatchers.Main) {
                    error = failure.message ?: "تعذر إنشاء الحساب. تحقق من البيانات وحاول مجددًا."
                    loading = false
                }
            }
        }
    }

    fun loadProducts() = viewModelScope.launch {
        if (productsLoading) return@launch
        productsLoading = true
        productsError = null
        try {
            products = withContext(Dispatchers.IO) { api.products() }
        } catch (failure: Exception) {
            productsError = when {
                failure.message.orEmpty().contains("Could not load products", ignoreCase = true) ->
                    "خدمة السوق غير متاحة مؤقتًا. أعد المحاولة بعد قليل."
                failure.message.orEmpty().contains("استجابة السوق", ignoreCase = true) ->
                    "وصل رد غير مكتمل من خدمة السوق. أعد المحاولة."
                else -> "تعذر الاتصال بخدمة السوق. تحقق من الإنترنت ثم أعد المحاولة."
            }
        } finally {
            productsLoading = false
        }
    }

    fun loadProfile() = viewModelScope.launch {
        if (profileLoading) return@launch
        profileLoading = true
        profileError = null
        try {
            profile = withContext(Dispatchers.IO) { api.profile() }
        } catch (failure: Exception) {
            profileError = failure.message ?: "تعذر تحميل بيانات الحساب. أعد المحاولة."
        } finally {
            profileLoading = false
        }
    }

    fun loadSubscriptions() = viewModelScope.launch {
        if (subscriptionsLoading) return@launch
        subscriptionsLoading = true
        subscriptionsError = null
        try {
            subscriptions = withContext(Dispatchers.IO) { api.subscriptions() }
        } catch (failure: Exception) {
            subscriptionsError = failure.message ?: "تعذر تحميل الاشتراكات. أعد المحاولة."
        } finally {
            subscriptionsLoading = false
        }
    }

    fun logout() {
        api.logout()
        loggedIn = false
        products = emptyList()
        productsError = null
        profile = null
        profileError = null
        subscriptions = emptyList()
        subscriptionsError = null
        error = null
    }

    fun clearError() { error = null }
}

private class AppViewModelFactory(private val api: ApiClient) : androidx.lifecycle.ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(api) as T }

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val api = ApiClient(this)
        setContent { ProxyTheme { val vm: AppViewModel = viewModel(factory = AppViewModelFactory(api)); ProxyPlatformApp(vm) } }
    }
}

@Composable private fun ProxyPlatformApp(vm: AppViewModel) { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { if (vm.loggedIn) MainShell(vm) else AuthScreen(vm) } } }
// ─────────────────────────────────────────────────────────────────────────────
// Proxy Screen — local proxy + embedded Wireless ADB flow
// ─────────────────────────────────────────────────────────────────────────────

@Composable internal fun ProxyScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val locationController = remember { ProxyLocationController(context) }
    DisposableEffect(Unit) { onDispose { locationController.close() } }

    // "vpn" = recommended one-tap mode (single system dialog, no Shizuku).
    // "advanced" = original local-proxy mode for users who want no VPN icon.
    var mode by remember {
        mutableStateOf(
            if (ProxyLocalService.isRunning(context) || AdvancedSystemProxy.hasPendingRestore(context))
                "advanced" else "vpn"
        )
    }

    var protocol by remember { mutableStateOf("socks5") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var auth by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val profileStore = remember(context) { ProxyProfileStore(context.applicationContext) }
    var savedProxies by remember { mutableStateOf(emptyList<SavedProxy>()) }
    var selectedSavedProxyId by remember { mutableStateOf<String?>(null) }
    var running by remember {
        mutableStateOf(ProxyLocalService.isRunning(context) || ProxyVpnService.isRunning(context))
    }
    var starting by remember { mutableStateOf(false) }
    var checkingEndpoint by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var mockLocation by remember { mutableStateOf(false) }
    var locationMode by remember { mutableStateOf("auto") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var wirelessState by remember {
        mutableStateOf(WirelessDebuggingManager.DebuggingState.NOT_PAIRED)
    }

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Permission requests are asynchronous: launch() returns immediately, and
    // the actual grant/deny result only arrives later in this callback. Code
    // that needs the permission for something specific (like posting the ADB
    // pairing notification) must act *here*, once it's actually granted — not
    // right after calling launch(), since at that point it usually isn't
    // granted yet. This gap is what previously made the pairing notification
    // silently fail to appear on a fresh install: the button asked for the
    // permission and posted the notification in the same instant, so the post
    // always ran before the user had answered the permission dialog.
    var showPairingNotificationOnGrant by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && showPairingNotificationOnGrant) {
            WirelessDebuggingManager.showPairingNotification(context)
        }
        showPairingNotificationOnGrant = false
        // If denied, the tunnel still works — it just hides the status/pairing
        // notification and the user falls back to the in-app pairing field.
    }

    // Best-effort request used by the plain VPN/local-proxy status notification.
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Gets the ADB pairing notification on screen: immediately if permission
    // is already granted, or as soon as the user grants it via the callback
    // above. Safe to call as many times as needed (e.g. automatically).
    fun ensurePairingNotification() {
        if (hasNotificationPermission()) {
            WirelessDebuggingManager.showPairingNotification(context)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            showPairingNotificationOnGrant = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Wireless debugging state is rendered in the advanced setup card below.

    val coroutineScope = rememberCoroutineScope()

    fun selectProxyProfile(profile: SavedProxy) {
        if (running || starting || stopping) return
        selectedSavedProxyId = profile.id
        protocol = profile.protocol
        host = profile.host
        port = profile.port.toString()
        auth = profile.authenticationRequired
        username = profile.username
        password = profile.password
        coroutineScope.launch {
            runCatching { withContext(Dispatchers.IO) { profileStore.select(profile.id) } }
                .onFailure { error = "تعذر حفظ اختيار البروكسي: ${it.message ?: "خطأ في قاعدة البيانات"}" }
        }
    }

    fun saveProxyProfile(name: String, existingId: String?) {
        val candidate = SavedProxy(
            id = existingId.orEmpty(), name = name, protocol = protocol,
            host = host.trim(), port = port.toIntOrNull() ?: 0,
            authenticationRequired = auth, username = username, password = password,
        )
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val saved = profileStore.save(candidate)
                    profileStore.loadAll() to saved
                }
            }.onSuccess { (profiles, saved) ->
                savedProxies = profiles
                selectedSavedProxyId = saved.id
                error = null
                AdvancedOperationLog.output(context, "حُفظ إعداد بروكسي محليًا في قاعدة البيانات المشفرة.")
            }.onFailure { error = it.message ?: "تعذر حفظ البروكسي. تحقق من المعلومات وحاول مجددًا." }
        }
    }

    fun deleteProxyProfile(profile: SavedProxy) {
        if (running || starting || stopping) return
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    profileStore.delete(profile.id)
                    profileStore.loadAll()
                }
            }.onSuccess { profiles ->
                savedProxies = profiles
                val selectedProfile = profiles.firstOrNull { it.selected } ?: profiles.firstOrNull()
                selectedSavedProxyId = selectedProfile?.id
                if (selectedProfile != null) selectProxyProfile(selectedProfile)
                else {
                    selectedSavedProxyId = null
                    host = ""; port = ""; username = ""; password = ""; auth = false
                }
            }.onFailure { error = it.message ?: "تعذر حذف البروكسي المحفوظ." }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { profileStore.loadAll() } }
            .onSuccess { profiles ->
                savedProxies = profiles
                val selectedProfile = profiles.firstOrNull { it.selected } ?: profiles.firstOrNull()
                if (selectedProfile != null) {
                    selectedSavedProxyId = selectedProfile.id
                    protocol = selectedProfile.protocol
                    host = selectedProfile.host
                    port = selectedProfile.port.toString()
                    auth = selectedProfile.authenticationRequired
                    username = selectedProfile.username
                    password = selectedProfile.password
                }
            }
            .onFailure {
                error = "تعذر فتح قاعدة بيانات البروكسيات المحفوظة: ${it.message ?: "تعذر فك التشفير"}"
                AdvancedOperationLog.error(context, "تعذر تحميل بيانات اعتماد البروكسي المشفرة.")
            }
        if (!ProxyLocalService.isRunning(context) && AdvancedSystemProxy.hasPendingRestore(context)) {
            AdvancedOperationLog.info(context, "اكتشاف جلسة متقدمة سابقة؛ محاولة استعادة بروكسي النظام.")
            val restoreResult = withContext(Dispatchers.IO) {
                AdvancedSystemProxy.restore(
                    context,
                    "127.0.0.1:${ProxyLocalService.localPort(context)}"
                )
            }
            if (restoreResult.isFailure) {
                AdvancedOperationLog.error(context, "تعذرت استعادة الجلسة السابقة تلقائيًا؛ يلزم اتصال ADB.")
                error = "تعذر استعادة إعدادات البروكسي السابقة: " +
                    (restoreResult.exceptionOrNull()?.message ?: "تحقق من اتصال ADB.")
            } else {
                AdvancedOperationLog.info(context, "اكتملت معالجة إعدادات الجلسة السابقة.")
                mode = "vpn"
            }
        }
    }

    LaunchedEffect(mode) {
        if (mode == "advanced") {
            while (true) {
                val newWirelessState = withContext(Dispatchers.IO) {
                    WirelessDebuggingManager.checkState(context)
                }
                if (newWirelessState != wirelessState) {
                    AdvancedOperationLog.info(context, "حالة Wireless ADB: ${newWirelessState.name}.")
                    wirelessState = newWirelessState
                }
                delay(1_000)
            }
        }
    }

    fun startLocation() {
        if (!mockLocation) return
        runCatching {
            if (locationMode == "auto") locationController.startAuto(protocol, host, port.toInt(), username, password)
            else locationController.startManual(latitude.toDouble(), longitude.toDouble())
        }.onFailure { error = "بدأ البروكسي، لكن تعذر تشغيل الموقع الوهمي." }
    }

    // ── Advanced mode: local proxy + embedded Wireless ADB ─────────────
    fun launchAdvancedTunnel() {
        error = null
        starting = true
        running = false
        AdvancedOperationLog.info(context, "========== بدء تشغيل البروكسي المتقدم ==========")
        AdvancedOperationLog.info(
            context,
            "الخيارات: protocol=${protocol.uppercase()}, port=${port.toIntOrNull() ?: "invalid"}, authentication=$auth؛ بيانات الاعتماد لا تُسجّل."
        )
        ProxyLocalService.clearLastError(context)
        runCatching {
            AdvancedOperationLog.info(context, "إرسال طلب تشغيل خدمة البروكسي المحلي إلى Android.")
            startLocalProxyService(context, protocol, host, port, username, password)
        }.onFailure {
            AdvancedOperationLog.error(context, "تعذر إرسال طلب تشغيل الخدمة: ${it.message ?: it.javaClass.simpleName}")
            starting = false
            error = it.message ?: "تعذر تشغيل خدمة البروكسي المحلي."
            return
        }

        // Keep the UI in "starting" until both the local inbound and Android's
        // system proxy have been verified. A service-running flag alone is not
        // enough to tell the user that device traffic is actually routed.
        coroutineScope.launch {
            val setupResult = withContext(Dispatchers.IO) {
                AdvancedOperationLog.info(context, "انتظار بدء الخدمة واستجابة المنفذ المحلي (مهلة 10 ثوانٍ).")
                var ready = false
                for (attempt in 0 until 40) {
                    if (ProxyLocalService.isRunning(context) && ProxyLocalService.isListenerReady(context)) {
                        ready = true
                        break
                    }
                    delay(250)
                }
                if (!ready || !ProxyLocalService.isRunning(context) || !ProxyLocalService.isListenerReady(context)) {
                    AdvancedOperationLog.error(context, "لم تصبح خدمة البروكسي والمنفذ المحلي جاهزين خلال المهلة.")
                    Result.failure(
                        IllegalStateException(
                            ProxyLocalService.lastError(context) ?: "لم تبدأ خدمة البروكسي المحلي."
                        )
                    )
                } else {
                    val expectedProxy = "127.0.0.1:${ProxyLocalService.localPort(context)}"
                    AdvancedOperationLog.info(context, "أصبح المنفذ المحلي جاهزًا؛ بدء ضبط بروكسي نظام Android.")
                    val result = AdvancedSystemProxy.apply(context, expectedProxy)
                    if (result.isSuccess &&
                        (!ProxyLocalService.isRunning(context) || !ProxyLocalService.isListenerReady(context))
                    ) {
                        AdvancedOperationLog.error(context, "توقفت الخدمة أو المنفذ أثناء تطبيق إعداد النظام.")
                        AdvancedSystemProxy.restore(context, expectedProxy)
                        Result.failure(IllegalStateException("توقفت خدمة البروكسي أثناء إعداد النظام."))
                    } else {
                        result
                    }
                }
            }

            if (setupResult.isSuccess) {
                AdvancedOperationLog.output(context, "اكتمل تشغيل الوضع المتقدم بنجاح.")
                running = true
                starting = false
                startLocation()
            } else {
                AdvancedOperationLog.error(
                    context,
                    "فشل تشغيل الوضع المتقدم: ${setupResult.exceptionOrNull()?.message ?: "سبب غير معروف"}"
                )
                val restoreResult = withContext(Dispatchers.IO) {
                    if (AdvancedSystemProxy.hasPendingRestore(context)) {
                        AdvancedSystemProxy.restore(
                            context,
                            "127.0.0.1:${ProxyLocalService.localPort(context)}"
                        )
                    } else {
                        Result.success(Unit)
                    }
                }
                if (restoreResult.isSuccess) {
                    AdvancedOperationLog.info(context, "اكتملت معالجة الاستعادة؛ إيقاف الخدمة المحلية.")
                    ProxyLocalService.stop(context)
                    running = false
                } else {
                    // Keep the loopback listener alive if Android may still
                    // point at it; stopping it could strand all device traffic.
                    running = ProxyLocalService.isRunning(context)
                }
                starting = false
                val cause = setupResult.exceptionOrNull()
                error = "تعذر تشغيل الوضع المتقدم: ${cause?.message ?: "تحقق من اتصال ADB"}"
                if (restoreResult.isFailure) {
                    AdvancedOperationLog.error(context, "فشلت الاستعادة؛ أُبقيت الخدمة المحلية لتفادي انقطاع المرور.")
                    error += " تعذر استعادة إعدادات النظام تلقائيًا؛ لم نوقف الخدمة حتى لا ينقطع الاتصال."
                }
            }
        }
    }

    fun requestStartAdvanced() {
        if (wirelessState != WirelessDebuggingManager.DebuggingState.READY) {
            AdvancedOperationLog.error(context, "رُفض بدء الوضع المتقدم: اتصال Wireless ADB غير جاهز.")
            error = "أكمل اقتران التصحيح اللاسلكي أولاً."
            return
        }
        launchAdvancedTunnel()
    }

    fun stopAdvanced() {
        if (stopping) return
        stopping = true
        AdvancedOperationLog.info(context, "========== طلب إيقاف البروكسي المتقدم ==========")
        val expectedProxy = "127.0.0.1:${ProxyLocalService.localPort(context)}"
        coroutineScope.launch(Dispatchers.IO) {
            val restoreResult = AdvancedSystemProxy.restore(context, expectedProxy)
            withContext(Dispatchers.Main) {
                stopping = false
                if (restoreResult.isSuccess) {
                    AdvancedOperationLog.output(context, "أُعيد Android إلى الاتصال المباشر ثم أُوقفت خدمة البروكسي المحلي.")
                    locationController.stop()
                    ProxyLocalService.stop(context)
                    running = false
                } else {
                    AdvancedOperationLog.error(context, "تعذر إيقاف آمن: فشلت استعادة إعدادات النظام.")
                    error = "تعذر استعادة إعدادات البروكسي السابقة؛ أبقينا الخدمة نشطة لحماية الاتصال. " +
                        (restoreResult.exceptionOrNull()?.message ?: "أعد المحاولة بعد التحقق من ADB.")
                }
            }
        }
    }

    // ── Recommended mode: one-tap VpnService tunnel ─────────────────────────
    fun launchVpnTunnel() {
        error = null
        starting = true
        ProxyVpnService.clearLastError(context)
        ensureNotificationPermission()
        runCatching {
            ProxyVpnService.start(context, protocol, host, port, username, password)
            startLocation()
        }.onFailure { starting = false; error = it.message ?: "تعذر تشغيل خدمة VPN." }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) launchVpnTunnel()
        else { starting = false; error = "لم يتم منح إذن VPN." }
    }

    fun requestStartVpn() {
        // Don't flip `starting` yet: VpnService.prepare() may show an async,
        // user-driven system dialog first. `starting` (and its 1.5s polling
        // window below) only starts once launchVpnTunnel() actually runs.
        val consent = VpnService.prepare(context)
        if (consent != null) vpnPermissionLauncher.launch(consent) else launchVpnTunnel()
    }

    fun stopVpn() {
        locationController.stop()
        ProxyVpnService.stop(context)
        running = false
    }

    fun requestStart() {
        val parsedPort = port.toIntOrNull()
        if (host.isBlank() || parsedPort !in 1..65535 || (auth && username.isBlank())) {
            error = "أدخل المضيف والمنفذ وبيانات المصادقة بشكل صحيح."
            return
        }
        if (auth && password.isBlank()) {
            error = "أدخل كلمة مرور البروكسي أو أوقف خيار المصادقة."
            return
        }
        error = null
        checkingEndpoint = true
        AdvancedOperationLog.info(context, "التحقق من إمكانية الوصول إلى خادم البروكسي قبل توجيه اتصال الجهاز (مهلة 5 ثوانٍ).")
        coroutineScope.launch {
            val probe = withContext(Dispatchers.IO) { ProxyEndpointProbe.check(host, parsedPort!!) }
            if (probe.isFailure) {
                checkingEndpoint = false
                AdvancedOperationLog.error(context, "تعذر الوصول إلى منفذ خادم البروكسي؛ لم نغيّر إعدادات الشبكة في Android.")
                error = "تعذر الوصول إلى خادم البروكسي. افحص عنوان الخادم والمنفذ واتصال الشبكة، ثم حاول مجددًا."
                return@launch
            }
            checkingEndpoint = false
            if (mode == "vpn") requestStartVpn() else requestStartAdvanced()
        }
    }

    fun stopProxy() {
        if (mode == "vpn") stopVpn() else stopAdvanced()
    }

    LaunchedEffect(starting, mode) {
        if (starting && mode == "vpn") {
            delay(1500)
            running = ProxyVpnService.isRunning(context)
            if (!running) {
                error = ProxyVpnService.lastError(context)
                    ?: "لم يبدأ الاتصال. تحقق من بيانات البروكسي وحاول مرة أخرى."
            }
            starting = false
        }
    }

    Box(Modifier.fillMaxSize().padding(padding)) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 142.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Status hero ────────────────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(27.dp),
                colors = CardDefaults.cardColors(containerColor = KunPalette.PrimaryDeep),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(KunPalette.PrimaryDeep, KunPalette.Primary, KunPalette.Cyan)))
                        .padding(21.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("حالة الاتصال", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.82f))
                            Text(
                                when { running -> "البروكسي نشط"; starting || checkingEndpoint -> "جارٍ الاتصال…"; else -> "جاهز للاتصال" },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                        }
                        FeaturePill(
                            if (running) "متصل" else if (starting || checkingEndpoint) "قيد الاتصال" else "غير متصل",
                            color = Color.White,
                            container = Color.White.copy(alpha = 0.16f),
                        )
                    }
                    Text(
                        when {
                            mode == "vpn" && running -> "يتم توجيه حركة مرور الجهاز عبر نفق VPN."
                            mode == "vpn" -> "اتصال مباشر بنقرة واحدة مع نافذة إذن Android القياسية."
                            running -> "إعداد بروكسي النظام نشط للتطبيقات المتوافقة؛ قد تتجاوزه بعض التطبيقات."
                            else -> "الوضع المتقدم يستخدم ADB المضمّن ولا يعرض أيقونة VPN."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                }
            }
        }

        // ── Connection mode ────────────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("طريقة الاتصال", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ModeOptionCard(
                        title = "VPN",
                        subtitle = "إعداد سريع ومُوصى به",
                        selected = mode == "vpn",
                        enabled = !running && !starting && !checkingEndpoint,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = "vpn"; error = null },
                    )
                    ModeOptionCard(
                        title = "متقدم",
                        subtitle = "بروكسي النظام عبر ADB",
                        selected = mode == "advanced",
                        enabled = !running && !starting && !checkingEndpoint,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = "advanced"; error = null },
                    )
                }
                Text(
                    if (mode == "vpn")
                        "اضغط اتصال ووافق على نافذة Android الوحيدة — من دون إعدادات مطوّر أو تطبيقات إضافية."
                    else
                        "يتطلب إعداد التصحيح اللاسلكي مرة واحدة، ويعمل مع التطبيقات التي تحترم بروكسي النظام.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Saved proxy profiles: available in both connection modes ──────────
        item {
            SavedProxyManagerCard(
                profiles = savedProxies,
                selectedId = selectedSavedProxyId,
                enabled = !running && !starting && !checkingEndpoint && !stopping,
                onSelect = { selectProxyProfile(it) },
                onSave = { name, id -> saveProxyProfile(name, id) },
                onDelete = { deleteProxyProfile(it) },
            )
        }

        // ── Connection details ────────────────────────────────────────────────
        item {
            KUNCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text("بيانات البروكسي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("اختر بروتوكول الاتصال وأدخل بيانات الخادم.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(protocol == "socks5", { protocol = "socks5" }, label = { Text("SOCKS5") }, enabled = !running && !starting && !checkingEndpoint)
                        FilterChip(protocol == "http", { protocol = "http" }, label = { Text("HTTP") }, enabled = !running && !starting && !checkingEndpoint)
                    }
                    OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), label = { Text("مضيف البروكسي أو عنوان IP") }, singleLine = true, enabled = !running && !starting && !checkingEndpoint, shape = RoundedCornerShape(15.dp))
                    OutlinedTextField(port, { port = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("المنفذ") }, singleLine = true, enabled = !running && !starting && !checkingEndpoint, shape = RoundedCornerShape(15.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(auth, { auth = it }, enabled = !running && !starting && !checkingEndpoint)
                        Text("البروكسي يتطلب مصادقة", style = MaterialTheme.typography.bodyMedium)
                    }
                    AnimatedVisibility(visible = auth) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("اسم المستخدم") }, singleLine = true, enabled = !running && !starting && !checkingEndpoint, shape = RoundedCornerShape(15.dp))
                            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("كلمة المرور") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !running && !starting && !checkingEndpoint, shape = RoundedCornerShape(15.dp))
                        }
                    }
                }
            }
        }

        // ── Advanced proxy setup (embedded Wireless ADB)
        if (mode == "advanced") item {
            var pairingCode by remember { mutableStateOf("") }
            var pairing by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            SetupStepCard(
                stepNumber = 2,
                title = "الوضع المتقدم: بروكسي النظام",
                subtitle = "ADB مضمّن؛ يعمل مع التطبيقات التي تحترم بروكسي النظام، وقد تتجاوزه بعض التطبيقات.",
                isComplete = wirelessState == WirelessDebuggingManager.DebuggingState.READY,
                statusText = when (wirelessState) {
                    WirelessDebuggingManager.DebuggingState.UNSUPPORTED_ANDROID_VERSION -> "Android 11 أو أحدث مطلوب"
                    WirelessDebuggingManager.DebuggingState.DEVELOPER_DISABLED -> "وضع المطور معطل"
                    WirelessDebuggingManager.DebuggingState.WIRELESS_DISABLED -> "التصحيح اللاسلكي معطل"
                    WirelessDebuggingManager.DebuggingState.NOT_PAIRED -> "بانتظار رمز الاقتران"
                    WirelessDebuggingManager.DebuggingState.PAIRED_NOT_CONNECTED -> "جارٍ الاتصال"
                    WirelessDebuggingManager.DebuggingState.READY -> "جاهز"
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (wirelessState) {
                        WirelessDebuggingManager.DebuggingState.UNSUPPORTED_ANDROID_VERSION -> {
                            Text(
                                "يتطلب الوضع المتقدم Android 11 أو أحدث لدعم التصحيح اللاسلكي المضمّن. استخدم وضع VPN بلمسة واحدة على هذا الجهاز.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        WirelessDebuggingManager.DebuggingState.DEVELOPER_DISABLED -> {
                            Text("الخطوة 1: فعّل خيارات المطور من إعدادات الهاتف.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Button(onClick = { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }, Modifier.fillMaxWidth()) { Text("فتح الإعدادات") }
                        }
                        WirelessDebuggingManager.DebuggingState.WIRELESS_DISABLED -> {
                            Text("الخطوة 2: فعّل التصحيح اللاسلكي من خيارات المطور.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Button(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }, Modifier.fillMaxWidth()) { Text("فتح إعدادات المطور") }
                        }
                        WirelessDebuggingManager.DebuggingState.NOT_PAIRED,
                        WirelessDebuggingManager.DebuggingState.PAIRED_NOT_CONNECTED -> {
                            Text(
                                if (wirelessState == WirelessDebuggingManager.DebuggingState.PAIRED_NOT_CONNECTED)
                                    "الاقتران محفوظ لكن اتصال ADB انقطع. جرّب إعادة الاتصال، أو أعد الاقتران إذا ألغيت الجهاز من إعدادات Android."
                                else
                                    "الخطوة 3: افتح Wireless debugging ثم Pair device with pairing code. التطبيق يكتشف IP والمنفذ تلقائيًا عبر الشبكة.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedTextField(
                                value = pairingCode,
                                onValueChange = { value -> pairingCode = value.filter { it in '0'..'9' }.take(6) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("رمز الاقتران (6 أرقام فقط)") },
                                singleLine = true,
                                enabled = !pairing,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Button(
                                onClick = {
                                    pairing = true
                                    error = null
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) { WirelessDebuggingManager.pair(context, pairingCode) }
                                        pairing = false
                                        result.onSuccess {
                                            pairingCode = ""
                                            wirelessState = WirelessDebuggingManager.DebuggingState.READY
                                            error = null
                                        }.onFailure {
                                            error = it.message ?: "فشل الاقتران. تأكد من بقاء شاشة الاقتران مفتوحة."
                                            wirelessState = withContext(Dispatchers.IO) {
                                                WirelessDebuggingManager.checkState(context)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = pairingCode.length == 6 && !pairing
                            ) {
                                if (pairing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("اقتران واتصال تلقائي")
                            }
                            if (wirelessState == WirelessDebuggingManager.DebuggingState.PAIRED_NOT_CONNECTED) {
                                OutlinedButton(
                                    onClick = {
                                        pairing = true
                                        error = null
                                        scope.launch {
                                            val connected = withContext(Dispatchers.IO) {
                                                WirelessDebuggingManager.connect(context, 0)
                                            }
                                            pairing = false
                                            if (connected) {
                                                wirelessState = WirelessDebuggingManager.DebuggingState.READY
                                            } else {
                                                error = "تعذرت إعادة اتصال ADB. تأكد من تفعيل التصحيح اللاسلكي واتصال الهاتف بالشبكة نفسها."
                                                wirelessState = withContext(Dispatchers.IO) {
                                                    WirelessDebuggingManager.checkState(context)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !pairing
                                ) { Text(if (pairing) "جارٍ إعادة الاتصال…" else "إعادة الاتصال عبر ADB") }
                            }
                            OutlinedButton(
                                onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !pairing
                            ) { Text("فتح إعدادات المطور") }
                            Text("لا تدخل IP أو أي منفذ؛ سيتم اكتشافهما تلقائيًا.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        WirelessDebuggingManager.DebuggingState.READY -> {
                            Text("تم الاقتران والاتصال عبر ADB المضمّن بنجاح.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = SuccessGreen)
                            Text("يمكنك الآن تشغيل الوضع المتقدم.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }



        // ── Mock location ────────────────────────────────────────────────────
        item {
            KUNCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("موقع وهمي اختياري", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("يتطلب Android اختيار هذا التطبيق من خيارات المطوّر.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(mockLocation, { mockLocation = it }, enabled = !running && !starting && !checkingEndpoint)
                        Text("تفعيل الموقع الوهمي")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(locationMode == "auto", { locationMode = "auto" }, label = { Text("تلقائي") })
                        FilterChip(locationMode == "manual", { locationMode = "manual" }, label = { Text("يدوي") })
                    }
                    if (locationMode == "manual") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(latitude, { latitude = it }, Modifier.weight(1f), label = { Text("خط العرض") }, singleLine = true)
                            OutlinedTextField(longitude, { longitude = it }, Modifier.weight(1f), label = { Text("خط الطول") }, singleLine = true)
                        }
                    }
                    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }, Modifier.fillMaxWidth()) { Text("فتح خيارات المطوّر") }
                }
            }
        }

        if (mode == "advanced" && !running && !starting &&
            AdvancedSystemProxy.hasPendingRestore(context)
        ) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "توجد جلسة متقدمة سابقة لم تكتمل استعادتها. أعد اتصال ADB ثم استعد الإعداد السابق قبل بدء جلسة جديدة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = ::stopAdvanced,
                    enabled = !stopping,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (stopping) "جارٍ الاستعادة…" else "استعادة بروكسي النظام السابق") }
            }
        }

    }

    val hasDetails = host.isNotBlank() && port.isNotBlank()
    val canConnect = hasDetails && (mode == "vpn" || wirelessState == WirelessDebuggingManager.DebuggingState.READY)
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 12.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(visible = error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(13.dp)) {
                    Text(error.orEmpty(), Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                }
            }
            Button(
                onClick = { if (running) stopProxy() else requestStart() },
                enabled = if (running) !stopping else !starting && !checkingEndpoint && !stopping && canConnect,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp),
                colors = if (running) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
            ) {
                if (starting || checkingEndpoint || stopping) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(
                        when { running && mode == "vpn" -> "قطع اتصال VPN"; running -> "إيقاف البروكسي"; mode == "vpn" -> "اتصال"; else -> "بدء بروكسي الجهاز" },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(if (running) SuccessGreen else if (starting || checkingEndpoint || stopping) KunPalette.Warning else KunPalette.Muted.copy(alpha = 0.55f), CircleShape))
                Text(
                    when { running -> "متصل"; checkingEndpoint -> "جارٍ فحص إمكانية الوصول إلى الخادم…"; starting -> if (mode == "vpn") "بانتظار إذن Android…" else "جارٍ تشغيل البروكسي المحلي…"; stopping -> "جارٍ استعادة الاتصال المباشر…"; else -> "غير متصل" },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (running) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    }
}

@Composable private fun SetupStepCard(
    stepNumber: Int,
    title: String,
    subtitle: String,
    isComplete: Boolean,
    statusText: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isComplete) KunPalette.Success.copy(alpha = 0.35f) else KunPalette.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(34.dp).background(if (isComplete) SuccessGreen else MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (isComplete) "✓" else stepNumber.toString(), color = if (isComplete) Color.White else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FeaturePill(
                    statusText,
                    color = if (isComplete) KunPalette.Success else KunPalette.Primary,
                    container = if (isComplete) KunPalette.SuccessSoft else MaterialTheme.colorScheme.primaryContainer,
                )
            }
            AnimatedVisibility(visible = !isComplete, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column { content() }
            }
        }
    }
}

@Composable private fun ModeOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(19.dp)
    Card(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else KunPalette.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
            }
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SavedProxyManagerCard(
    profiles: List<SavedProxy>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (SavedProxy) -> Unit,
    onSave: (String, String?) -> Unit,
    onDelete: (SavedProxy) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var deleteConfirmationOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var profileName by remember { mutableStateOf("") }
    val selected = profiles.firstOrNull { it.id == selectedId }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, KunPalette.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("بروكسياتي المحفوظة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("احفظ اتصالك مرة واحدة واستخدمه في وضعي VPN والمتقدم.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    OutlinedButton(onClick = { menuExpanded = true }, enabled = enabled && profiles.isNotEmpty()) {
                        Text(if (selected != null) "اختيار بروكسي" else "اختر بروكسي")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(profile.name, fontWeight = if (profile.id == selectedId) FontWeight.Bold else FontWeight.Medium)
                                        Text("${profile.protocol.uppercase()} • ${profile.endpoint}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = { menuExpanded = false; onSelect(profile) },
                                enabled = enabled,
                            )
                        }
                    }
                }
            }
            if (selected != null) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(selected.name, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                            Text("${selected.protocol.uppercase()} • ${selected.endpoint}", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall)
                            Text(if (selected.authenticationRequired) "بيانات المصادقة محفوظة ومشفّرة" else "من دون مصادقة", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { editingId = selected.id; profileName = selected.name; editorOpen = true }, enabled = enabled) { Text("تعديل") }
                        TextButton(onClick = { deleteConfirmationOpen = true }, enabled = enabled) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                    }
                }
            } else {
                Text("ما أضفت بروكسي بعد. احفظ بيانات اتصال جديدة لتظهر هنا وتبقى بعد إغلاق التطبيق.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editingId = null; profileName = ""; editorOpen = true }, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text("إضافة بروكسي")
                }
                if (selected != null) {
                    OutlinedButton(onClick = { editingId = selected.id; profileName = selected.name; editorOpen = true }, enabled = enabled, modifier = Modifier.weight(1f)) {
                        Text("حفظ التغييرات")
                    }
                }
            }
            Text("تُخزّن ملفات البروكسي على هذا الجهاز، وتُشفّر كلمات المرور بمفتاح Android الآمن.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (editorOpen) {
        AlertDialog(
            onDismissRequest = { editorOpen = false },
            title = { Text(if (editingId == null) "إضافة بروكسي جديد" else "تعديل البروكسي المحفوظ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيُحفَظ عنوان الاتصال والبروتوكول وبيانات المصادقة الحالية في قاعدة البيانات المشفّرة على جهازك.", style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it.take(64) },
                        label = { Text("اسم يميّز هذا البروكسي") },
                        singleLine = true,
                        enabled = enabled,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onSave(profileName.trim(), editingId); editorOpen = false }, enabled = enabled && profileName.isNotBlank()) {
                    Text("حفظ البروكسي")
                }
            },
            dismissButton = { TextButton(onClick = { editorOpen = false }) { Text("إلغاء") } },
        )
    }

    if (deleteConfirmationOpen && selected != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmationOpen = false },
            title = { Text("حذف البروكسي المحفوظ؟") },
            text = { Text("سيُحذف «${selected.name}» وبيانات اعتماده المشفّرة من قاعدة البيانات على هذا الجهاز.") },
            confirmButton = {
                Button(onClick = { onDelete(selected); deleteConfirmationOpen = false }, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("حذف")
                }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmationOpen = false }) { Text("إلغاء") } },
        )
    }
}

private fun startLocalProxyService(context: Context, protocol: String, host: String, port: String, username: String, password: String) {
    val intent = Intent(context, ProxyLocalService::class.java).apply {
        action = ProxyLocalService.ACTION_START
        putExtra(ProxyLocalService.EXTRA_PROTOCOL, protocol)
        putExtra(ProxyLocalService.EXTRA_HOST, host.trim())
        putExtra(ProxyLocalService.EXTRA_PORT, port.toInt())
        putExtra(ProxyLocalService.EXTRA_USERNAME, username)
        putExtra(ProxyLocalService.EXTRA_PASSWORD, password)
        putExtra(ProxyLocalService.EXTRA_LOCAL_PORT, ProxyLocalService.DEFAULT_LOCAL_PORT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
}
