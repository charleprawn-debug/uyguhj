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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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

private data class Product(val id: String, val name: String, val description: String, val protocol: String, val country: String, val city: String, val ipType: String, val daily: String, val monthly: String, val featured: Boolean)
private data class Profile(val email: String, val name: String, val role: String, val verified: Boolean)
private data class Subscription(val status: String, val expires: String, val product: String, val protocol: String)
private enum class Screen { MARKET, SUBSCRIPTIONS, PROXY, PROFILE }

private val ProxyColors = lightColorScheme(
    primary = Color(0xFF2563EB), onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF1FF), onPrimaryContainer = Color(0xFF153B8A),
    secondary = Color(0xFF0F8B78), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F5F0), onSecondaryContainer = Color(0xFF075E53),
    tertiary = Color(0xFFB66B08), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF1D8), onTertiaryContainer = Color(0xFF744200),
    background = Color(0xFFF6F8FC), onBackground = Color(0xFF18202F),
    surface = Color.White, onSurface = Color(0xFF18202F),
    surfaceVariant = Color(0xFFF0F3F8), onSurfaceVariant = Color(0xFF616B7C),
    error = Color(0xFFCE3D43), onError = Color.White,
    errorContainer = Color(0xFFFFE9E8), onErrorContainer = Color(0xFF8F1D24),
    outline = Color(0xFFD8DEE8), outlineVariant = Color(0xFFE8ECF2),
)

private val SuccessGreen = Color(0xFF15866F)
private val SuccessGreenContainer = Color(0xFFE4F5EF)
private val OnSuccessGreenContainer = Color(0xFF14624F)

@Composable private fun ProxyTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = ProxyColors, content = content) }

private class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("proxy_session", Context.MODE_PRIVATE)
    var accessToken: String? get() = prefs.getString("access_token", null); set(value) { prefs.edit().putString("access_token", value).apply() }
    var refreshToken: String? get() = prefs.getString("refresh_token", null); set(value) { prefs.edit().putString("refresh_token", value).apply() }
    fun clear() = prefs.edit().clear().apply()
}

private class ApiClient(context: Context) {
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
    fun products(): List<Product> { val a = request("/products").getJSONArray("data"); return (0 until a.length()).map { val x = a.getJSONObject(it); Product(x.getString("id"), x.getString("name"), x.optString("description"), x.optString("protocol").uppercase(), x.optString("country_name") + " (" + x.optString("country_code") + ")", x.optString("city"), x.optString("ip_type"), x.optDouble("price_daily").toString() + " دولار/يوم", x.optDouble("price_monthly").toString() + " دولار/شهر", x.optBoolean("is_featured")) } }
    fun profile(): Profile { val x = request("/me").getJSONObject("data"); return Profile(x.optString("email"), x.optString("full_name", "بدون اسم"), x.optString("role", "user"), x.optBoolean("is_email_verified")) }
    fun subscriptions(): List<Subscription> { val a = request("/me/subscriptions").getJSONArray("data"); return (0 until a.length()).map { val x = a.getJSONObject(it); val p = x.optJSONObject("proxy_products"); Subscription(x.optString("status"), x.optString("expires_at"), p?.optString("name", "البروكسي") ?: "البروكسي", p?.optString("protocol", "") ?: "") } }
    fun loggedIn() = store.accessToken != null
    fun logout() = store.clear()
}

private class AppViewModel(private val api: ApiClient) : ViewModel() {
    var loggedIn by mutableStateOf(api.loggedIn()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var products by mutableStateOf<List<Product>>(emptyList()); private set
    var profile by mutableStateOf<Profile?>(null); private set
    var subscriptions by mutableStateOf<List<Subscription>>(emptyList()); private set
    fun login(email: String, password: String) { loading = true; error = null; viewModelScope.launch(Dispatchers.IO) { try { api.login(email, password); withContext(Dispatchers.Main) { loggedIn = true; loading = false; loadProducts() } } catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message ?: "تعذر تسجيل الدخول"; loading = false } } } }
    fun register(email: String, password: String, name: String, done: () -> Unit) { loading = true; error = null; viewModelScope.launch(Dispatchers.IO) { try { api.register(email, password, name); withContext(Dispatchers.Main) { loading = false; error = "تم إنشاء الحساب. سجّل الدخول للمتابعة."; done() } } catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message ?: "تعذر إنشاء الحساب"; loading = false } } } }
    fun loadProducts() = viewModelScope.launch(Dispatchers.IO) { try { val value = api.products(); withContext(Dispatchers.Main) { products = value } } catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message ?: "تعذر تحميل الخطط" } } }
    fun loadProfile() = viewModelScope.launch(Dispatchers.IO) { try { val value = api.profile(); withContext(Dispatchers.Main) { profile = value } } catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message ?: "تعذر تحميل الملف الشخصي" } } }
    fun loadSubscriptions() = viewModelScope.launch(Dispatchers.IO) { try { val value = api.subscriptions(); withContext(Dispatchers.Main) { subscriptions = value } } catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message ?: "تعذر تحميل الاشتراكات" } } }
    fun logout() { api.logout(); loggedIn = false; products = emptyList(); profile = null; subscriptions = emptyList(); error = null }
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
@Composable private fun BrandMark() { Box(Modifier.size(56.dp).background(Brush.linearGradient(listOf(Color(0xFF4A9EFF), Color(0xFF42D5C1))), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("P", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Black) } }

@Composable private fun AuthScreen(vm: AppViewModel) {
    var register by remember { mutableStateOf(false) }; var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) { BrandMark(); Spacer(Modifier.height(20.dp)); Text("منصة البروكسي", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(if (register) "أنشئ حسابًا آمنًا وأدر اتصالات البروكسي الخاصة بك." else "طريقة أسرع وأسهل لتوجيه اتصالك.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(24.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { TextButton(onClick = { register = false; vm.clearError() }) { Text("تسجيل الدخول", fontWeight = if (!register) FontWeight.Bold else FontWeight.Normal) }; TextButton(onClick = { register = true; vm.clearError() }) { Text("إنشاء حساب", fontWeight = if (register) FontWeight.Bold else FontWeight.Normal) } }; Spacer(Modifier.height(12.dp)); if (register) { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("الاسم الكامل") }, singleLine = true); Spacer(Modifier.height(10.dp)) }; OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("البريد الإلكتروني") }, singleLine = true); Spacer(Modifier.height(10.dp)); OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("كلمة المرور") }, visualTransformation = PasswordVisualTransformation(), singleLine = true); vm.error?.let { Text(it, Modifier.padding(top = 12.dp), color = if (it.startsWith("تم إنشاء الحساب")) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error) }; Spacer(Modifier.height(18.dp)); Button(onClick = { if (register) vm.register(email, password, name) { register = false } else vm.login(email, password) }, enabled = !vm.loading && email.isNotBlank() && password.length >= 8 && (!register || name.isNotBlank()), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp)) { if (vm.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (register) "إنشاء حساب" else "متابعة", fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(16.dp)); Text("تُستخدم بيانات اعتمادك فقط للمصادقة مع الخدمة.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable private fun MainShell(vm: AppViewModel) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.MARKET) }; var selectedProduct by remember { mutableStateOf<Product?>(null) }; if (selectedProduct != null) { ProductDetails(selectedProduct!!, { selectedProduct = null }); return }; LaunchedEffect(screen) { AdvancedOperationLog.info(context, "App navigation: ${screen.name}."); vm.clearError(); when (screen) { Screen.MARKET -> vm.loadProducts(); Screen.PROFILE -> vm.loadProfile(); Screen.SUBSCRIPTIONS -> vm.loadSubscriptions(); Screen.PROXY -> Unit } }
    val title = when (screen) { Screen.MARKET -> "السوق"; Screen.SUBSCRIPTIONS -> "اشتراكاتي"; Screen.PROXY -> "بروكسي الجهاز"; Screen.PROFILE -> "ملفي الشخصي" }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopAppBar(title = { Text(title, fontWeight = FontWeight.Bold) }) }, bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) { NavigationBarItem(screen == Screen.MARKET, { screen = Screen.MARKET }, icon = { Text("⌂") }, label = { Text("السوق") }); NavigationBarItem(screen == Screen.SUBSCRIPTIONS, { screen = Screen.SUBSCRIPTIONS }, icon = { Text("▣") }, label = { Text("الخطط") }); NavigationBarItem(screen == Screen.PROXY, { screen = Screen.PROXY }, icon = { Text("⚡") }, label = { Text("البروكسي") }); NavigationBarItem(screen == Screen.PROFILE, { screen = Screen.PROFILE }, icon = { Text("●") }, label = { Text("الملف الشخصي") }) } }) { padding -> when (screen) { Screen.MARKET -> Marketplace(vm, padding) { selectedProduct = it }; Screen.SUBSCRIPTIONS -> Subscriptions(vm, padding); Screen.PROXY -> ProxyScreen(padding); Screen.PROFILE -> ProfileScreen(vm, padding) } }
}

// ─────────────────────────────────────────────────────────────────────────────
// Proxy Screen — local proxy + embedded Wireless ADB flow
// ─────────────────────────────────────────────────────────────────────────────

@Composable private fun ProxyScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
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
    var operationLog by remember { mutableStateOf(emptyList<String>()) }
    var logCopied by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf<String?>(null) }

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

    val exportLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { destination ->
        if (destination == null) {
            exportStatus = "أُلغي اختيار مكان الحفظ."
        } else {
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    AdvancedOperationLog.export(context, destination)
                }
                exportStatus = result.fold(
                    onSuccess = { "تم حفظ السجل الكامل ($it بايت)." },
                    onFailure = { "تعذر حفظ السجل: ${it.message ?: "خطأ غير معروف"}" }
                )
            }
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
        operationLog = withContext(Dispatchers.IO) { AdvancedOperationLog.readLines(context) }
        while (true) {
            if (mode == "advanced") {
                val newWirelessState = withContext(Dispatchers.IO) {
                    WirelessDebuggingManager.checkState(context)
                }
                if (newWirelessState != wirelessState) {
                    AdvancedOperationLog.info(context, "حالة Wireless ADB: ${newWirelessState.name}.")
                    wirelessState = newWirelessState
                }
            }
            val newLog = withContext(Dispatchers.IO) { AdvancedOperationLog.readLines(context) }
            if (newLog != operationLog) {
                operationLog = newLog
                logCopied = false
            }
            delay(1_000)
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

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Status card ──────────────────────────────────────────────────────
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (running) SuccessGreenContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        if (running) "البروكسي نشط" else if (starting) "جارٍ الاتصال…" else "جاهز للاتصال",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (running) OnSuccessGreenContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        when {
                            mode == "vpn" && running -> "يتم توجيه حركة مرور الجهاز عبر نفق VPN."
                            mode == "vpn" -> "وضع اللمسة الواحدة — يظهر مربع إذن VPN القياسي في Android فقط."
                            running -> "إعداد بروكسي النظام نشط للتطبيقات المتوافقة؛ قد تتجاوزه بعض التطبيقات."
                            else -> "الوضع المتقدم — لا تظهر أيقونة مفتاح VPN، ويستخدم ADB المضمّن داخل التطبيق."
                        },
                        color = if (running) OnSuccessGreenContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Mode selector ────────────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("وضع الاتصال", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == "vpn",
                        onClick = { if (!running && !starting && !checkingEndpoint) { mode = "vpn"; error = null } },
                        label = { Text("VPN بلمسة واحدة (موصى به)") },
                        enabled = !running && !starting && !checkingEndpoint
                    )
                    FilterChip(
                        selected = mode == "advanced",
                        onClick = { if (!running && !starting && !checkingEndpoint) { mode = "advanced"; error = null } },
                        label = { Text("متقدم: بدون أيقونة VPN") },
                        enabled = !running && !starting && !checkingEndpoint
                    )
                }
                Text(
                    if (mode == "vpn")
                        "اضغط «اتصال» ووافق على مربع حوار نظام Android الوحيد — دون خيارات مطوّر أو تطبيقات إضافية."
                    else
                        "يبقي الاتصال خارج مؤشر VPN في Android، مع إعداد التصحيح اللاسلكي مرة واحدة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

        // ── Connection details ───────────────────────────────────────────────
        item { Text("تفاصيل الاتصال", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(protocol == "socks5", { protocol = "socks5" }, label = { Text("SOCKS5") }, enabled = !running && !starting && !checkingEndpoint)
                FilterChip(protocol == "http", { protocol = "http" }, label = { Text("HTTP") }, enabled = !running && !starting && !checkingEndpoint)
            }
        }
        item { OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), label = { Text("مضيف البروكسي أو عنوان IP") }, singleLine = true, enabled = !running && !starting && !checkingEndpoint) }
        item { OutlinedTextField(port, { port = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("المنفذ") }, singleLine = true, enabled = !running && !starting && !checkingEndpoint) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(auth, { auth = it }, enabled = !running && !starting && !checkingEndpoint)
                Text("البروكسي يتطلب مصادقة")
            }
        }
        if (auth) {
            item { OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("اسم المستخدم") }, singleLine = true, enabled = !running && !starting && !checkingEndpoint) }
            item { OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("كلمة المرور") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !running && !starting && !checkingEndpoint) }
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

        item {
            AdvancedOperationLogPanel(
                lines = operationLog,
                copied = logCopied,
                exportStatus = exportStatus,
                exportPath = AdvancedOperationLog.adbPullPath(context),
                onCopy = {
                    coroutineScope.launch {
                        val completeLog = withContext(Dispatchers.IO) {
                            AdvancedOperationLog.readLines(context)
                        }
                        clipboardManager.setText(AnnotatedString(completeLog.joinToString("\n")))
                        operationLog = completeLog
                        logCopied = true
                    }
                },
                onExport = {
                    AdvancedOperationLog.info(context, "طلب المستخدم حفظ نسخة من السجل الكامل عبر منتقي الملفات.")
                    exportStatus = "اختر مكان الحفظ…"
                    exportLogLauncher.launch("advanced-operation-${System.currentTimeMillis()}.log")
                }
            )
        }

        // ── Mock location ────────────────────────────────────────────────────
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

        // ── Error display ────────────────────────────────────────────────────
        item { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }

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

        // ── Connect / Disconnect button ──────────────────────────────────────
        item {
            if (running) {
                Button(
                    onClick = { stopProxy() },
                    enabled = !stopping,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        when {
                            stopping -> "جارٍ استعادة إعدادات البروكسي…"
                            mode == "vpn" -> "قطع اتصال VPN"
                            else -> "إيقاف البروكسي"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                val hasDetails = host.isNotBlank() && port.isNotBlank()
                val canConnect = hasDetails && (mode == "vpn" || wirelessState == WirelessDebuggingManager.DebuggingState.READY)
                Button(
                    onClick = ::requestStart,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !starting && !checkingEndpoint && canConnect,
                    shape = RoundedCornerShape(15.dp)
                ) {
                    if (starting || checkingEndpoint) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(if (mode == "vpn") "اتصال" else "بدء بروكسي الجهاز", fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Status text ──────────────────────────────────────────────────────
        item {
            Text(
                when {
                    running -> "متصل"
                    checkingEndpoint -> "جارٍ فحص إمكانية الوصول إلى خادم البروكسي…"
                    starting -> if (mode == "vpn") "بانتظار منح Android إذن VPN…" else "بانتظار تشغيل البروكسي المحلي…"
                    else -> "غير متصل"
                },
                color = if (running) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isComplete) SuccessGreenContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(28.dp).background(
                        if (isComplete) SuccessGreen else MaterialTheme.colorScheme.primary,
                        CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isComplete) "✓" else stepNumber.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AnimatedVisibility(
                visible = !isComplete,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) { content() }
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

// ─────────────────────────────────────────────────────────────────────────────
// Marketplace, Subscriptions, Profile — unchanged from original
// ─────────────────────────────────────────────────────────────────────────────

@Composable private fun Marketplace(vm: AppViewModel, padding: PaddingValues, onProductClick: (Product) -> Unit) { var featured by remember { mutableStateOf(false) }; val visible = vm.products.filter { !featured || it.featured }; LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text("اعثر على مسارك المثالي", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("خطط بروكسي موثوقة مع مواقع وبروتوكولات وأسعار واضحة.", color = MaterialTheme.colorScheme.onSurfaceVariant); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(vm.products.size.toString() + " خطط متاحة", color = MaterialTheme.colorScheme.secondary); FilterChip(featured, { featured = !featured }, label = { Text("مميز") }) } } } }; if (visible.isEmpty()) item { Text("لا توجد خطط متاحة حاليًا", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }; items(visible) { ProductCard(it, onProductClick) } } }
@Composable private fun ProductCard(p: Product, onClick: (Product) -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); if (p.featured) Text("مميز", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall) }; Text(p.description.ifBlank { "مسار بروكسي موثوق لاتصالك اليومي." }, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(p.country + " • " + p.city); Text(p.protocol + " • " + p.ipType, color = MaterialTheme.colorScheme.secondary); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text(p.daily, style = MaterialTheme.typography.bodySmall); Text(p.monthly, fontWeight = FontWeight.Bold) }; Button(onClick = { onClick(p) }) { Text("عرض التفاصيل") } } } } }
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable private fun ProductDetails(p: Product, onBack: () -> Unit) { Scaffold(topBar = { TopAppBar(title = { Text(p.name) }, navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }) }) { padding -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(p.description, style = MaterialTheme.typography.bodyLarge); Text("الموقع: " + p.country + ", " + p.city); Text("البروتوكول: " + p.protocol); Text("النوع: " + p.ipType); Text("يوميًا: " + p.daily); Text("شهريًا: " + p.monthly); Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("شراء الخطة") } } } }
@Composable private fun Subscriptions(vm: AppViewModel, padding: PaddingValues) { LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("خططك", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }; if (vm.subscriptions.isEmpty()) item { Text("لا توجد اشتراكات نشطة حتى الآن.", color = MaterialTheme.colorScheme.onSurfaceVariant) }; items(vm.subscriptions) { s -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(s.product, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(s.protocol + " • " + s.status); Text("تنتهي في: " + s.expires, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
@Composable private fun ProfileScreen(vm: AppViewModel, padding: PaddingValues) { Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("الحساب", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); vm.profile?.let { p -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(p.email, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("الدور: " + p.role); Text(if (p.verified) "تم التحقق من البريد الإلكتروني" else "بانتظار التحقق من البريد الإلكتروني", color = if (p.verified) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error) } } }; Button(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("تسجيل الخروج") } } }
