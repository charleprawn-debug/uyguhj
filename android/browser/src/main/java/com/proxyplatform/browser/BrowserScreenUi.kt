package com.proxyplatform.browser

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoView

private val BrowserNight = Color(0xFF111B2B)
private val BrowserNightSoft = Color(0xFF1B2A40)
private val BrowserCyan = Color(0xFFBDECEF)
private val BrowserWhite = Color(0xFFF4F8FC)
private val BrowserMuted = Color(0xFF9EACC0)

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
    fun focusSearch() {
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
                onSubmit = ::submitAddress,
                onMenu = { showMenu = true },
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    controller.configError != null -> BrowserMessagePanel(
                        title = "تعذّر تشغيل المتصفح",
                        message = "أغلق المتصفح وأعد فتح ملف التصفح. إذا استمرت المشكلة، حدّث التطبيق.",
                        actionLabel = "الصفحة الرئيسية",
                        onAction = { controller.goHome() },
                    )
                    !controller.connectionReady -> BrowserMessagePanel(
                        title = "اختر ملف تصفح",
                        message = "افتح ملفًا من لوحة KUN Browser لبدء التصفح.",
                        actionLabel = "بحث أو كتابة عنوان",
                        onAction = ::focusSearch,
                    )
                    isHome -> BrowserStartPage(
                        profileName = controller.activeProfileName,
                        directConnection = controller.directConnection,
                        connectionLabel = controller.connectionLabel,
                        onSearch = ::focusSearch,
                        onOpenUrl = { url -> controller.goToAddress(url) },
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
                                Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(24.dp),
                                color = Color(0xFF41C6D2),
                                strokeWidth = 2.5.dp,
                            )
                        }
                    }
                }
            }

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

    if (showTabs) {
        AlertDialog(
            onDismissRequest = { showTabs = false },
            title = { Text("التبويبات المفتوحة") },
            text = {
                if (controller.tabs.isEmpty()) Text("لا توجد تبويبات مفتوحة.")
                else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    controller.tabs.forEach { tab ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { controller.selectTab(tab.id); showTabs = false }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text((if (tab.privateMode) "خاص · " else "") + tab.title, maxLines = 1, color = Color(0xFF17263B), fontWeight = FontWeight.SemiBold)
                                    Text(tab.uri.takeUnless { it.isBlank() || it == "about:blank" } ?: "صفحة بداية جديدة", maxLines = 1, style = MaterialTheme.typography.labelSmall, color = Color(0xFF718096))
                                }
                            }
                            TextButton(onClick = { controller.closeTab(tab.id) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("×") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { controller.newTab(); showTabs = false }) { Text("تبويب جديد +") } },
            dismissButton = { TextButton(onClick = { showTabs = false }) { Text("إغلاق") } },
        )
    }

    if (showBookmarks) {
        AlertDialog(
            onDismissRequest = { showBookmarks = false },
            title = { Text("المحفوظات") },
            text = {
                val bookmarks = controller.bookmarks()
                if (bookmarks.isEmpty()) Text("لا توجد صفحات محفوظة بعد.")
                else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    bookmarks.forEach { url -> TextButton(onClick = { controller.goToAddress(url); address = url; showBookmarks = false }) { Text(url, maxLines = 1) } }
                }
            },
            confirmButton = { TextButton(onClick = { showBookmarks = false }) { Text("تم") } },
        )
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("سجل التصفح") },
            text = {
                val history = controller.history()
                if (history.isEmpty()) Text("لا يوجد سجل محفوظ.")
                else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    history.forEach { url -> TextButton(onClick = { controller.goToAddress(url); address = url; showHistory = false }) { Text(url, maxLines = 1) } }
                }
            },
            confirmButton = { TextButton(onClick = { controller.clearHistory(); showHistory = false }) { Text("مسح السجل") } },
            dismissButton = { TextButton(onClick = { showHistory = false }) { Text("إغلاق") } },
        )
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text("خيارات المتصفح") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(controller.connectionLabel, color = if (controller.directConnection) Color(0xFFB45A18) else Color(0xFF16866F), style = MaterialTheme.typography.bodySmall)
                    Text("اللغة: ${controller.languageLabel}  •  الوقت: ${controller.timeZoneLabel}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { controller.newTab(); showMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("تبويب جديد") }
                    OutlinedButton(onClick = { controller.newTab(privateMode = true); showMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("تبويب خاص جديد") }
                    OutlinedButton(onClick = { showTabs = true; showMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("التبويبات المفتوحة (${controller.tabs.size})") }
                    OutlinedButton(onClick = { feedback = if (controller.addBookmark()) "تم حفظ الصفحة في المحفوظات." else "افتح صفحة ويب أولًا ثم احفظها."; showMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("حفظ الصفحة الحالية") }
                    OutlinedButton(onClick = { showBookmarks = true; showMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("المحفوظات") }
                    OutlinedButton(onClick = { showHistory = true; showMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("سجل التصفح") }
                    OutlinedButton(onClick = { controller.clearBrowsingData(); showMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("مسح بيانات التصفح") }
                    Text("حالة الاتصال واضحة في الصفحة الرئيسية. الاتصال المباشر لا يستخدم بروكسي.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF718096))
                }
            },
            confirmButton = { TextButton(onClick = { showMenu = false }) { Text("إغلاق") } },
        )
    }

    if (feedback != null) {
        AlertDialog(onDismissRequest = { feedback = null }, title = { Text("KUN Browser") }, text = { Text(feedback.orEmpty()) }, confirmButton = { TextButton(onClick = { feedback = null }) { Text("حسنًا") } })
    }
}

@Composable
private fun BrowserAddressBar(
    value: String,
    onValueChange: (String) -> Unit,
    isHome: Boolean,
    isSecure: Boolean,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
    onMenu: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp).height(54.dp),
        shape = RoundedCornerShape(28.dp),
        color = BrowserCyan,
        tonalElevation = 4.dp,
    ) {
        Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (isHome) "⌕" else if (isSecure) "▣" else "◉", color = Color(0xFF294A65), fontSize = 19.sp, fontWeight = FontWeight.Bold)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).padding(horizontal = 11.dp).focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF142536), fontWeight = FontWeight.Medium, textDirection = TextDirection.ContentOrLtr),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) Text("ابحث أو اكتب عنوان URL", color = Color(0xFF607787), style = MaterialTheme.typography.bodyMedium)
                        innerTextField()
                    }
                },
            )
            TextButton(onClick = onSubmit, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)) {
                Text("↵", color = Color(0xFF294A65), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onMenu, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("•••", color = Color(0xFF294A65), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private data class BrowserShortcut(val title: String, val glyph: String, val color: Color, val url: String? = null, val action: String = "")

@Composable
private fun BrowserStartPage(
    profileName: String,
    directConnection: Boolean,
    connectionLabel: String,
    onSearch: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onHistory: () -> Unit,
    onBookmarks: () -> Unit,
) {
    val shortcuts = listOf(
        BrowserShortcut("بحث Google", "G", Color.White, "https://www.google.com/"),
        BrowserShortcut("YouTube", "▶", Color(0xFFE64646), "https://m.youtube.com/"),
        BrowserShortcut("Telegram", "➤", Color(0xFF2B9FD9), "https://web.telegram.org/"),
        BrowserShortcut("Facebook", "f", Color(0xFF3768B5), "https://m.facebook.com/"),
        BrowserShortcut("سجل التصفح", "◷", Color(0xFF344B66), action = "history"),
        BrowserShortcut("Amazon", "a", Color.White, "https://www.amazon.com/"),
        BrowserShortcut("Apple", "●", Color(0xFF48566B), "https://www.apple.com/"),
        BrowserShortcut("المحفوظات", "☆", Color(0xFF344B66), action = "bookmarks"),
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF15263B), Color(0xFF101827), Color(0xFF17283A))))) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 100.dp, y = (-65).dp).size(250.dp).background(Color(0xFF63D8D6).copy(alpha = .20f), CircleShape))
        Box(Modifier.align(Alignment.BottomStart).offset(x = (-105).dp, y = 75.dp).size(260.dp).background(Color(0xFF376E92).copy(alpha = .20f), CircleShape))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 21.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(21.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("KUN Browser", color = BrowserWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("مساحة تصفحك", color = BrowserMuted, style = MaterialTheme.typography.labelSmall)
                }
                Surface(shape = RoundedCornerShape(20.dp), color = if (directConnection) Color(0xFFFFE2C5) else Color(0xFF1B695F)) {
                    Text(
                        if (directConnection) "مباشر · بلا بروكسي" else "●  متصل بالبروكسي",
                        Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        color = if (directConnection) Color(0xFF8A4B08) else Color(0xFFD8FFF3),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("مرحبًا، ${profileName.take(24)}", color = BrowserWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("افتح موقعًا أو ابدأ البحث من هنا", color = BrowserMuted, style = MaterialTheme.typography.bodyMedium)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                shortcuts.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { shortcut ->
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
            Surface(
                Modifier.fillMaxWidth().height(58.dp).clickable(onClick = onSearch),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF27364A),
                tonalElevation = 2.dp,
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Google", color = Color(0xFFBDECEF), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("ابحث في الويب", color = BrowserMuted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.size(11.dp))
                    Text("⌕", color = BrowserCyan, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
            }
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .07f)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("ملفك", color = BrowserMuted, style = MaterialTheme.typography.labelSmall)
                    Text("  $profileName", color = BrowserWhite, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(connectionLabel, color = if (directConnection) Color(0xFFFFC48D) else Color(0xFF9DE3CC), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
            if (directConnection) {
                Text("الاتصال المباشر لا يستخدم بروكسي؛ قد ترى المواقع عنوان اتصالك الحقيقي.", color = Color(0xFFFFC48D), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun HomeShortcut(modifier: Modifier, shortcut: BrowserShortcut, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Surface(Modifier.size(55.dp), shape = RoundedCornerShape(20.dp), color = shortcut.color, tonalElevation = 3.dp) {
            Box(contentAlignment = Alignment.Center) {
                Text(shortcut.glyph, color = if (shortcut.color == Color.White) Color(0xFF334968) else Color.White, fontSize = if (shortcut.glyph == "▶") 20.sp else 24.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Text(shortcut.title, color = BrowserWhite.copy(alpha = .91f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).height(62.dp),
        shape = RoundedCornerShape(24.dp),
        color = BrowserNightSoft,
        tonalElevation = 5.dp,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BrowserNavButton(Modifier.weight(1f), "‹", "رجوع", canGoBack, false, onBack)
            BrowserNavButton(Modifier.weight(1f), "›", "التالي", canGoForward, false, onForward)
            BrowserNavButton(Modifier.weight(1f), "⌂", "الرئيسية", true, true, onHome)
            BrowserNavButton(Modifier.weight(1f), "▢ $tabCount", "التبويبات", true, false, onTabs)
            BrowserNavButton(Modifier.weight(1f), "☰", "القائمة", true, false, onMenu)
        }
    }
}

@Composable
private fun BrowserNavButton(modifier: Modifier, glyph: String, label: String, enabled: Boolean, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 3.dp).background(
            color = if (selected) BrowserCyan else Color.Transparent,
            shape = RoundedCornerShape(15.dp),
        ).clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(glyph, color = when { selected -> Color(0xFF1B3A4B); enabled -> BrowserWhite; else -> Color(0xFF65748A) }, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = if (selected) Color(0xFF1B3A4B) else BrowserMuted, fontSize = 9.sp, maxLines = 1)
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
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.size(58.dp), shape = CircleShape, color = Color(0xFF25374D)) { Box(contentAlignment = Alignment.Center) { Text("!", color = BrowserCyan, fontSize = 28.sp, fontWeight = FontWeight.Bold) } }
            Text(title, color = BrowserWhite, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(message, color = BrowserMuted, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAction, shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = BrowserCyan, contentColor = Color(0xFF18354A))) {
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
            if (secondaryLabel != null && onSecondary != null) {
                TextButton(onClick = onSecondary) { Text(secondaryLabel, color = BrowserCyan) }
            }
        }
    }
}
