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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

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
    var screen by remember { mutableStateOf(Screen.MARKET) }
    LaunchedEffect(Unit) { vm.loadPlansAndWallet() }
    LaunchedEffect(screen) {
        AdvancedOperationLog.info(context, "App navigation: ${screen.name}.")
        vm.clearError()
        when (screen) {
            Screen.MARKET -> vm.loadMarket()
            Screen.PROFILE -> { vm.loadProfile(); vm.loadPlansAndWallet() }
            Screen.SUBSCRIPTIONS -> vm.loadSubscriptions()
            Screen.PROXY -> Unit
            Screen.SIM -> Unit
            Screen.WALLET -> vm.loadPlansAndWallet()
        }
    }

    val sectionTitle = when (screen) {
        Screen.MARKET -> "السوق"
        Screen.SUBSCRIPTIONS -> "اشتراكاتي"
        Screen.PROXY -> "اتصال البروكسي"
        Screen.SIM -> "SIM"
        Screen.PROFILE -> "حسابي"
        Screen.WALLET -> "المحفظة"
    }
    val sectionDescription = when (screen) {
        Screen.MARKET -> "تصفّح المنتجات المتاحة"
        Screen.SUBSCRIPTIONS -> "إدارة خططك الحالية"
        Screen.PROXY -> "إعداد اتصالك والتحكم فيه"
        Screen.SIM -> "اختبار خصائص المشغّل عبر ADB"
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
                    Triple(Screen.MARKET, "السوق", R.drawable.ic_nav_market),
                    Triple(Screen.SUBSCRIPTIONS, "الخطط", R.drawable.ic_nav_subscriptions),
                    Triple(Screen.PROXY, "الاتصال", R.drawable.ic_nav_proxy),
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
                Screen.MARKET -> Marketplace(vm, padding)
                Screen.SUBSCRIPTIONS -> Subscriptions(vm, padding) { screen = Screen.MARKET }
                Screen.PROXY -> FeatureGate(vm.access.vpn || vm.access.advanced, "لا يوجد اشتراك اتصال نشط") { ProxyScreen(padding) }
                Screen.SIM -> FeatureGate(vm.access.sim, "هذه الميزة غير موجودة في اشتراكك الحالي") { SimScreen(padding) }
                Screen.PROFILE -> ProfileScreen(vm, padding, onWallet = { screen = Screen.WALLET }, onPlans = { screen = Screen.SUBSCRIPTIONS })
                Screen.WALLET -> WalletScreen(vm, padding)
            }
        }
    }
}

@Composable
internal fun Marketplace(vm: AppViewModel, padding: PaddingValues) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var section by remember { mutableStateOf("home") }
    var selectedCountry by remember { mutableStateOf<String?>(null) }
    var selectedState by remember { mutableStateOf<String?>(null) }
    var selectedCity by remember { mutableStateOf<String?>(null) }
    var selectedProvider by remember { mutableStateOf<ProxyPurchaseLocation?>(null) }
    var selectedPlan by remember { mutableStateOf<ProxyPurchasePlan?>(null) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var review by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<String?>(null) }
    val options = vm.purchaseOptions
    val locations = options?.locations.orEmpty()
    val countryOptions = locations.map { it.country to it.countryCode }.filter { it.first.isNotBlank() }.distinctBy { it.first }.sortedBy { it.first }
    val countries = countryOptions.map { it.first }
    val countryLocations = locations.filter { it.country == selectedCountry }
    val states = countryLocations.map { it.state }.filter { it.isNotBlank() }.distinct().sorted()
    val stateLocations = countryLocations.filter { selectedState == null || it.state == selectedState }
    val cityName = { location: ProxyPurchaseLocation -> location.provider.substringBefore(" • ").trim().takeIf { " • " in location.provider } }
    val cities = stateLocations.mapNotNull(cityName).distinct().sorted()
    val cityLocations = stateLocations
    val providers = cityLocations.filter { location ->
        val locationCity = cityName(location)
        (selectedCity == null || locationCity == selectedCity) && location.provider.isNotBlank()
    }
    val isps = providers.map { it.provider.substringAfter(" • ", it.provider) }.distinct().sorted()
    val activeProxies = vm.remoteProxies.filter { it.status.isBlank() || it.status.equals("active", true) }

    LaunchedEffect(section) {
        if (section == "purchase" && vm.purchaseOptions == null) vm.loadPurchaseOptions()
        if (section == "proxies") vm.loadRemoteProxies()
    }
    LaunchedEffect(vm.purchaseOptions) {
        // A Proxy-Cheap plan change replaces the options object. Keep the plan
        // selection, but discard location selections from the previous catalog.
        selectedCountry = null; selectedState = null; selectedCity = null; selectedProvider = null; picker = null; review = false
    }
    LaunchedEffect(vm.selectedMarketplaceProvider) { selectedCountry = null; selectedState = null; selectedCity = null; selectedProvider = null; picker = null; selectedPlan = null; review = false }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = KunPalette.PrimaryDeep)) {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(KunPalette.PrimaryDeep, KunPalette.Primary, KunPalette.Cyan))).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("السوق", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Text("إدارة بروكسياتك وشراء اتصال جديد بخطوات واضحة وسريعة.", color = Color.White.copy(alpha = .88f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (section == "home") {
            item { MarketActionCard("بروكسياتي", "عرض البروكسيات النشطة واختيار البروكسي المستخدم", "فتح بروكسياتي") { section = "proxies" } }
            item { MarketActionCard("شراء بروكسي", "اختر السيرفر المناسب ثم أكمل خطوات الشراء والسعر المباشر.", "اختيار السيرفر", highlighted = true) { section = "servers" } }
        } else {
            item { TextButton(onClick = { section = "home"; vm.closePurchase() }) { Text("رجوع إلى السوق") } }
            if (section == "servers") {
                item { Text("اختر سيرفر الشراء", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) }
                if (vm.marketplaceProvidersLoading) item { StateCard("جارٍ تحميل السيرفرات", "نجهز مزودي البروكسي المتاحين.", loading = true) }
                else if (vm.marketplaceProvidersError != null) item { StateCard("تعذر تحميل السيرفرات", vm.marketplaceProvidersError.orEmpty(), error = true, actionLabel = "إعادة المحاولة", onAction = { vm.loadMarketplaceProviders() }) }
                else items(vm.marketplaceProviders.filter { it.id != "proxy-seller" }, key = { it.id }) { provider ->
                    MarketActionCard(provider.name, provider.description, "المتابعة مع ${provider.name}", highlighted = provider.number == 1) { vm.chooseMarketplaceProvider(provider.id); section = "purchase"; vm.loadPurchaseOptions() }
                }
            } else if (section == "proxies") {
                item { Text("بروكسياتي", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) }
                if (vm.remoteProxiesLoading) item { StateCard("جارٍ تحميل البروكسيات", "نقرأ البروكسيات المرتبطة بحسابك.", loading = true) }
                else if (vm.remoteProxiesError != null) item { StateCard("تعذر تحميل البروكسيات", vm.remoteProxiesError.orEmpty(), error = true, actionLabel = "إعادة المحاولة", onAction = { vm.loadRemoteProxies() }) }
                else if (activeProxies.isEmpty()) item { StateCard("لا توجد بروكسيات نشطة", "اشترِ بروكسيًا جديدًا ليظهر هنا تلقائيًا.", actionLabel = "شراء بروكسي", onAction = { section = "purchase" }) }
                else items(activeProxies, key = { it.id }) { proxy ->
                    RemoteProxyCard(proxy) {
                        runCatching { ProxyProfileStore(context.applicationContext).save(SavedProxy(proxy.id.ifBlank { UUID.randomUUID().toString() }, proxy.location.ifBlank { "بروكسي محفوظ" }, proxy.protocol, proxy.host, proxy.port, proxy.username.isNotBlank(), proxy.username, proxy.password)) }
                            .onSuccess { section = "home" }
                    }
                }
            } else {
                    item { Text("شراء بروكسي — ${vm.marketplaceProviders.firstOrNull { it.id == vm.selectedMarketplaceProvider }?.name ?: "السيرفر"}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) }
                if (vm.purchaseError != null) item { StateCard(if (options == null) "تعذر تحميل خيارات الشراء" else "تعذر إتمام العملية", vm.purchaseError.orEmpty(), error = true, actionLabel = "إعادة المحاولة", onAction = { vm.clearPurchaseError(); if (options == null) { val plan = selectedPlan; vm.closePurchase(); if (plan != null && vm.selectedMarketplaceProvider != "ezproxy") vm.loadPurchaseOptionsFor(vm.selectedMarketplaceProvider, plan.serviceId, plan.providerPlanId.ifBlank { plan.id }) else vm.loadPurchaseOptions() } }) }
                if (vm.purchaseLoading && options == null) item { StateCard("جارٍ تحميل الدول", "نطلب الخيارات المتاحة من مزود الخدمة.", loading = true) }
                else if (options != null) {
                    if (options.provider != "ezproxy") item {
                        PlanChoiceCards(options.plans, selectedPlan) { plan ->
                            if (selectedPlan?.providerPlanId != plan.providerPlanId || selectedPlan?.id != plan.id) {
                                selectedPlan = plan; review = false
                                vm.loadPurchaseOptionsFor(options.provider, plan.serviceId.ifBlank { options.serviceId }, plan.providerPlanId.ifBlank { plan.id })
                            }
                        }
                    }
                    val planMatches = options.provider == "ezproxy" || (selectedPlan != null && (options.selectedPlanId.isBlank() || options.selectedPlanId == selectedPlan.let { plan -> plan?.providerPlanId.orEmpty().ifBlank { plan?.id.orEmpty() } }))
                    if (options.provider != "ezproxy" && selectedPlan != null && !planMatches) item { StateCard("جارٍ تحميل مواقع الباقة", "نطلب الدول والولايات والمزودين المتاحين للباقة المختارة.", loading = true) }
                    if (planMatches) {
                        item { LocationPickerField("الدولة", selectedCountry ?: "", "اختر الدولة", selectedCountry != null) { picker = "country" } }
                        if (selectedCountry != null && states.isNotEmpty()) item { LocationPickerField("الولاية / المنطقة", selectedState ?: "", "اختر الولاية أو المنطقة", selectedState != null) { picker = "state" } }
                        if (selectedCountry != null && cities.isNotEmpty()) item { LocationPickerField("المدينة", selectedCity ?: "", "اختر المدينة أو عشوائي", selectedCity != null) { picker = "city" } }
                        if (selectedCountry != null && (states.isEmpty() || selectedState != null) && (cities.isEmpty() || selectedCity != null) && providers.isNotEmpty()) item { LocationPickerField("مزود الخدمة ISP", selectedProvider?.provider?.substringAfter(" • ", selectedProvider?.provider.orEmpty()) ?: "", "اختر المزود", selectedProvider != null) { picker = "isp" } }
                    }
                    if (selectedProvider != null) item {
                        KUNCard { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("البيانات النهائية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (options.provider == "ezproxy") {
                                Text("ستُستخدم هذه البيانات لإنشاء البروكسي لدى المزود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("اسم المستخدم") }, singleLine = true, shape = RoundedCornerShape(15.dp))
                                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("كلمة المرور") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(15.dp))
                            } else Text("سيُصدر المزود بيانات الدخول تلقائيًا بعد إنشاء البروكسي السكني.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (options.provider == "ezproxy") Text("5. اختر المدة", fontWeight = FontWeight.Bold)
                            if (options.provider == "ezproxy") options.plans.forEach { plan -> FilterChip(selected = selectedPlan?.id == plan.id, onClick = { selectedPlan = plan; review = false }, label = { Text(if (plan.price > 0) "${plan.name} — ${"%.2f".format(Locale.US, plan.price)} دولار" else "${plan.name} — السعر يحسب مباشرة من المزود") }, modifier = Modifier.fillMaxWidth()) }
                            val ready = (options.provider != "ezproxy" || (username.trim().length >= 3 && password.length >= 6)) && selectedPlan != null
                            Button(onClick = { review = true; if (selectedPlan != null && selectedProvider != null) vm.loadProxyQuote(options, selectedPlan!!, selectedProvider!!) }, enabled = ready && !vm.purchaseLoading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("مراجعة الطلب", fontWeight = FontWeight.Bold) }
                        } }
                    }
                    if (review && selectedProvider != null && selectedPlan != null) item {
                        val plan = selectedPlan!!
                        KUNCard { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("مراجعة وتأكيد", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            KeyValueRow("الدولة", selectedProvider!!.country)
                            KeyValueRow("الولاية", selectedProvider!!.state)
                            KeyValueRow("المزود", selectedProvider!!.provider)
                            KeyValueRow("المدة", plan.name)
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) { Text(vm.purchaseQuote?.let { "الإجمالي: ${"%.2f".format(Locale.US, it)} دولار — رصيدك: ${"%.2f".format(Locale.US, vm.wallet?.balance ?: 0.0)} ${vm.wallet?.currency ?: "USD"}" } ?: if (options.provider == "proxy-seller") "لا يعرض Proxy-Seller سعر إنشاء القائمة؛ سيتم استخدام باقتك residential الحالية." else "جارٍ جلب السعر المباشر من المزود…", Modifier.fillMaxWidth().padding(12.dp), fontWeight = FontWeight.Bold) }
                            Button(onClick = { vm.confirmProxyPurchase(options, plan, selectedProvider!!, if (options.provider == "ezproxy") username.trim() else "", if (options.provider == "ezproxy") password else "") { purchased ->
                                runCatching { ProxyProfileStore(context.applicationContext).save(SavedProxy(UUID.randomUUID().toString(), "${selectedProvider!!.country} • ${selectedProvider!!.provider}", purchased.protocol, purchased.host, purchased.port, true, purchased.username, purchased.password)) }
                                section = "proxies"; vm.loadRemoteProxies()
                            } }, enabled = !vm.purchaseLoading, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                                if (vm.purchaseLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("تأكيد الشراء والخصم من المحفظة", fontWeight = FontWeight.Bold)
                            }
                        } }
                    }
                }
            }
        }
    }
    if (picker != null) {
        val pickerValues = when (picker) {
            "country" -> countries
            "state" -> states
            "city" -> listOf("عشوائي") + cities
            "isp" -> isps
            else -> emptyList()
        }
        SearchSelectionSheet(
            title = when (picker) {
                "country" -> "اختر الدولة"
                "state" -> "اختر الولاية أو المنطقة"
                "city" -> "اختر المدينة"
                else -> "اختر مزود الخدمة"
            },
            values = pickerValues,
            selected = when (picker) {
                "country" -> selectedCountry
                "state" -> selectedState
                "city" -> selectedCity
                else -> selectedProvider?.provider?.substringAfter(" • ", selectedProvider?.provider.orEmpty())
            },
            showFlags = picker == "country",
            countryCodes = countryOptions.associate { it.first to it.second },
            onDismiss = { picker = null },
            onSelect = { value ->
                when (picker) {
                    "country" -> { selectedCountry = value; selectedState = null; selectedCity = null; selectedProvider = null }
                    "state" -> { selectedState = value; selectedCity = null; selectedProvider = null }
                    "city" -> {
                        selectedCity = if (value == "عشوائي") cities.randomOrNull() else value
                        selectedProvider = null
                    }
                    "isp" -> {
                        selectedProvider = providers.firstOrNull { it.provider.substringAfter(" • ", it.provider) == value }
                    }
                }
                review = false
                picker = null
            },
        )
    }
}

@Composable
private fun PlanChoiceCards(plans: List<ProxyPurchasePlan>, selected: ProxyPurchasePlan?, onSelect: (ProxyPurchasePlan) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("1. خطة البروكسي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        plans.forEach { plan ->
            val active = selected?.providerPlanId == plan.providerPlanId && selected?.id == plan.id
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(plan) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else KunPalette.Border),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(plan.name, fontWeight = FontWeight.Bold)
                        Text("Proxy-Cheap • Static Residential IPv4", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (active) "✓" else "اختيار", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LocationPickerField(title: String, value: String, placeholder: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else KunPalette.Border),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value.ifBlank { placeholder }, fontWeight = FontWeight.SemiBold, color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
            Text("⌄", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSelectionSheet(
    title: String,
    values: List<String>,
    selected: String?,
    showFlags: Boolean,
    countryCodes: Map<String, String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember(title, values) { mutableStateOf("") }
    val filtered = values.filter { it.contains(query.trim(), ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("بحث") }, singleLine = true, shape = RoundedCornerShape(15.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it }) { value ->
                    Card(Modifier.fillMaxWidth().clickable { onSelect(value) }, shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = if (value == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (showFlags) Text(countryFlag(countryCodes[value].orEmpty()), fontSize = 23.sp, modifier = Modifier.padding(end = 10.dp))
                            Text(value, Modifier.weight(1f), fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal)
                            if (value == selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun countryFlag(code: String): String {
    val normalized = code.trim().uppercase(Locale.US)
    if (normalized.length != 2) return "🌐"
    return normalized.map { char -> String(Character.toChars(0x1F1E6 + char.code - 'A'.code)) }.joinToString("")
}

@Composable
private fun MarketActionCard(title: String, description: String, action: String, highlighted: Boolean = false, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, KunPalette.Border)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onClick, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) { Text(action, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SelectionStep(number: String, title: String, values: List<String>, selected: String?, onSelect: (String) -> Unit) {
    KUNCard { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("$number. $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (values.isEmpty()) Text("لا توجد خيارات متاحة حاليًا.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        values.forEach { value -> FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(value.ifBlank { "متاح" }) }, modifier = Modifier.fillMaxWidth()) }
    } }
}

@Composable
private fun RemoteProxyCard(proxy: RemoteProxy, onSelect: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, KunPalette.Border)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(proxy.location.ifBlank { "بروكسي محفوظ" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FeaturePill(if (proxy.status.equals("active", true)) "نشط" else proxy.status, color = if (proxy.status.equals("active", true)) KunPalette.Success else KunPalette.Warning, container = if (proxy.status.equals("active", true)) KunPalette.SuccessSoft else KunPalette.WarningSoft)
            }
            Text("${proxy.protocol.uppercase()} • ${proxy.host}:${proxy.port}", style = MaterialTheme.typography.bodyMedium)
            if (proxy.expiresAt.isNotBlank()) Text("ينتهي: ${proxy.expiresAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onSelect, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("استخدام هذا البروكسي", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
internal fun Subscriptions(vm: AppViewModel, padding: PaddingValues, onMarket: () -> Unit) {
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
