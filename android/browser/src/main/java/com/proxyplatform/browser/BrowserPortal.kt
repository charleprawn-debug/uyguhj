package com.proxyplatform.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone

private val Ink = Color(0xFF162238)
private val Muted = Color(0xFF69768A)
private val Canvas = Color(0xFFF4F7FB)
private val Primary = Color(0xFF2367D1)
private val Line = Color(0xFFE4EAF2)

private enum class PortalPage { AUTH, HOME, EDIT }

class BrowserHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    BrowserPortalApp(
                        onOpen = { profile -> openProfile(profile) },
                        onClearProfileData = { profileId ->
                            startActivity(Intent(this, BrowserActivity::class.java).apply {
                                action = ACTION_CLEAR_PROFILE
                                putExtra(EXTRA_PROFILE_ID, profileId)
                            })
                        },
                    )
                }
            }
        }
    }

    private fun openProfile(profile: BrowserProfile) {
        val direct = profile.proxyId == "direct"
        val language = if (profile.languageMode == "custom") profile.language.trim() else profile.detectedLocale.ifBlank { Locale.getDefault().toLanguageTag() }
        val timeZone = if (profile.timeZoneMode == "custom") profile.timeZone.trim() else profile.detectedTimeZone.ifBlank { TimeZone.getDefault().id }
        val locationMode = when {
            direct && profile.locationMode == "auto" -> "off"
            profile.locationMode == "off" -> "off"
            profile.locationMode == "custom" -> "custom"
            profile.detectedLatitude.isNotBlank() && profile.detectedLongitude.isNotBlank() -> "resolved"
            else -> "auto"
        }
        val latitude = if (profile.locationMode == "custom") profile.latitude else profile.detectedLatitude
        val longitude = if (profile.locationMode == "custom") profile.longitude else profile.detectedLongitude
        val intent = Intent(this, BrowserActivity::class.java).apply {
            putExtra("kun.browser.proxy_mode", if (direct) "direct" else "profile")
            putExtra("kun.browser.proxy_protocol", profile.protocol)
            putExtra("kun.browser.proxy_host", profile.host)
            putExtra("kun.browser.local_port", profile.port)
            putExtra("kun.browser.proxy_username", profile.username)
            putExtra("kun.browser.proxy_password", profile.password)
            putExtra("kun.browser.language", language)
            putExtra("kun.browser.time_zone", timeZone)
            putExtra("kun.browser.location_mode", locationMode)
            putExtra("kun.browser.latitude", latitude)
            putExtra("kun.browser.longitude", longitude)
            putExtra("kun.browser.profile_name", profile.name)
            putExtra("kun.browser.profile_id", profile.id)
        }
        startActivity(intent)
    }
}

@Composable
private fun BrowserPortalApp(onOpen: (BrowserProfile) -> Unit, onClearProfileData: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val api = remember { BrowserApi(context) }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(if (api.isSignedIn) PortalPage.HOME else PortalPage.AUTH) }
    var isRegister by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(api.email) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var proxies by remember { mutableStateOf<List<AccountProxy>>(emptyList()) }
    var proxiesLoading by remember { mutableStateOf(false) }
    var profileStore by remember { mutableStateOf<BrowserProfileStore?>(null) }
    val profiles = remember { mutableStateListOf<BrowserProfile>() }
    var editing by remember { mutableStateOf<BrowserProfile?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<BrowserProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<BrowserProfile?>(null) }

    fun loadAccountData() {
        val owner = api.email
        profileStore = BrowserProfileStore(context, owner)
        profiles.clear()
        profiles.addAll(profileStore?.load().orEmpty())
        proxiesLoading = true
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { api.loadProxies() } }
            proxies = result.getOrDefault(emptyList())
            notice = if (result.isFailure) "تعذر تحميل بروكسيات الحساب الآن؛ تستطيع إضافة بروكسي يدويًا." else null
            proxiesLoading = false
        }
    }

    LaunchedEffect(api.isSignedIn, page) {
        if (api.isSignedIn && page == PortalPage.HOME && profileStore == null) loadAccountData()
    }

    Surface(Modifier.fillMaxSize(), color = Canvas) {
        when (page) {
            PortalPage.AUTH -> AuthPortal(
                register = isRegister,
                fullName = fullName,
                email = email,
                password = password,
                busy = busy,
                error = error,
                onName = { fullName = it },
                onEmail = { email = it; error = null },
                onPassword = { password = it; error = null },
                onToggle = { isRegister = it; error = null },
                onSubmit = {
                    if (busy) return@AuthPortal
                    error = null
                    if (email.isBlank() || password.isBlank() || (isRegister && fullName.isBlank())) {
                        error = "أكمل الحقول المطلوبة أولًا."
                    } else if (isRegister && password.length < 8) {
                        error = "كلمة المرور يجب أن تكون 8 أحرف على الأقل."
                    } else {
                        busy = true
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    if (isRegister) api.signUp(fullName, email, password) else api.signIn(email, password)
                                }
                            }
                            busy = false
                            result.onSuccess {
                                error = null
                                page = PortalPage.HOME
                                password = ""
                            }.onFailure { error = it.message ?: "تعذر تسجيل الدخول. تحقق من اتصالك وحاول مجددًا." }
                        }
                    }
                },
            )
            PortalPage.HOME -> BrowserDashboard(
                email = api.email,
                profiles = profiles,
                loadingProxies = proxiesLoading,
                notice = notice,
                onCreate = { error = null; editing = null; showEditor = true; page = PortalPage.EDIT },
                onDetails = { details = it },
                onEdit = { error = null; editing = it; showEditor = true; page = PortalPage.EDIT },
                onDelete = { deleteTarget = it },
                onOpen = onOpen,
                onLogout = {
                    api.signOut()
                    profileStore = null
                    profiles.clear()
                    proxies = emptyList()
                    error = null
                    notice = null
                    page = PortalPage.AUTH
                    isRegister = false
                },
                onReloadProxies = { loadAccountData() },
            )
            PortalPage.EDIT -> BrowserProfileEditor(
                initial = editing,
                accountProxies = proxies,
                proxiesLoading = proxiesLoading,
                saveError = error,
                onCancel = { showEditor = false; page = PortalPage.HOME },
                onSave = { profile ->
                    runCatching { profileStore?.save(profile) ?: throw IllegalStateException("سجّل الدخول أولًا.") }
                        .onSuccess { error = null; profiles.clear(); profiles.addAll(it); showEditor = false; editing = null; page = PortalPage.HOME; notice = "تم حفظ ملف التصفح على هذا الجهاز." }
                        .onFailure { error = it.message ?: "تعذر حفظ الملف." }
                },
            )
        }
    }

    details?.let { profile ->
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text(profile.name, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الاتصال: ${if (profile.proxyId == "direct") "مباشر بدون بروكسي" else "${profile.protocol.uppercase()} • ${profile.host}:${profile.port}"}")
                    Text("اللغة: ${if (profile.languageMode == "auto") if (profile.proxyId == "direct") "تلقائي من الجهاز" else "تلقائي حسب البروكسي" else profile.language}")
                    Text("المنطقة الزمنية: ${if (profile.timeZoneMode == "auto") if (profile.proxyId == "direct") "تلقائي من الجهاز" else "تلقائي حسب البروكسي" else profile.timeZone}")
                    Text("الموقع: ${when (profile.locationMode) { "custom" -> "مخصص (${profile.latitude}, ${profile.longitude})"; "off" -> "غير محدد"; else -> if (profile.proxyId == "direct") "إعداد الجهاز" else "تلقائي حسب عنوان خروج البروكسي" }}")
                    Text("كلمة مرور البروكسي محفوظة مشفّرة على هذا الجهاز.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            },
            confirmButton = { TextButton(onClick = { details = null }) { Text("إغلاق") } },
        )
    }
    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف ملف التصفح؟") },
            text = { Text("سيُحذف الملف وإعداداته المحفوظة من هذا الجهاز. لا يمكن التراجع عن ذلك.") },
            confirmButton = {
                TextButton(onClick = {
                    profiles.clear()
                    profiles.addAll(profileStore?.delete(profile.id).orEmpty())
                    deleteTarget = null
                    onClearProfileData(profile.id)
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun AuthPortal(
    register: Boolean, fullName: String, email: String, password: String, busy: Boolean, error: String?,
    onName: (String) -> Unit, onEmail: (String) -> Unit, onPassword: (String) -> Unit,
    onToggle: (Boolean) -> Unit, onSubmit: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF173969), Color(0xFF286ACB), Canvas)))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 22.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(18.dp))
            Surface(Modifier.size(64.dp), shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = 0.15f)) {
                Box(contentAlignment = Alignment.Center) { Text("K", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold) }
            }
            Spacer(Modifier.height(10.dp))
            Text("KUN Browser", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text("ملفات تصفح مستقلة، بإعدادك", color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(26.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(8.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text(if (register) "إنشاء حساب KUN" else "أهلًا بعودتك", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
                    Text("استخدم حساب KUN نفسه الذي تستعمله في التطبيق الأساسي.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !register, onClick = { onToggle(false) }, label = { Text("تسجيل الدخول") })
                        FilterChip(selected = register, onClick = { onToggle(true) }, label = { Text("حساب جديد") })
                    }
                    if (register) OutlinedTextField(value = fullName, onValueChange = onName, modifier = Modifier.fillMaxWidth(), label = { Text("الاسم الكامل") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(value = email, onValueChange = onEmail, modifier = Modifier.fillMaxWidth(), label = { Text("البريد الإلكتروني") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(value = password, onValueChange = onPassword, modifier = Modifier.fillMaxWidth(), label = { Text("كلمة المرور") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(14.dp))
                    if (register) Text("ثمانية أحرف على الأقل. سيتم إنشاء الحساب على خادم KUN نفسه.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (error != null) Text(error, color = Color(0xFFB3261E), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onSubmit, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text(if (register) "إنشاء الحساب" else "تسجيل الدخول", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("حسابك يتصل بخدمات KUN عبر اتصال مشفّر.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BrowserDashboard(
    email: String, profiles: List<BrowserProfile>, loadingProxies: Boolean, notice: String?,
    onCreate: () -> Unit, onDetails: (BrowserProfile) -> Unit, onEdit: (BrowserProfile) -> Unit,
    onDelete: (BrowserProfile) -> Unit, onOpen: (BrowserProfile) -> Unit, onLogout: () -> Unit, onReloadProxies: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("KUN Browser", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Ink)
                Text(email, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            TextButton(onClick = onLogout) { Text("خروج") }
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF173969))) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ملفات التصفح", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("لكل ملف إعداد اتصال وبيانات تصفح منفصلة.", color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall)
                }
                Surface(Modifier.size(42.dp), shape = CircleShape, color = Color.White.copy(alpha = .14f)) { Box(contentAlignment = Alignment.Center) { Text("${profiles.size}", color = Color.White, fontWeight = FontWeight.Bold) } }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
            Text("إنشاء ملف  +", fontWeight = FontWeight.Bold)
        }
        if (loadingProxies) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("جارٍ تحميل بروكسيات حسابك", color = Muted, style = MaterialTheme.typography.bodySmall) }
        if (notice != null) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(notice, Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onReloadProxies) { Text("تحديث") }
        }
        Spacer(Modifier.height(12.dp))
        if (profiles.isEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ابدأ بإنشاء أول ملف", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("سمّه، اختر بروكسيًا أو اتصالًا مباشرًا، ثم اضبط اللغة والوقت والموقع.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                profiles.forEach { profile ->
                    ProfileCard(profile, onDetails, onEdit, onDelete, onOpen)
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: BrowserProfile, onDetails: (BrowserProfile) -> Unit, onEdit: (BrowserProfile) -> Unit, onDelete: (BrowserProfile) -> Unit, onOpen: (BrowserProfile) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Line), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(40.dp), shape = RoundedCornerShape(13.dp), color = Color(0xFFEAF1FF)) { Box(contentAlignment = Alignment.Center) { Text("K", color = Primary, fontWeight = FontWeight.ExtraBold) } }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.name, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(if (profile.proxyId == "direct") "اتصال مباشر • بدون بروكسي" else "${profile.protocol.uppercase()}  •  ${profile.host}:${profile.port}", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onDetails(profile) }) { Text("التفاصيل") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoPill(if (profile.languageMode == "auto") if (profile.proxyId == "direct") "لغة الجهاز" else "لغة تلقائية" else profile.language)
                InfoPill(if (profile.timeZoneMode == "auto") if (profile.proxyId == "direct") "وقت الجهاز" else "وقت تلقائي" else profile.timeZone)
                InfoPill(when (profile.locationMode) { "custom" -> "موقع مخصص"; "off" -> "الموقع مغلق"; else -> if (profile.proxyId == "direct") "موقع الجهاز" else "موقع تلقائي" })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(onClick = { onOpen(profile) }, modifier = Modifier.weight(1.25f), shape = RoundedCornerShape(13.dp)) { Text("فتح", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = { onEdit(profile) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("تعديل") }
                TextButton(onClick = { onDelete(profile) }) { Text("حذف", color = Color(0xFFB3261E)) }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(shape = RoundedCornerShape(99.dp), color = Color(0xFFF1F4F9)) { Text(text.take(18), Modifier.padding(horizontal = 9.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1) }
}

@Composable
private fun BrowserProfileEditor(initial: BrowserProfile?, accountProxies: List<AccountProxy>, proxiesLoading: Boolean, saveError: String?, onCancel: () -> Unit, onSave: (BrowserProfile) -> Unit) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var proxyId by remember(initial?.id) { mutableStateOf(initial?.proxyId ?: "manual") }
    var protocol by remember(initial?.id) { mutableStateOf(initial?.protocol ?: "socks5") }
    var host by remember(initial?.id) { mutableStateOf(initial?.host.orEmpty()) }
    var port by remember(initial?.id) { mutableStateOf(initial?.port?.takeIf { it > 0 }?.toString().orEmpty()) }
    var username by remember(initial?.id) { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember(initial?.id) { mutableStateOf(initial?.password.orEmpty()) }
    var languageMode by remember(initial?.id) { mutableStateOf(initial?.languageMode ?: "auto") }
    var language by remember(initial?.id) { mutableStateOf(initial?.language.orEmpty()) }
    var timeMode by remember(initial?.id) { mutableStateOf(initial?.timeZoneMode ?: "auto") }
    var timeZone by remember(initial?.id) { mutableStateOf(initial?.timeZone.orEmpty()) }
    var locationMode by remember(initial?.id) {
        mutableStateOf(initial?.let { if (it.proxyId == "direct" && it.locationMode == "auto") "off" else it.locationMode } ?: "auto")
    }
    var latitude by remember(initial?.id) { mutableStateOf(initial?.latitude.orEmpty()) }
    var longitude by remember(initial?.id) { mutableStateOf(initial?.longitude.orEmpty()) }
    var formError by remember(initial?.id) { mutableStateOf<String?>(null) }
    var detected by remember(initial?.id) {
        mutableStateOf(
            initial?.takeIf { it.detectedTimeZone.isNotBlank() }?.let {
                ProxyGeoInfo(it.detectedCountry, it.detectedCountryCode, it.detectedTimeZone, it.detectedLatitude.toDoubleOrNull() ?: Double.NaN, it.detectedLongitude.toDoubleOrNull() ?: Double.NaN)
            },
        )
    }
    var probing by remember(initial?.id) { mutableStateOf(false) }
    var probeError by remember(initial?.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("إلغاء") }
            Text(if (initial == null) "إنشاء ملف" else "تعديل الملف", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(54.dp))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it; formError = null }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم الملف") }, placeholder = { Text("مثال: عمل، شخصي، متجر") }, singleLine = true, shape = RoundedCornerShape(14.dp))
            Text("البروكسي", color = Ink, fontWeight = FontWeight.Bold)
            Text("استخدم بروكسي KUN، أدخل بروكسيًا خارجيًا، أو اختر اتصالًا مباشرًا.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip(selected = proxyId == "direct", onClick = { proxyId = "direct"; protocol = "direct"; host = ""; port = ""; username = ""; password = ""; detected = null; probeError = null; if (locationMode == "auto") locationMode = "off" }, label = { Text("بدون بروكسي") })
                FilterChip(selected = proxyId == "manual", onClick = { proxyId = "manual"; if (protocol == "direct") protocol = "socks5"; detected = null; probeError = null }, label = { Text("إدخال يدوي") })
                if (proxiesLoading) FilterChip(selected = false, onClick = {}, enabled = false, label = { Text("جارٍ التحميل") })
            }
            if (accountProxies.isNotEmpty()) {
                Text("بروكسيات حسابك", color = Muted, style = MaterialTheme.typography.labelMedium)
                accountProxies.filter { it.status.equals("active", true) }.forEach { item ->
                    FilterChip(
                        selected = proxyId == item.id,
                        onClick = { proxyId = item.id; protocol = item.protocol; host = item.host; port = item.port.toString(); username = item.username; password = item.password; detected = null; probeError = null },
                        label = { Text(item.label.ifBlank { "${item.protocol.uppercase()} • ${item.host}:${item.port}" }, maxLines = 1) },
                    )
                }
            }
            if (proxyId == "direct") {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)), shape = RoundedCornerShape(14.dp)) {
                    Text("سيستخدم المتصفح الإنترنت مباشرةً من الهاتف. لن يمر الاتصال عبر KUN أو بروكسي، وقد يظهر عنوان اتصالك الحقيقي للمواقع.", Modifier.fillMaxWidth().padding(12.dp), color = Color(0xFF8A4B08), style = MaterialTheme.typography.bodySmall)
                }
            } else if (proxyId == "manual") {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(selected = protocol == "socks5", onClick = { protocol = "socks5"; detected = null }, label = { Text("SOCKS5") })
                    FilterChip(selected = protocol == "http", onClick = { protocol = "http"; detected = null }, label = { Text("HTTP") })
                }
                OutlinedTextField(value = host, onValueChange = { host = it.trim(); formError = null; detected = null }, modifier = Modifier.fillMaxWidth(), label = { Text("عنوان البروكسي") }, placeholder = { Text("proxy.example.com") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(value = port, onValueChange = { port = it.filter(Char::isDigit).take(5); formError = null; detected = null }, modifier = Modifier.fillMaxWidth(), label = { Text("المنفذ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(14.dp))
                Text("اترك بيانات الدخول فارغة إذا كان البروكسي لا يتطلب مصادقة.", color = Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم المستخدم (اختياري)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("كلمة مرور البروكسي (اختياري)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(14.dp))
            }
            if (proxyId != "direct") {
            OutlinedButton(
                onClick = {
                    val portNumber = port.toIntOrNull()
                    if (host.isBlank() || portNumber == null || portNumber !in 1..65535) {
                        probeError = "أدخل عنوان البروكسي ومنفذًا صالحًا أولًا."
                    } else {
                        probing = true
                        probeError = null
                        val candidate = BrowserProfile(
                            id = "probe", name = name.ifBlank { "فحص" }, proxyId = proxyId, protocol = protocol,
                            host = host.trim(), port = portNumber, username = username, password = password,
                        )
                        scope.launch {
                            val result = runCatching { withContext(Dispatchers.IO) { ProxyGeoProbe.detect(candidate) } }
                            probing = false
                            result.onSuccess { detected = it }.onFailure { probeError = it.message ?: "فشل اتصال البروكسي." }
                        }
                    }
                },
                enabled = !probing,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
            ) {
                if (probing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("فحص البروكسي ومطابقة إعداداته")
            }
            Text("يُرسل الفحص طلب HTTPS إلى ipwho.is عبر البروكسي فقط؛ ستشاهد الخدمة عنوان خروج البروكسي لتقدير البلد والوقت.", style = MaterialTheme.typography.bodySmall, color = Muted)
            Text("في SOCKS5 تُحل أسماء المواقع عبر البروكسي؛ قد يحتاج اسم خادم البروكسي نفسه إلى DNS الجهاز.", style = MaterialTheme.typography.bodySmall, color = Muted)
            detected?.let { geo ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF6EF)), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("تم اكتشاف خروج البروكسي", color = Color(0xFF1E6B42), fontWeight = FontWeight.Bold)
                        Text("${geo.country} • ${geo.timeZone} • ${geo.suggestedLanguage}", color = Color(0xFF315B43), style = MaterialTheme.typography.bodySmall)
                        Text("إحداثيات تقديرية من موقع IP: ${geo.latitude}, ${geo.longitude}", color = Color(0xFF315B43), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (probeError != null) Text(probeError.orEmpty(), color = Color(0xFFB3261E), style = MaterialTheme.typography.bodySmall)
            }
            SectionRule("اللغة")
            ChoiceRow(listOf("مطابقة البروكسي" to "auto", "مخصص" to "custom"), languageMode) { languageMode = it }
            if (languageMode == "auto") Text(if (proxyId == "direct") "سيستخدم الملف لغة الجهاز الحالية." else "بعد نجاح الفحص، تُستخدم لغة مناسبة لبلد خروج البروكسي.", style = MaterialTheme.typography.bodySmall, color = Muted)
            if (languageMode == "custom") OutlinedTextField(value = language, onValueChange = { language = it }, modifier = Modifier.fillMaxWidth(), label = { Text("رمز اللغة") }, placeholder = { Text("ar-SA أو en-US") }, singleLine = true, shape = RoundedCornerShape(14.dp))
            SectionRule("المنطقة الزمنية")
            ChoiceRow(listOf("مطابقة البروكسي" to "auto", "مخصص" to "custom"), timeMode) { timeMode = it }
            if (timeMode == "auto") Text(if (proxyId == "direct") "سيستخدم الملف المنطقة الزمنية الحالية للجهاز." else "بعد نجاح الفحص، تُضبط منطقة Gecko الزمنية حسب بلد خروج البروكسي.", style = MaterialTheme.typography.bodySmall, color = Muted)
            if (timeMode == "custom") OutlinedTextField(value = timeZone, onValueChange = { timeZone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("المنطقة الزمنية") }, placeholder = { Text("Europe/Istanbul") }, singleLine = true, shape = RoundedCornerShape(14.dp))
            SectionRule("الموقع")
            ChoiceRow(if (proxyId == "direct") listOf("موقع مخصص" to "custom", "إيقاف" to "off") else listOf("تلقائي عبر IP" to "auto", "موقع مخصص" to "custom", "إيقاف" to "off"), locationMode) { locationMode = it }
            if (locationMode == "auto" && proxyId != "direct") Text("يُستخدم موقع IP التقريبي المكتشف عبر البروكسي. وهو لا يمثل GPS دقيقًا.", style = MaterialTheme.typography.bodySmall, color = Muted)
            if (locationMode == "custom") {
                Text("أدخل إحداثيات يدوية للمواقع التي تطلب الموقع. لا يقرأ المتصفح GPS الحقيقي للهاتف.", style = MaterialTheme.typography.bodySmall, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = latitude, onValueChange = { latitude = it }, modifier = Modifier.weight(1f), label = { Text("خط العرض") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(value = longitude, onValueChange = { longitude = it }, modifier = Modifier.weight(1f), label = { Text("خط الطول") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(14.dp))
                }
            }
            Text("ملفات التصفح محفوظة على هذا الجهاز. كلمات مرور البروكسي ورموز الجلسة مشفّرة بمفتاح Android Keystore؛ الملفات لا تُرفع إلى حساب KUN.", color = Muted, style = MaterialTheme.typography.bodySmall)
            if (formError != null || saveError != null) Text(formError ?: saveError.orEmpty(), color = Color(0xFFB3261E), style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    val direct = proxyId == "direct"
                    val portNumber = port.toIntOrNull()
                    formError = when {
                        name.isBlank() -> "اكتب اسمًا للملف."
                        !direct && host.isBlank() -> "أدخل عنوان البروكسي أو اختر الاتصال المباشر."
                        !direct && (portNumber == null || portNumber !in 1..65535) -> "أدخل منفذًا صالحًا بين 1 و65535."
                        languageMode == "custom" && language.isBlank() -> "أدخل رمز اللغة أو اختر التلقائي."
                        timeMode == "custom" && timeZone.isBlank() -> "أدخل المنطقة الزمنية أو اختر التلقائي."
                        locationMode == "custom" && (latitude.toDoubleOrNull()?.let { it !in -90.0..90.0 } != false || longitude.toDoubleOrNull()?.let { it !in -180.0..180.0 } != false) -> "أدخل خطي عرض وطول ضمن الحدود الصحيحة."
                        else -> null
                    }
                    if (formError == null) onSave(BrowserProfile(
                        id = initial?.id ?: java.util.UUID.randomUUID().toString(), name = name.trim(), proxyId = proxyId,
                        protocol = if (direct) "direct" else protocol, host = if (direct) "" else host.trim(), port = if (direct) 0 else portNumber!!,
                        username = if (direct) "" else username, password = if (direct) "" else password,
                        languageMode = languageMode, language = language.trim(), timeZoneMode = timeMode, timeZone = timeZone.trim(),
                        locationMode = if (direct && locationMode == "auto") "off" else locationMode, latitude = latitude.trim(), longitude = longitude.trim(),
                        detectedCountry = detected?.country.orEmpty(), detectedCountryCode = detected?.countryCode.orEmpty(),
                        detectedLocale = detected?.suggestedLanguage.orEmpty(), detectedTimeZone = detected?.timeZone.orEmpty(),
                        detectedLatitude = detected?.latitude?.takeIf { it.isFinite() }?.toString().orEmpty(),
                        detectedLongitude = detected?.longitude?.takeIf { it.isFinite() }?.toString().orEmpty(),
                    ))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp),
            ) { Text("إنشاء الملف وحفظه", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SectionRule(title: String) {
    Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Line))
    }
}

@Composable
private fun ChoiceRow(choices: List<Pair<String, String>>, selected: String, onSelected: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        choices.forEach { (label, value) -> FilterChip(selected = selected == value, onClick = { onSelected(value) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) }) }
    }
}
