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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var screen by remember { mutableStateOf(Screen.MARKET) }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }

    if (selectedProduct != null) {
        ProductDetails(selectedProduct!!, onBack = { selectedProduct = null })
        return
    }

    LaunchedEffect(Unit) { vm.loadPlansAndWallet() }
    LaunchedEffect(screen) {
        AdvancedOperationLog.info(context, "App navigation: ${screen.name}.")
        vm.clearError()
        when (screen) {
            Screen.MARKET -> vm.loadProducts()
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
                Screen.MARKET -> Marketplace(vm, padding) { selectedProduct = it }
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
internal fun Marketplace(vm: AppViewModel, padding: PaddingValues, onProductClick: (Product) -> Unit) {
    var featuredOnly by remember { mutableStateOf(false) }
    val visibleProducts = remember(vm.products, featuredOnly) {
        vm.products.filter { !featuredOnly || it.featured }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("المنتجات المتاحة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("المعلومات والأسعار تُحمّل من الخدمة مباشرةً.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilterChip(
                    selected = featuredOnly,
                    onClick = { featuredOnly = !featuredOnly },
                    label = { Text("المميزة") },
                )
            }
        }
        if (vm.productsError != null && vm.products.isNotEmpty()) {
            item {
                StateCard(
                    title = "تعذّر تحديث السوق",
                    message = "نعرض المنتجات الحقيقية التي سبق تحميلها. أعد المحاولة لجلب أحدث البيانات.",
                    error = true,
                    actionLabel = "إعادة المحاولة",
                    onAction = { vm.loadProducts() },
                )
            }
        } else if (vm.productsLoading && vm.products.isEmpty()) {
            item {
                StateCard(
                    title = "جارٍ تحميل السوق",
                    message = "نطلب المنتجات المتاحة من الخدمة. لن نعرض بيانات تجريبية.",
                    loading = true,
                )
            }
        } else if (vm.productsError != null && vm.products.isEmpty()) {
            item {
                StateCard(
                    title = "السوق غير متاح مؤقتًا",
                    message = vm.productsError.orEmpty(),
                    error = true,
                    actionLabel = "إعادة المحاولة",
                    onAction = { vm.loadProducts() },
                )
            }
        } else if (visibleProducts.isEmpty()) {
            item {
                StateCard(
                    title = if (featuredOnly) "لا توجد منتجات مميزة الآن" else "لا توجد منتجات متاحة حاليًا",
                    message = if (featuredOnly) "أوقف التصفية لعرض جميع المنتجات." else "لم تنشر الخدمة منتجات متاحة بعد. ستظهر هنا عند توفر بيانات حقيقية.",
                    actionLabel = if (featuredOnly) "عرض جميع المنتجات" else "تحديث السوق",
                    onAction = { if (featuredOnly) featuredOnly = false else vm.loadProducts() },
                )
            }
        } else {
            items(visibleProducts, key = { it.id }) { product -> ProductCard(product, onProductClick) }
        }
    }
}

@Composable
private fun ProductCard(product: Product, onClick: (Product) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, KunPalette.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(product.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (product.description.isNotBlank()) {
                        Text(product.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (product.featured) FeaturePill("مميز", color = KunPalette.Warning, container = KunPalette.WarningSoft)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (product.protocol.isNotBlank()) FeaturePill(product.protocol)
                if (product.ipType.isNotBlank()) FeaturePill(product.ipType, color = KunPalette.Success, container = KunPalette.SuccessSoft)
            }
            val location = listOf(product.city, product.country).filter { it.isNotBlank() }.distinct().joinToString(" • ")
            if (location.isNotBlank()) Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KunPalette.Background).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("السعر اليومي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(product.daily.ifBlank { "غير محدد" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.width(1.dp).height(34.dp).background(KunPalette.Border))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("السعر الشهري", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(product.monthly.ifBlank { "غير محدد" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            OutlinedButton(onClick = { onClick(product) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                Text("عرض تفاصيل المنتج", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun ProductDetails(product: Product, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(onClick = onBack) { Text("رجوع") }
                    Text(product.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BrandMark(38.dp)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = KunPalette.PrimaryDeep)) {
                    Column(
                        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(KunPalette.PrimaryDeep, KunPalette.Primary, KunPalette.Cyan))).padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        FeaturePill(product.protocol.ifBlank { "بروكسي" }, color = KunPalette.PrimaryDeep, container = Color.White)
                        Text(product.name, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.ExtraBold)
                        if (product.description.isNotBlank()) Text(product.description, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
            item {
                KUNCard {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                        Text("تفاصيل المنتج", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        val location = listOf(product.country, product.city).filter { it.isNotBlank() }.distinct().joinToString(" • ")
                        KeyValueRow("الموقع", location)
                        KeyValueRow("البروتوكول", product.protocol)
                        KeyValueRow("نوع عنوان IP", product.ipType)
                        KeyValueRow("السعر اليومي", product.daily)
                        KeyValueRow("السعر الأسبوعي", product.weekly)
                        KeyValueRow("السعر الشهري", product.monthly)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = KunPalette.WarningSoft), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("الشراء غير متاح حاليًا", color = KunPalette.Warning, fontWeight = FontWeight.Bold)
                        Text("نعرض تفاصيل المنتجات المتاحة من الخدمة فقط. لا يوجد إجراء شراء مفعّل في هذه النسخة.", color = KunPalette.Ink, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(disabledContainerColor = KunPalette.Border, disabledContentColor = KunPalette.Muted)) {
                    Text("الشراء غير متاح حاليًا")
                }
            }
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
private fun WalletCard(vm: AppViewModel) {
    KUNCard { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column { Text("المحفظة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("الرصيد المتاح", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(vm.wallet?.let { "%.2f ${it.currency}".format(Locale.US, it.balance) } ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = KunPalette.Primary)
        }
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("شحن الرصيد — قريبًا") }
        if (vm.walletTransactions.isNotEmpty()) { Text("آخر العمليات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); vm.walletTransactions.take(3).forEach { tx -> KeyValueRow(tx.description, "${if (tx.amount >= 0) "+" else ""}%.2f".format(Locale.US, tx.amount)) } }
    } }
}

@Composable
private fun WalletScreen(vm: AppViewModel, padding: PaddingValues) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading("حسابك", "المحفظة", "إدارة رصيدك ومراجعة جميع العمليات المالية.") }
        item { WalletCard(vm) }
        item { Text("سجل العمليات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (vm.walletTransactions.isEmpty()) item { StateCard("لا توجد عمليات بعد", "ستظهر هنا عمليات الخصم والإضافة عند توفرها.") }
        items(vm.walletTransactions) { tx -> KUNCard { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(tx.description, fontWeight = FontWeight.Bold); Text(readableDate(tx.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text("${if (tx.amount >= 0) "+" else ""}%.2f".format(Locale.US, tx.amount), fontWeight = FontWeight.ExtraBold, color = if (tx.amount >= 0) KunPalette.Success else KunPalette.Error) } } }
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
