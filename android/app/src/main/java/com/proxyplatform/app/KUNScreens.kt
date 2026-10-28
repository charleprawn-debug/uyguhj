package com.proxyplatform.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun AuthScreen(vm: AppViewModel) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(KunPalette.Background)) {
        Box(
            Modifier.fillMaxWidth().height(285.dp)
                .background(Brush.linearGradient(listOf(KunPalette.PrimaryDeep, KunPalette.Primary, KunPalette.Cyan)))
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            BrandMark(58.dp)
            Spacer(Modifier.height(14.dp))
            Text("KUN Proxy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("اتصالك، بإعدادك.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f))
            Spacer(Modifier.height(30.dp))

            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 7.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        if (register) "حسابك يبدأ من هنا" else "أهلًا بعودتك",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        if (register) "أنشئ حسابًا لإدارة خدماتك وملفك الشخصي." else "سجّل الدخول للمتابعة إلى KUN Proxy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KunPalette.SurfaceSoft).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AuthModeButton("تسجيل الدخول", selected = !register, modifier = Modifier.weight(1f)) {
                            register = false
                            vm.clearError()
                        }
                        AuthModeButton("حساب جديد", selected = register, modifier = Modifier.weight(1f)) {
                            register = true
                            vm.clearError()
                        }
                    }

                    AnimatedContent(targetState = register) { showName ->
                        if (showName) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("الاسم الكامل") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                            )
                        } else {
                            Spacer(Modifier.height(1.dp))
                        }
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("البريد الإلكتروني") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("كلمة المرور") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    )

                    AnimatedVisibility(visible = vm.error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Surface(
                            color = if (vm.error?.startsWith("تم إنشاء الحساب") == true) KunPalette.SuccessSoft else KunPalette.ErrorSoft,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(
                                vm.error.orEmpty(),
                                Modifier.fillMaxWidth().padding(12.dp),
                                color = if (vm.error?.startsWith("تم إنشاء الحساب") == true) KunPalette.Success else KunPalette.Error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (register) vm.register(email, password, name) { register = false }
                            else vm.login(email, password)
                        },
                        enabled = !vm.loading && email.isNotBlank() && password.length >= 8 && (!register || name.isNotBlank()),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(17.dp),
                    ) {
                        if (vm.loading) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.5.dp, color = Color.White)
                        } else {
                            Text(if (register) "إنشاء الحساب" else "تسجيل الدخول", fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "تُستخدم بيانات الدخول للمصادقة مع الخدمة فقط.",
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FeaturePill("VPN")
                FeaturePill("HTTP")
                FeaturePill("SOCKS5")
            }
        }
    }
}

@Composable
private fun AuthModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(13.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) Color.White else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 8.dp, vertical = 11.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun MainShell(vm: AppViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var screen by remember { mutableStateOf(Screen.SUBSCRIPTIONS) }
    LaunchedEffect(Unit) {
        vm.loadPlansAndWallet()
        while (true) {
            vm.updateWirelessState(withContext(Dispatchers.IO) { WirelessDebuggingManager.checkState(context) })
            delay(1_500)
        }
    }
    LaunchedEffect(screen) {
        AdvancedOperationLog.info(context, "App navigation: ${screen.name}.")
        vm.clearError()
        when (screen) {
            Screen.PROFILE -> { vm.loadProfile(); vm.loadPlansAndWallet() }
            Screen.SUBSCRIPTIONS -> vm.loadSubscriptions()
            Screen.PROXY -> Unit
            Screen.SIM -> Unit
            Screen.ADB -> Unit
            Screen.WALLET -> vm.loadPlansAndWallet()
        }
    }

    val sectionTitle = when (screen) {
        Screen.SUBSCRIPTIONS -> "اشتراكاتي"
        Screen.PROXY -> "اتصال البروكسي"
        Screen.SIM -> "SIM"
        Screen.ADB -> "ADB"
        Screen.PROFILE -> "حسابي"
        Screen.WALLET -> "المحفظة"
    }
    val sectionDescription = when (screen) {
        Screen.SUBSCRIPTIONS -> "إدارة خططك الحالية"
        Screen.PROXY -> "إعداد اتصالك والتحكم فيه"
        Screen.SIM -> "اختبار خصائص المشغّل عبر ADB"
        Screen.ADB -> "إعداد الاتصال اللاسلكي وإدارته"
        Screen.PROFILE -> "بيانات الحساب والتحقق"
        Screen.WALLET -> "الرصيد وسجل العمليات"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BrandMark(42.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("KUN Proxy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            Text(sectionDescription, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FeaturePill(sectionTitle, color = KunPalette.Primary, container = MaterialTheme.colorScheme.primaryContainer)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(KunPalette.Border))
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 5.dp) {
                val entries = listOf(
                    Triple(Screen.SUBSCRIPTIONS, "الخطط", R.drawable.ic_nav_subscriptions),
                    Triple(Screen.PROXY, "الاتصال", R.drawable.ic_nav_proxy),
                    Triple(Screen.ADB, "ADB", R.drawable.ic_nav_adb),
                    Triple(Screen.SIM, "SIM", R.drawable.ic_nav_sim),
                    Triple(Screen.PROFILE, "حسابي", R.drawable.ic_nav_profile),
                )
                entries.forEach { (destination, label, iconId) ->
                    NavigationBarItem(
                        selected = screen == destination,
                        onClick = { screen = destination },
                        icon = { Icon(painterResource(iconId), contentDescription = label, modifier = Modifier.size(22.dp)) },
                        label = { Text(label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Crossfade(targetState = screen, modifier = Modifier.fillMaxSize()) { destination ->
            when (destination) {
                Screen.SUBSCRIPTIONS -> Subscriptions(vm, padding)
                Screen.PROXY -> FeatureGate(vm.access.vpn || vm.access.advanced, "لا يوجد اشتراك اتصال نشط") { ProxyScreen(padding, vm.wirelessState == WirelessDebuggingManager.DebuggingState.READY) { screen = Screen.ADB } }
                Screen.SIM -> {
                    if (vm.wirelessState != WirelessDebuggingManager.DebuggingState.READY) {
                        AdbRequiredPanel(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            title = "إعدادات الشريحة",
                            description = "تستخدم هذه الصفحة اتصال Wireless ADB المشترك لتنفيذ عمليات SIM بأمان.",
                        ) { screen = Screen.ADB }
                    } else {
                        FeatureGate(vm.access.sim, "هذه الميزة غير موجودة في اشتراكك الحالي") {
                            SimScreen(padding, adbReady = true) { screen = Screen.ADB }
                        }
                    }
                }
                Screen.ADB -> AdbScreen(vm, padding)
                Screen.PROFILE -> ProfileScreen(vm, padding, onWallet = { screen = Screen.WALLET }, onPlans = { screen = Screen.SUBSCRIPTIONS })
                Screen.WALLET -> WalletScreen(vm, padding)
            }
        }
    }
}

@Composable
internal fun Subscriptions(vm: AppViewModel, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeading("حسابك", "باقات الاشتراك", "اختر الباقة المناسبة وفعّلها من رصيد محفظتك.")
        }
        if (vm.plansLoading && vm.plans.isEmpty()) item { StateCard("جارٍ تحميل الباقات", "نسترجع الباقات المتاحة.", loading = true) }
        if (vm.plansError != null && vm.plans.isEmpty()) item { StateCard("تعذّر تحميل الباقات", vm.plansError.orEmpty(), error = true, actionLabel = "إعادة المحاولة", onAction = { vm.loadPlansAndWallet() }) }
        items(vm.plans, key = { it.id }) { plan -> PlanCard(plan, vm) }
        if (vm.subscriptions.isNotEmpty()) item { Text("اشتراكاتي النشطة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        when {
            vm.subscriptionsLoading && vm.subscriptions.isEmpty() -> item {
                StateCard("جارٍ تحميل الاشتراكات", "نسترجع بيانات حسابك من الخدمة.", loading = true)
            }
            vm.subscriptionsError != null && vm.subscriptions.isEmpty() -> item {
                StateCard("تعذّر تحميل الاشتراكات", vm.subscriptionsError.orEmpty(), error = true, actionLabel = "إعادة المحاولة", onAction = { vm.loadSubscriptions() })
            }
            vm.subscriptions.isEmpty() -> Unit
            else -> {
                if (vm.subscriptionsError != null) item {
                    StateCard("تعذّر تحديث القائمة", "نعرض آخر بيانات الاشتراكات التي تم تحميلها.", error = true, actionLabel = "إعادة المحاولة", onAction = { vm.loadSubscriptions() })
                }
                items(vm.subscriptions) { subscription ->
                    SubscriptionCard(subscription)
                }
            }
        }
    }
}

@Composable
private fun SubscriptionCard(subscription: Subscription) {
    val active = subscription.status.equals("active", ignoreCase = true)
    KUNCard {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrandMark(40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(subscription.product, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(subscription.protocol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FeaturePill(
                    statusLabel(subscription.status),
                    color = if (active) KunPalette.Success else KunPalette.Warning,
                    container = if (active) KunPalette.SuccessSoft else KunPalette.WarningSoft,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(KunPalette.Border))
            KeyValueRow("تنتهي في", readableDate(subscription.expires))
        }
    }
}

@Composable
internal fun ProfileScreen(vm: AppViewModel, padding: PaddingValues, onWallet: () -> Unit, onPlans: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { PageHeading("إعدادات الحساب", "ملفي الشخصي", "معلومات حسابك وحالة التحقق من البريد الإلكتروني.") }
        if (vm.profileLoading && vm.profile == null) {
            item { StateCard("جارٍ تحميل الحساب", "نطلب معلومات الملف الشخصي من الخدمة.", loading = true) }
        } else if (vm.profileError != null && vm.profile == null) {
            item { StateCard("تعذّر تحميل الحساب", vm.profileError.orEmpty(), error = true, actionLabel = "إعادة المحاولة", onAction = { vm.loadProfile() }) }
        } else if (vm.profile == null) {
            item { StateCard("معلومات الحساب غير متاحة", "جرّب تحديث بيانات الملف الشخصي.", actionLabel = "إعادة المحاولة", onAction = { vm.loadProfile() }) }
        } else {
            val profile = vm.profile!!
            item {
                Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = KunPalette.PrimaryDeep)) {
                    Row(
                        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(KunPalette.PrimaryDeep, KunPalette.Primary, KunPalette.Cyan))).padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Text(profile.name.take(1).ifBlank { "K" }.uppercase(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(profile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text(profile.email, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
            }
            item {
                KUNCard {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                        Text("حالة الحساب", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        KeyValueRow("نوع الحساب", roleLabel(profile.role))
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("البريد الإلكتروني", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FeaturePill(
                                if (profile.verified) "تم التحقق" else "بانتظار التحقق",
                                color = if (profile.verified) KunPalette.Success else KunPalette.Warning,
                                container = if (profile.verified) KunPalette.SuccessSoft else KunPalette.WarningSoft,
                            )
                        }
                    }
                }
            }
            if (vm.profileError != null) item {
                StateCard("تعذّر تحديث بيانات الحساب", "نعرض آخر بيانات نجح تحميلها.", error = true, actionLabel = "إعادة المحاولة", onAction = { vm.loadProfile() })
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onWallet, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("المحفظة", fontWeight = FontWeight.Bold) }
            Button(onClick = onPlans, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("الاشتراك", fontWeight = FontWeight.Bold) }
        } }
        item {
            OutlinedButton(
                onClick = { vm.logout() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KunPalette.Error),
            ) { Text("تسجيل الخروج", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun WalletCard(vm: AppViewModel, onTopUp: () -> Unit) {
    KUNCard { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column { Text("المحفظة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("الرصيد المتاح", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(vm.wallet?.let { "%.2f ${it.currency}".format(Locale.US, it.balance) } ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = KunPalette.Primary)
        }
        Button(onClick = onTopUp, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("شحن الرصيد", fontWeight = FontWeight.Bold) }
        if (vm.walletTransactions.isNotEmpty()) { Text("آخر العمليات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); vm.walletTransactions.take(3).forEach { tx -> KeyValueRow(tx.description, "${if (tx.amount >= 0) "+" else ""}%.2f".format(Locale.US, tx.amount)) } }
    } }
}

@Composable
private fun WalletScreen(vm: AppViewModel, padding: PaddingValues) {
    var topUpOpen by remember { mutableStateOf(false) }
    var selectedMethod by remember { mutableStateOf<TopUpMethod?>(null) }
    var amountText by remember { mutableStateOf("") }
    var referenceText by remember { mutableStateOf("") }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading("حسابك", "المحفظة", "إدارة رصيدك ومراجعة جميع العمليات المالية.") }
        item { WalletCard(vm) { topUpOpen = true; selectedMethod = null; amountText = ""; referenceText = "" } }
        if (topUpOpen) item {
            KUNCard { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("شحن المحفظة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    TextButton(onClick = { topUpOpen = false; vm.clearTopUp() }) { Text("إغلاق") }
                }
                if (vm.topUpError != null) StateCard("تعذر تنفيذ طلب الشحن", vm.topUpError.orEmpty(), error = true)
                val created = vm.topUpCreated
                if (created == null) {
                    Text("اختر طريقة الشحن المناسبة لك.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    vm.topUpMethods.forEach { method ->
                        FilterChip(selected = selectedMethod?.code == method.code, onClick = { selectedMethod = method; amountText = "" }, label = { Text(method.name) }, modifier = Modifier.fillMaxWidth(), enabled = method.address.isNotBlank())
                    }
                    if (vm.topUpMethods.isEmpty()) Text("لا توجد طرق شحن مهيأة حاليًا.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    selectedMethod?.let { method ->
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("أرسل المبلغ إلى العنوان التالي", fontWeight = FontWeight.Bold)
                                Text(method.address, fontWeight = FontWeight.ExtraBold, color = KunPalette.Primary)
                                if (method.network.isNotBlank()) Text("الشبكة: ${method.network}")
                                Text("الحد الأدنى: ${method.minAmount} ${method.currency}")
                                if (method.currency == "SYP") Text("سعر الصرف: 1 دولار = ${method.exchangeRate} ليرة سورية")
                                if (method.instructions.isNotBlank()) Text(method.instructions, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        OutlinedTextField(amountText, { amountText = it }, Modifier.fillMaxWidth(), label = { Text("المبلغ الذي سترسله (${method.currency})") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(15.dp))
                        val amount = amountText.toDoubleOrNull()
                        if (method.currency == "SYP" && amount != null && method.exchangeRate > 0) Text("سيضاف تقريبًا: ${"%.2f".format(Locale.US, amount / method.exchangeRate)} USD", color = KunPalette.Primary, fontWeight = FontWeight.Bold)
                        Button(onClick = { if (amount != null) vm.createTopUp(method, amount) }, enabled = amount != null && amount > 0 && !vm.topUpLoading, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) {
                            if (vm.topUpLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("تم الإرسال — تحقق من العملية", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Text("أدخل رقم العملية بعد إتمام التحويل.", style = MaterialTheme.typography.bodyMedium)
                    Text("المبلغ المحتسب: ${"%.2f".format(Locale.US, created.expectedUsd)} USD", fontWeight = FontWeight.Bold, color = KunPalette.Primary)
                    OutlinedTextField(referenceText, { referenceText = it }, Modifier.fillMaxWidth(), label = { Text("رقم العملية / Tx ID") }, singleLine = true, shape = RoundedCornerShape(15.dp))
                    Button(onClick = { vm.verifyTopUp(created, referenceText.trim()) { } }, enabled = referenceText.trim().length >= 3 && !vm.topUpLoading, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) {
                        if (vm.topUpLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("تحقق من العملية", fontWeight = FontWeight.Bold)
                    }
                    Text("سيظهر الطلب في السجل بحالة قيد التحقق، ولن يضاف الرصيد أكثر من مرة لنفس رقم العملية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
        }
        item { Text("سجل العمليات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (vm.walletTransactions.isEmpty()) item { StateCard("لا توجد عمليات بعد", "ستظهر هنا عمليات الخصم والإضافة عند توفرها.") }
        items(vm.walletTransactions) { tx -> KUNCard { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(tx.description, fontWeight = FontWeight.Bold); Text(readableDate(tx.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text("${if (tx.amount >= 0) "+" else ""}%.2f".format(Locale.US, tx.amount), fontWeight = FontWeight.ExtraBold, color = if (tx.amount >= 0) KunPalette.Success else KunPalette.Error) } } }
        if (vm.topUpRequests.isNotEmpty()) item { Text("طلبات الشحن", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(vm.topUpRequests, key = { it.id }) { request ->
            KUNCard { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(request.methodCode, fontWeight = FontWeight.Bold); Text("${request.amount} ${request.currency} • ${"%.2f".format(Locale.US, request.expectedUsd)} USD", style = MaterialTheme.typography.bodySmall); Text(request.reference, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                FeaturePill(statusLabel(request.status), color = if (request.status == "verified") KunPalette.Success else if (request.status == "rejected") KunPalette.Error else KunPalette.Warning, container = if (request.status == "verified") KunPalette.SuccessSoft else if (request.status == "rejected") KunPalette.ErrorSoft else KunPalette.WarningSoft)
            } }
        }
    }
}

@Composable
private fun PlanCard(plan: Plan, vm: AppViewModel) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), border = BorderStroke(if (plan.featured) 2.dp else 1.dp, if (plan.featured) KunPalette.Primary else KunPalette.Border), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Column(Modifier.weight(1f)) { Text(plan.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(plan.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (plan.featured) FeaturePill("مميز", color = KunPalette.Primary, container = MaterialTheme.colorScheme.primaryContainer) }
            Text(plan.monthly.ifBlank { "السعر غير محدد" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = KunPalette.Primary)
            Text("المدة: ${plan.duration}", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { val labels = mapOf("advanced" to "الوضع المتقدم", "sim" to "SIM", "vpn" to "VPN", "mock_location" to "محاكاة الموقع"); plan.features.forEach { labels[it]?.let { label -> FeaturePill(label, color = KunPalette.Success, container = KunPalette.SuccessSoft) } } }
            Button(onClick = { vm.activatePlan(plan) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) { Text("تفعيل من رصيد المحفظة", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun FeatureGate(allowed: Boolean, message: String, content: @Composable () -> Unit) {
    if (allowed) content() else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { StateCard("الميزة تحتاج اشتراكًا", message, actionLabel = "عرض الخطط", onAction = {}) } }
}

private fun statusLabel(status: String): String = when (status.lowercase(Locale.ROOT)) {
    "active" -> "نشط"
    "pending" -> "قيد الانتظار"
    "expired" -> "منتهي"
    "cancelled", "canceled" -> "ملغي"
    else -> status.ifBlank { "غير محدد" }
}

private fun roleLabel(role: String): String = when (role.lowercase(Locale.ROOT)) {
    "user" -> "مستخدم"
    "admin" -> "مدير"
    else -> role.ifBlank { "مستخدم" }
}

private fun readableDate(value: String): String {
    if (value.isBlank()) return "غير محدد"
    return runCatching {
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("ar"))
            .format(Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate())
    }.getOrDefault(value)
}
