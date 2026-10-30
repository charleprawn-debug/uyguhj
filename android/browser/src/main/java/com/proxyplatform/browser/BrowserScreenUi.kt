package com.proxyplatform.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoView

private val BrowserNight = Color(0xFF111A2A)
private val BrowserPanel = Color(0xFF1C2A40)
private val BrowserPanelSoft = Color(0xFF25364D)
private val BrowserCyan = Color(0xFFBDECEF)
private val BrowserWhite = Color(0xFFF5F8FC)
private val BrowserMuted = Color(0xFFA9B6C7)
private val BrowserDialog = Color(0xFF1B293D)

private data class BrowserShortcut(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val container: Color,
    val tint: Color,
    val url: String? = null,
    val action: String = "",
)

@Composable
internal fun BrowserScreen(controller: BrowserController) {
    val activeTab = controller.selectedTab
    val isHome = activeTab == null || activeTab.uri.isBlank() || activeTab.uri == "about:blank"
    var address by remember(activeTab?.id) {
        mutableStateOf(activeTab?.uri?.takeUnless { it.isBlank() || it == "about:blank" }.orEmpty())
    }
    var showBookmarks by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val addressFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(activeTab?.id, activeTab?.uri) {
        address = activeTab?.uri?.takeUnless { it.isBlank() || it == "about:blank" }.orEmpty()
    }
    BackHandler(enabled = activeTab?.canGoBack == true) { controller.goBack() }

    fun submitAddress() {
        if (address.isBlank()) {
            addressFocus.requestFocus()
            keyboard?.show()
        } else {
            controller.goToAddress(address)
            keyboard?.hide()
        }
    }
    fun focusAddress() {
        addressFocus.requestFocus()
        keyboard?.show()
    }

    Surface(Modifier.fillMaxSize(), color = BrowserNight) {
        Column(Modifier.fillMaxSize()) {
            BrowserAddressBar(
                value = address,
                onValueChange = { address = it },
                isHome = isHome,
                isSecure = activeTab?.uri?.startsWith("https://") == true,
                focusRequester = addressFocus,
                onFocus = ::focusAddress,
                onSubmit = ::submitAddress,
                onMenu = { showMenu = true },
            )
            if (activeTab?.loading == true && !isHome) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color(0xFF8BDDE2),
                    trackColor = Color.Transparent,
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    controller.configError != null -> BrowserMessagePanel(
                        title = "تعذّر تشغيل المتصفح",
                        message = "أغلق المتصفح وأعد فتح ملف التصفح. إذا استمرت المشكلة، حدّث التطبيق.",
                        actionLabel = "إعادة المحاولة",
                        onAction = { controller.retryLaunchConfig() },
                    )
                    !controller.connectionReady -> BrowserMessagePanel(
                        title = "اختر ملف تصفح",
                        message = "افتح ملفًا من لوحة KUN Browser لبدء التصفح.",
                        actionLabel = "بحث أو كتابة عنوان",
                        onAction = ::focusAddress,
                    )
                    isHome -> BrowserStartPage(
                        profileName = controller.activeProfileName,
                        directConnection = controller.directConnection,
                        onOpenUrl = { controller.goToAddress(it) },
                        onHistory = { showHistory = true },
                        onBookmarks = { showBookmarks = true },
                    )
                    else -> {
                        AndroidView(
                            modifier = Modifier.fillMaxSize().background(Color.White),
                            factory = { context -> GeckoView(context).apply { setSession(activeTab.session) } },
                        )
                        activeTab.errorMessage?.let { message ->
                            BrowserMessagePanel(
                                title = "تعذّر تحميل الصفحة",
                                message = message,
                                actionLabel = "إعادة المحاولة",
                                onAction = { controller.reload() },
                                secondaryLabel = "العودة للبداية",
                                onSecondary = { controller.goHome() },
                            )
                        }
                        if (activeTab.loading) {
                            CircularProgressIndicator(
                                Modifier.align(Alignment.Center).size(30.dp),
                                color = Color(0xFF8BDDE2),
                                strokeWidth = 2.5.dp,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isHome && controller.connectionReady,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 },
            ) {
                BrowserBottomBar(
                    canGoBack = activeTab?.canGoBack == true,
                    canGoForward = activeTab?.canGoForward == true,
                    tabCount = controller.tabs.size,
                    onBack = { controller.goBack() },
                    onForward = { controller.goForward() },
                    onHome = { controller.goHome(); address = "" },
                    onTabs = { showTabs = true },
                    onMenu = { showMenu = true },
                )
            }
        }
    }

    if (showTabs) {
        AlertDialog(
            onDismissRequest = { showTabs = false },
            shape = RoundedCornerShape(30.dp),
            containerColor = BrowserDialog,
            titleContentColor = BrowserWhite,
            textContentColor = BrowserMuted,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("التبويبات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${controller.tabs.size} مفتوحة", color = BrowserMuted, style = MaterialTheme.typography.labelMedium)
                }
            },
            text = {
                if (controller.tabs.isEmpty()) Text("لا توجد تبويبات مفتوحة.")
                else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    controller.tabs.forEach { tab ->
                        Surface(
                            Modifier.fillMaxWidth().clickable { controller.selectTab(tab.id); showTabs = false },
                            shape = RoundedCornerShape(22.dp),
                            color = if (tab.id == controller.selectedTabId) Color(0xFF2E435B) else BrowserPanel,
                        ) {
                            Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(38.dp).clip(CircleShape).background(if (tab.privateMode) Color(0xFF463C5D) else Color(0xFF29485A)), contentAlignment = Alignment.Center) {
                                    Icon(if (tab.privateMode) Icons.Filled.Warning else Icons.AutoMirrored.Filled.List, contentDescription = null, tint = if (tab.privateMode) Color(0xFFE0C7FF) else BrowserCyan, modifier = Modifier.size(19.dp))
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                                    Text((if (tab.privateMode) "خاص · " else "") + tab.title, color = BrowserWhite, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(tab.uri.takeUnless { it.isBlank() || it == "about:blank" } ?: "صفحة بداية جديدة", color = BrowserMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                                Box(Modifier.size(38.dp).clip(CircleShape).clickable { controller.closeTab(tab.id) }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Close, contentDescription = "إغلاق التبويب", tint = BrowserMuted, modifier = Modifier.size(19.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                DialogAction("تبويب جديد", onClick = { controller.newTab(); showTabs = false }, icon = Icons.Filled.Search)
            },
            dismissButton = { DialogAction("تم", onClick = { showTabs = false }) },
        )
    }

    if (showBookmarks) {
        AlertDialog(
            onDismissRequest = { showBookmarks = false },
            shape = RoundedCornerShape(30.dp),
            containerColor = BrowserDialog,
            titleContentColor = BrowserWhite,
            textContentColor = BrowserMuted,
            title = { Text("المحفوظات", fontWeight = FontWeight.Bold) },
            text = {
                val bookmarks = controller.bookmarks()
                if (bookmarks.isEmpty()) Text("لا توجد صفحات محفوظة بعد.")
                else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    bookmarks.forEach { url -> DialogListRow(url, Icons.Filled.Star) { controller.goToAddress(url); address = url; showBookmarks = false } }
                }
            },
            confirmButton = { DialogAction("تم", onClick = { showBookmarks = false }) },
        )
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            shape = RoundedCornerShape(30.dp),
            containerColor = BrowserDialog,
            titleContentColor = BrowserWhite,
            textContentColor = BrowserMuted,
            title = { Text("سجل التصفح", fontWeight = FontWeight.Bold) },
            text = {
                val history = controller.history()
                if (history.isEmpty()) Text("لا يوجد سجل محفوظ.")
                else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    history.forEach { url -> DialogListRow(url, Icons.Filled.DateRange) { controller.goToAddress(url); address = url; showHistory = false } }
                }
            },
            confirmButton = { DialogAction("مسح السجل", onClick = { controller.clearHistory(); showHistory = false }) },
            dismissButton = { DialogAction("إغلاق", onClick = { showHistory = false }) },
        )
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            shape = RoundedCornerShape(30.dp),
            containerColor = BrowserDialog,
            titleContentColor = BrowserWhite,
            textContentColor = BrowserMuted,
            title = { Text("القائمة", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(22.dp), color = BrowserPanelSoft) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(controller.activeProfileName, color = BrowserWhite, fontWeight = FontWeight.SemiBold)
                            Text(controller.connectionLabel, color = if (controller.directConnection) Color(0xFFFFCA91) else Color(0xFF9DE3CC), style = MaterialTheme.typography.labelSmall)
                            Text("${controller.languageLabel} · ${controller.timeZoneLabel}", color = BrowserMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                    if (!isHome) {
                        MenuAction("رجوع", Icons.Filled.ArrowBack, activeTab?.canGoBack == true) { controller.goBack(); showMenu = false }
                        MenuAction("التالي", Icons.Filled.ArrowForward, activeTab?.canGoForward == true) { controller.goForward(); showMenu = false }
                        MenuAction("الصفحة الرئيسية", Icons.Filled.Home) { controller.goHome(); address = ""; showMenu = false }
                        MenuAction("إعادة تحميل الصفحة", Icons.Filled.Refresh) { controller.reload(); showMenu = false }
                    }
                    MenuAction("تبويب جديد", Icons.Filled.Search) { controller.newTab(); showMenu = false }
                    MenuAction("تبويب خاص جديد", Icons.Filled.Warning) { controller.newTab(privateMode = true); showMenu = false }
                    MenuAction("التبويبات المفتوحة · ${controller.tabs.size}", Icons.AutoMirrored.Filled.List) { showTabs = true; showMenu = false }
                    MenuAction("حفظ الصفحة الحالية", Icons.Filled.Star, !isHome) {
                        feedback = if (controller.addBookmark()) "تم حفظ الصفحة في المحفوظات." else "افتح صفحة ويب أولًا ثم احفظها."
                        showMenu = false
                    }
                    MenuAction("المحفوظات", Icons.Filled.Star) { showBookmarks = true; showMenu = false }
                    MenuAction("سجل التصفح", Icons.Filled.DateRange) { showHistory = true; showMenu = false }
                    MenuAction("مسح بيانات التصفح", Icons.Filled.Close) { controller.clearBrowsingData(); address = ""; showMenu = false }
                    if (controller.directConnection) {
                        Text("الاتصال مباشر بلا بروكسي؛ قد يظهر عنوان اتصالك الحقيقي للمواقع.", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFFFFCA91), style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = { DialogAction("إغلاق", onClick = { showMenu = false }) },
        )
    }

    feedback?.let { message ->
        AlertDialog(
            onDismissRequest = { feedback = null },
            shape = RoundedCornerShape(30.dp),
            containerColor = BrowserDialog,
            titleContentColor = BrowserWhite,
            textContentColor = BrowserMuted,
            title = { Text("KUN Browser") },
            text = { Text(message) },
            confirmButton = { DialogAction("حسنًا", onClick = { feedback = null }) },
        )
    }
}

@Composable
private fun BrowserAddressBar(
    value: String,
    onValueChange: (String) -> Unit,
    isHome: Boolean,
    isSecure: Boolean,
    focusRequester: FocusRequester,
    onFocus: () -> Unit,
    onSubmit: () -> Unit,
    onMenu: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp).height(58.dp),
        shape = CircleShape,
        color = Color(0xFFDFF2F4),
        shadowElevation = 4.dp,
        tonalElevation = 0.dp,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxSize().padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    Modifier.size(43.dp).clickable { if (value.isBlank()) onFocus() else onSubmit() },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = .78f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isSecure) Icons.Filled.Lock else Icons.Filled.Search,
                            contentDescription = if (isSecure) "اتصال مشفر" else "بحث",
                            tint = Color(0xFF31596A),
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp).focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF173243), fontWeight = FontWeight.Medium, textDirection = TextDirection.ContentOrLtr),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isBlank()) Text("ابحث أو اكتب عنوان موقع", color = Color(0xFF66808C), style = MaterialTheme.typography.bodyMedium)
                            innerTextField()
                        }
                    },
                )
                Box(
                    Modifier.size(43.dp).clip(CircleShape).background(Color.White.copy(alpha = .42f)).clickable(onClick = onMenu),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "القائمة", tint = Color(0xFF31596A), modifier = Modifier.size(21.dp))
                }
            }
        }
    }
}

@Composable
private fun BrowserStartPage(
    profileName: String,
    directConnection: Boolean,
    onOpenUrl: (String) -> Unit,
    onHistory: () -> Unit,
    onBookmarks: () -> Unit,
) {
    val shortcuts = listOf(
        BrowserShortcut("بحث Google", Icons.Filled.Search, Color(0xFFF6FAFC), Color(0xFF4285F4), "https://www.google.com/"),
        BrowserShortcut("YouTube", Icons.Filled.PlayArrow, Color(0xFFFF3B45), Color.White, "https://m.youtube.com/"),
        BrowserShortcut("Telegram", Icons.Filled.Send, Color(0xFF229ED9), Color.White, "https://web.telegram.org/"),
        BrowserShortcut("Facebook", Icons.Filled.Face, Color(0xFF1877F2), Color.White, "https://m.facebook.com/"),
        BrowserShortcut("سجل التصفح", Icons.Filled.DateRange, Color(0xFF293B53), Color(0xFFBFEAF0), action = "history"),
        BrowserShortcut("Amazon", Icons.Filled.ShoppingCart, Color(0xFFF6FAFC), Color(0xFF25384B), "https://www.amazon.com/"),
        BrowserShortcut("Apple", Icons.Filled.Phone, Color(0xFF35475E), Color.White, "https://www.apple.com/"),
        BrowserShortcut("المحفوظات", Icons.Filled.Star, Color(0xFF293B53), Color(0xFFFFD27D), action = "bookmarks"),
    )
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF111A2A), Color(0xFF182B40), Color(0xFF10202F))))) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 105.dp, y = (-75).dp).size(250.dp).background(Brush.radialGradient(listOf(Color(0xFF70DCE0).copy(alpha = .30f), Color.Transparent)), CircleShape))
        Box(Modifier.align(Alignment.BottomStart).offset(x = (-110).dp, y = 100.dp).size(270.dp).background(Brush.radialGradient(listOf(Color(0xFF45799B).copy(alpha = .25f), Color.Transparent)), CircleShape))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(profileName.take(26), color = BrowserWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("ملف التصفح الحالي", color = BrowserMuted, style = MaterialTheme.typography.labelSmall)
                }
                Surface(shape = CircleShape, color = if (directConnection) Color(0xFF59412E) else Color(0xFF1F514A)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(7.dp).background(if (directConnection) Color(0xFFFFC078) else Color(0xFF7BE0C2), CircleShape))
                        Text(if (directConnection) "مباشر" else "عبر البروكسي", color = if (directConnection) Color(0xFFFFD4A7) else Color(0xFFBEF5E4), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(23.dp)) {
                    shortcuts.chunked(4).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
                            rowItems.forEach { shortcut ->
                                HomeShortcut(
                                    modifier = Modifier.weight(1f),
                                    shortcut = shortcut,
                                    onClick = {
                                        when (shortcut.action) {
                                            "history" -> onHistory()
                                            "bookmarks" -> onBookmarks()
                                            else -> shortcut.url?.let(onOpenUrl)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (directConnection) {
                Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF4B3929).copy(alpha = .80f)) {
                    Text(
                        "الاتصال مباشر بلا بروكسي؛ قد يظهر عنوان اتصالك الحقيقي للمواقع.",
                        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 11.dp),
                        color = Color(0xFFFFD4A7),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeShortcut(modifier: Modifier, shortcut: BrowserShortcut, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Surface(
            Modifier.size(58.dp),
            shape = CircleShape,
            color = shortcut.container,
            shadowElevation = 3.dp,
            tonalElevation = 1.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(shortcut.icon, contentDescription = null, tint = shortcut.tint, modifier = Modifier.size(26.dp))
            }
        }
        Text(shortcut.title, color = BrowserWhite.copy(alpha = .93f), style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BrowserBottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 9.dp).height(66.dp),
        shape = CircleShape,
        color = BrowserPanel.copy(alpha = .97f),
        shadowElevation = 7.dp,
        tonalElevation = 0.dp,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                BrowserNavButton(Icons.Filled.ArrowBack, "رجوع", canGoBack, onBack)
                BrowserNavButton(Icons.Filled.ArrowForward, "التالي", canGoForward, onForward)
                BrowserNavButton(Icons.Filled.Home, "الرئيسية", true, onHome, selected = true)
                TabSwitcherButton(tabCount, onTabs)
                BrowserNavButton(Icons.Filled.Menu, "القائمة", true, onMenu)
            }
        }
    }
}

@Composable
private fun BrowserNavButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit, selected: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(19.dp),
        color = if (selected) BrowserCyan else Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = when { selected -> Color(0xFF173B4B); enabled -> BrowserWhite; else -> Color(0xFF66778B) }, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun TabSwitcherButton(tabCount: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(50.dp),
        shape = RoundedCornerShape(19.dp),
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            TabStackGlyph(Modifier.size(24.dp), BrowserWhite)
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = (-3).dp, y = 3.dp).defaultMinSize(minWidth = 17.dp, minHeight = 17.dp).clip(CircleShape).background(BrowserCyan).padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (tabCount > 9) "9+" else tabCount.toString(), color = Color(0xFF173B4B), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TabStackGlyph(modifier: Modifier, tint: Color) {
    Canvas(modifier) {
        val strokeWidth = 1.8.dp.toPx()
        val corner = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * .22f, size.height * .12f),
            size = androidx.compose.ui.geometry.Size(size.width * .66f, size.height * .68f),
            cornerRadius = corner,
            style = Stroke(width = strokeWidth),
        )
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * .12f, size.height * .22f),
            size = androidx.compose.ui.geometry.Size(size.width * .66f, size.height * .68f),
            cornerRadius = corner,
            style = Stroke(width = strokeWidth),
        )
    }
}

@Composable
private fun MenuAction(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) BrowserPanel else BrowserPanel.copy(alpha = .45f),
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = if (enabled) BrowserCyan else BrowserMuted.copy(alpha = .5f), modifier = Modifier.size(19.dp))
            Text(label, color = if (enabled) BrowserWhite else BrowserMuted.copy(alpha = .5f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DialogListRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = BrowserPanel) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = BrowserCyan, modifier = Modifier.size(18.dp))
            Text(label, color = BrowserWhite, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
private fun DialogAction(label: String, onClick: () -> Unit, icon: ImageVector? = null) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = BrowserCyan), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(18.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BrowserMessagePanel(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF15263B), Color(0xFF101827)))), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Surface(Modifier.size(62.dp), shape = CircleShape, color = BrowserPanelSoft) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Warning, contentDescription = null, tint = BrowserCyan, modifier = Modifier.size(27.dp)) }
            }
            Text(title, color = BrowserWhite, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(message, color = BrowserMuted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Button(
                onClick = onAction,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = BrowserCyan, contentColor = Color(0xFF18354A)),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
            ) { Text(actionLabel, fontWeight = FontWeight.Bold) }
            if (secondaryLabel != null && onSecondary != null) {
                TextButton(onClick = onSecondary, colors = ButtonDefaults.textButtonColors(contentColor = BrowserCyan)) { Text(secondaryLabel) }
            }
        }
    }
}
