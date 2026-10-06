package com.proxyplatform.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.proxyplatform.app.adb.PairingCodeInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SimScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val controller = remember(context) { SimOperatorController(context) }
    val scope = rememberCoroutineScope()
    var adbState by remember { mutableStateOf(WirelessDebuggingManager.DebuggingState.NOT_PAIRED) }
    var selectedProfile by remember { mutableStateOf(SimOperatorProfiles.all.first()) }
    var pairingCode by remember { mutableStateOf("") }
    var airplaneMode by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var logs by remember { mutableStateOf(SimOperationLog.read(context)) }
    val logScrollState = rememberScrollState()
    val connected = adbState == WirelessDebuggingManager.DebuggingState.READY
    val pendingRestore = controller.hasPendingRestore

    LaunchedEffect(logs) {
        logScrollState.animateScrollTo(logScrollState.maxValue)
    }

    fun refreshLogs() {
        logs = SimOperationLog.read(context)
    }

    fun refreshConnection() {
        scope.launch {
            busy = true
            adbState = withContext(Dispatchers.IO) { WirelessDebuggingManager.checkState(context) }
            airplaneMode = withContext(Dispatchers.IO) {
                runCatching {
                    Settings.Global.getInt(
                        context.contentResolver,
                        Settings.Global.AIRPLANE_MODE_ON,
                        0,
                    ) == 1
                }.getOrDefault(false)
            }
            busy = false
            refreshLogs()
        }
    }

    LaunchedEffect(Unit) {
        if (controller.activeProfileId != null) {
            SimOperatorProfiles.find(controller.activeProfileId)?.let { selectedProfile = it }
        }
        SimOperationLog.info(context, "تم فتح صفحة sim.")
        refreshConnection()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("اختبار خصائص المشغّل", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(
                    "يتصل عبر Wireless ADB ويحاول تغيير خصائص gsm.* المؤقتة فقط.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            NoticeCard(
                title = "حدود مهمة قبل التشغيل",
                body = "هذه الأوامر لا تغيّر IMSI أو ICCID أو بيانات الشريحة أو تسجيلها على الشبكة. خصائص gsm.* محمية ويتحكم بها Android/Telephony، لذلك قد يرفض setprop عبر Wireless ADB العادي؛ عندها سيتوقف الاختبار قبل حفظ جلسة استعادة. يلزم أيضاً تفعيل وضع الطيران يدوياً وإعادة تشغيل Wi‑Fi مع إبقاءه فعالاً حتى يبقى ADB متصلاً.",
                color = KunPalette.WarningSoft,
                textColor = KunPalette.Ink,
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Wireless ADB", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                adbStateLabel(adbState),
                                color = if (connected) KunPalette.Success else KunPalette.Warning,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = ::refreshConnection, enabled = !busy) { Text("تحديث") }
                    }

                    if (!connected) {
                        Text(
                            "فعّل خيارات المطوّر وWireless debugging، ثم افتح Pair device with pairing code وأدخل الرمز هنا.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(WirelessDebuggingManager.getDebuggingSettingsIntent(context))
                                }.onFailure {
                                    message = "تعذر فتح خيارات المطوّر: ${it.message}"
                                    SimOperationLog.error(context, message.orEmpty())
                                    refreshLogs()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("فتح خيارات المطوّر") }

                        when (adbState) {
                            WirelessDebuggingManager.DebuggingState.PAIRED_NOT_CONNECTED -> {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            SimOperationLog.info(context, "طلب إعادة اتصال Wireless ADB من صفحة SIM.")
                                            val success = withContext(Dispatchers.IO) {
                                                WirelessDebuggingManager.connect(context, 0)
                                            }
                                            adbState = withContext(Dispatchers.IO) {
                                                WirelessDebuggingManager.checkState(context)
                                            }
                                            message = if (success) "أُعيد الاتصال بنجاح." else "تعذرت إعادة الاتصال. تحقق من Wi‑Fi وWireless debugging."
                                            busy = false
                                            refreshLogs()
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("إعادة الاتصال") }
                            }
                            WirelessDebuggingManager.DebuggingState.NOT_PAIRED -> {
                                OutlinedTextField(
                                    value = pairingCode,
                                    onValueChange = { pairingCode = PairingCodeInput.digitsOnly(it).take(6) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("رمز الاقتران — 6 أرقام") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                Button(
                                    onClick = {
                                        val code = PairingCodeInput.normalize(pairingCode) ?: return@Button
                                        scope.launch {
                                            busy = true
                                            pairingCode = ""
                                            SimOperationLog.info(context, "بدء اقتران Wireless ADB من صفحة SIM؛ رمز الاقتران غير مسجل.")
                                            val result = withContext(Dispatchers.IO) {
                                                WirelessDebuggingManager.pair(context, code)
                                            }
                                            adbState = withContext(Dispatchers.IO) {
                                                WirelessDebuggingManager.checkState(context)
                                            }
                                            message = result.fold(
                                                { "اكتمل اقتران Wireless ADB." },
                                                { "فشل الاقتران: ${it.message ?: it.javaClass.simpleName}" },
                                            )
                                            busy = false
                                            refreshLogs()
                                        }
                                    },
                                    enabled = !busy && PairingCodeInput.normalize(pairingCode) != null,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Text("اقتران واتصال")
                                }
                            }
                            else -> Unit
                        }
                    } else {
                        Text(
                            "الجهاز مقترن ومتصل. يمكن للصفحة تنفيذ الأوامر محلياً على هذا الجهاز فقط.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("الدولة / ملف المشغّل", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    SimOperatorProfiles.all.forEach { profile ->
                        FilterChip(
                            selected = selectedProfile.id == profile.id,
                            onClick = {
                                if (!pendingRestore) {
                                    selectedProfile = profile
                                    message = null
                                }
                            },
                            label = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(profile.label, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "MCC/MNC ${profile.operatorNumeric} • ISO ${profile.isoCountry}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            },
                            enabled = !pendingRestore,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (airplaneMode) KunPalette.SuccessSoft else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(
                        if (airplaneMode) "وضع الطيران مفعّل" else "وضع الطيران غير مفعّل",
                        fontWeight = FontWeight.Bold,
                        color = if (airplaneMode) KunPalette.Success else KunPalette.Warning,
                    )
                    Text(
                        "فعّله من إعدادات Android بنفسك. إذا أوقف وضع الطيران Wi‑Fi، أعد تشغيل Wi‑Fi قبل تنفيذ الأوامر حتى يبقى اتصال ADB قائماً.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = ::refreshConnection, enabled = !busy) {
                        Text("تحقق من حالة وضع الطيران وADB")
                    }
                }
            }
        }
        if (pendingRestore) {
            item {
                NoticeCard(
                    title = "هناك جلسة تنتظر الاستعادة",
                    body = "الملف المحفوظ: ${SimOperatorProfiles.find(controller.activeProfileId)?.label ?: "غير معروف"}. تبقى النسخة الأصلية على الجهاز حتى نجاح الاستعادة والتحقق منها.",
                    color = KunPalette.WarningSoft,
                    textColor = KunPalette.Ink,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (pendingRestore) {
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                val result = withContext(Dispatchers.IO) { controller.restore() }
                                message = result.fold(
                                    { "اكتملت الاستعادة والتحقق من القيم الأصلية." },
                                    { "تعذرت الاستعادة: ${it.message ?: it.javaClass.simpleName}" },
                                )
                                busy = false
                                refreshLogs()
                            }
                        },
                        enabled = connected && !busy,
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KunPalette.Success),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("إيقاف واستعادة الأصل", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                message = null
                                val result = withContext(Dispatchers.IO) {
                                    controller.apply(selectedProfile)
                                }
                                message = result.fold(
                                    { "نجح التغيير المؤقت وتم التحقق من الخصائص. استخدم زر الاستعادة عند الانتهاء." },
                                    { "لم يكتمل التغيير: ${it.message ?: it.javaClass.simpleName}" },
                                )
                                busy = false
                                refreshLogs()
                            }
                        },
                        enabled = connected && airplaneMode && !busy,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("تشغيل اختبار ${selectedProfile.isoCountry.uppercase()}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        message?.let { text ->
            item { InlineMessage(text) }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("سجل عمليات SIM", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${logs.size} سجل محفوظ محلياً", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = ::refreshLogs) { Text("تحديث") }
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    if (clipboard != null && logs.isNotEmpty()) {
                                        clipboard.setPrimaryClip(ClipData.newPlainText("SIM operation log", simLogTranscript(logs)))
                                        Toast.makeText(context, "تم نسخ السجل", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = logs.isNotEmpty(),
                            ) { Text("نسخ") }
                        }
                    }
                    if (logs.isEmpty()) {
                        Text("ستظهر هنا حالة الاقتران، أوامر ADB، ردود النظام، والتحقق أو سبب الخطأ.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(
                            Modifier.fillMaxWidth()
                                .height(320.dp)
                                .background(Color(0xFF101510), RoundedCornerShape(14.dp))
                                .verticalScroll(logScrollState)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            logs.takeLast(80).forEach { entry ->
                                val lineColor = when (entry.level) {
                                    "ERR" -> Color(0xFFFF7777)
                                    "CMD" -> Color(0xFF9BE9A8)
                                    "OUT" -> Color(0xFFD6DED6)
                                    else -> Color(0xFF8CC8FF)
                                }
                                val command = entry.message.removePrefix("$ ")
                                    .replace("\\n", "\n")
                                    .replace("\\r", "\r")
                                val prompt = when (entry.level) {
                                    "CMD" -> "adb$ $command"
                                    "ERR" -> "ERROR  $command"
                                    "OUT" -> command
                                    else -> "INFO   $command"
                                }
                                Text(
                                    "[${entry.timestamp}] $prompt",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = lineColor,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(title: String, body: String, color: Color, textColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, color = textColor)
            Text(body, style = MaterialTheme.typography.bodySmall, color = textColor)
        }
    }
}

@Composable
private fun InlineMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            message,
            Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Start,
        )
    }
}

private fun adbStateLabel(state: WirelessDebuggingManager.DebuggingState): String = when (state) {
    WirelessDebuggingManager.DebuggingState.UNSUPPORTED_ANDROID_VERSION -> "يلزم Android 11 أو أحدث"
    WirelessDebuggingManager.DebuggingState.DEVELOPER_DISABLED -> "خيارات المطوّر غير مفعّلة"
    WirelessDebuggingManager.DebuggingState.WIRELESS_DISABLED -> "Wireless debugging غير مفعّل"
    WirelessDebuggingManager.DebuggingState.NOT_PAIRED -> "غير مقترن"
    WirelessDebuggingManager.DebuggingState.PAIRED_NOT_CONNECTED -> "مقترن، لكن الاتصال غير نشط"
    WirelessDebuggingManager.DebuggingState.READY -> "متصل وجاهز"
}

private fun simLogTranscript(entries: List<SimLogEntry>): String = entries.joinToString("\n") { entry ->
    val message = entry.message.replace("\\n", "\n").replace("\\r", "\r")
    "[${entry.timestamp}] [${entry.level}] $message"
}
