package com.proxyplatform.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proxyplatform.app.adb.PairingCodeInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SIM_UI_PREFS = "sim_nrfr_ui"
private const val PREF_SELECTED_SUB_ID = "selected_sub_id"
private const val PREF_COUNTRY_CODE = "country_code"
private const val PREF_CUSTOM_COUNTRY_CODE = "custom_country_code"
private const val PREF_IS_CUSTOM_COUNTRY = "is_custom_country"
private const val PREF_CARRIER_ID = "carrier_id"
private const val PREF_CUSTOM_CARRIER = "custom_carrier"

@Composable
internal fun SimScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val controller = remember(context) { SimOperatorController(context) }
    val uiPrefs = remember(context) { context.getSharedPreferences(SIM_UI_PREFS, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    var adbState by remember { mutableStateOf(WirelessDebuggingManager.DebuggingState.NOT_PAIRED) }
    var simCards by remember { mutableStateOf(controller.readSimCards()) }
    var selectedSubId by remember { mutableIntStateOf(uiPrefs.getInt(PREF_SELECTED_SUB_ID, -1)) }
    var selectedCountryCode by remember { mutableStateOf(uiPrefs.getString(PREF_COUNTRY_CODE, "").orEmpty()) }
    var customCountryCode by remember { mutableStateOf(uiPrefs.getString(PREF_CUSTOM_COUNTRY_CODE, "").orEmpty()) }
    var isCustomCountryCode by remember { mutableStateOf(uiPrefs.getBoolean(PREF_IS_CUSTOM_COUNTRY, false)) }
    var selectedCarrierId by remember { mutableStateOf(uiPrefs.getString(PREF_CARRIER_ID, "").orEmpty()) }
    var customCarrierName by remember { mutableStateOf(uiPrefs.getString(PREF_CUSTOM_CARRIER, "").orEmpty()) }
    var countryMenuExpanded by remember { mutableStateOf(false) }
    var carrierMenuExpanded by remember { mutableStateOf(false) }
    var pairingCode by remember { mutableStateOf("") }
    var airplaneMode by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var logs by remember { mutableStateOf(SimOperationLog.read(context)) }
    val logScrollState = rememberScrollState()
    val connected = adbState == WirelessDebuggingManager.DebuggingState.READY
    val selectedSim = simCards.firstOrNull { it.subscriptionId == selectedSubId } ?: simCards.firstOrNull()
    val selectedCarrier = SimNrfrPresets.carriers.firstOrNull { it.id == selectedCarrierId }

    fun persistUiState() {
        uiPrefs.edit()
            .putInt(PREF_SELECTED_SUB_ID, selectedSubId)
            .putString(PREF_COUNTRY_CODE, selectedCountryCode)
            .putString(PREF_CUSTOM_COUNTRY_CODE, customCountryCode)
            .putBoolean(PREF_IS_CUSTOM_COUNTRY, isCustomCountryCode)
            .putString(PREF_CARRIER_ID, selectedCarrierId)
            .putString(PREF_CUSTOM_CARRIER, customCarrierName)
            .apply()
    }

    fun refreshLogs() {
        logs = SimOperationLog.read(context)
    }

    suspend fun queueSimRefresh(): Boolean {
        val result = withContext(Dispatchers.IO) { controller.refreshSimCards() }
        result.onSuccess {
            simCards = controller.readSimCards()
            message = "تم تحديث قائمة الشرائح وقراءة CarrierConfig عبر Wireless ADB المضمّن."
        }.onFailure {
            message = "تعذّر تحديث الشرائح: ${it.message ?: it.javaClass.simpleName}"
        }
        refreshLogs()
        return result.isSuccess
    }

    fun refreshConnection() {
        scope.launch {
            busy = true
            val state = withContext(Dispatchers.IO) { WirelessDebuggingManager.checkState(context) }
            adbState = state
            airplaneMode = withContext(Dispatchers.IO) {
                runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 }
                    .getOrDefault(false)
            }
            simCards = controller.readSimCards()
            if (state == WirelessDebuggingManager.DebuggingState.READY && !controller.hasSimCache && !controller.operationPending) {
                queueSimRefresh()
            }
            controller.consumeOperationMessage()?.let { message = it }
            busy = false
            refreshLogs()
        }
    }

    fun waitForOperationResult() {
        scope.launch {
            controller.consumeOperationMessage()?.let {
                message = it
                simCards = controller.readSimCards()
                refreshLogs()
                return@launch
            }
            repeat(60) {
                delay(500)
                controller.consumeOperationMessage()?.let {
                    message = it
                    simCards = controller.readSimCards()
                    refreshLogs()
                    return@launch
                }
            }
        }
    }

    fun clearNrfrSelections() {
        selectedCountryCode = ""
        customCountryCode = ""
        isCustomCountryCode = false
        selectedCarrierId = ""
        customCarrierName = ""
        persistUiState()
    }

    LaunchedEffect(logs) { logScrollState.animateScrollTo(logScrollState.maxValue) }

    LaunchedEffect(Unit) {
        simCards = controller.readSimCards()
        if (selectedSubId !in simCards.map { it.subscriptionId }) {
            selectedSubId = simCards.firstOrNull()?.subscriptionId ?: -1
            persistUiState()
        }
        controller.consumeOperationMessage()?.let { message = it }
        if (controller.operationPending) message = "عملية SIM قيد التنفيذ؛ انتظر اكتمال Instrumentation عبر ADB المضمّن."
        SimOperationLog.info(context, "فتح صفحة SIM؛ NRFR carrier-config API عبر Wireless ADB المدمج.")
        refreshConnection()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("إعداد CarrierConfig للشريحة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(
                    "نفس ملفات وخيارات NRFR: رمز بلد ISO واسم المشغّل، باستخدام ICarrierConfigLoader عبر Wireless ADB.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            NoticeCard(
                title = "حدود التغيير",
                body = "هذا هو نفس مسار NRFR: يحفظ CarrierConfig override بشكل persistent إلى أن تستخدم إعادة التعيين. يغيّر ISO البلد واسم المشغّل فقط؛ لا يغيّر IMSI أو ICCID أو MCC/MNC أو الشبكة الفعلية. لا يحتاج Shizuku أو تطبيق Helper، لكن قد يعيد التطبيق فتح نفسه بعد كل عملية.",
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
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Wireless ADB المدمج", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(adbStateLabel(adbState), color = if (connected) KunPalette.Success else KunPalette.Warning, style = MaterialTheme.typography.bodySmall)
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
                                runCatching { context.startActivity(WirelessDebuggingManager.getDebuggingSettingsIntent(context)) }
                                    .onFailure {
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
                                            val success = withContext(Dispatchers.IO) { WirelessDebuggingManager.connect(context, 0) }
                                            adbState = withContext(Dispatchers.IO) { WirelessDebuggingManager.checkState(context) }
                                            message = if (success) "أُعيد الاتصال بنجاح." else "تعذرت إعادة الاتصال؛ تحقق من Wi‑Fi وWireless debugging."
                                            if (adbState == WirelessDebuggingManager.DebuggingState.READY && !controller.hasSimCache) queueSimRefresh()
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
                                            SimOperationLog.info(context, "بدء اقتران Wireless ADB؛ رمز الاقتران غير مسجل.")
                                            val result = withContext(Dispatchers.IO) { WirelessDebuggingManager.pair(context, code) }
                                            adbState = withContext(Dispatchers.IO) { WirelessDebuggingManager.checkState(context) }
                                            message = result.fold({ "اكتمل اقتران Wireless ADB." }, { "فشل الاقتران: ${it.message ?: it.javaClass.simpleName}" })
                                            if (result.isSuccess && !controller.hasSimCache) queueSimRefresh()
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
                        Text("الجهاز مقترن ومتصل. تبقى العملية متصلة بـADB حتى يعود تأكيد Android النهائي.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("اختيار SIM", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        TextButton(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    queueSimRefresh()
                                    busy = false
                                    waitForOperationResult()
                                }
                            },
                            enabled = connected && !busy && !controller.operationPending,
                        ) { Text("تحديث الشرائح") }
                    }
                    if (simCards.isEmpty()) {
                        Text("لم تُقرأ أي شريحة بعد. اتصل بـWireless ADB ثم حدّث القائمة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        simCards.forEach { sim ->
                            FilterChip(
                                selected = selectedSubId == sim.subscriptionId,
                                onClick = {
                                    selectedSubId = sim.subscriptionId
                                    persistUiState()
                                },
                                enabled = !busy && !controller.operationPending,
                                label = { Text("SIM ${sim.slot} • ${sim.carrierName.ifBlank { "مشغّل غير معروف" }}") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        selectedSim?.let { sim ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("CarrierConfig الحالي — SIM ${sim.slot}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        if (sim.currentConfig.isEmpty()) {
                            Text("لا يوجد override ظاهر حالياً.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            sim.currentConfig.forEach { (key, value) -> Text("$key: $value", style = MaterialTheme.typography.bodyMedium) }
                        }
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
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("رمز البلد", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { countryMenuExpanded = true }, modifier = Modifier.fillMaxWidth(), enabled = !controller.operationPending) {
                            val countryLabel = if (isCustomCountryCode) "مخصص" else SimNrfrPresets.countries.firstOrNull { it.code == selectedCountryCode }?.let { "${it.label} (${it.code})" } ?: "اختر دولة"
                            Text(countryLabel)
                        }
                        DropdownMenu(
                            expanded = countryMenuExpanded,
                            onDismissRequest = { countryMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 460.dp),
                        ) {
                            SimNrfrPresets.countries.forEach { country ->
                                DropdownMenuItem(
                                    text = { Text("${country.label} (${country.code})") },
                                    onClick = {
                                        selectedCountryCode = country.code
                                        isCustomCountryCode = false
                                        countryMenuExpanded = false
                                        message = null
                                        persistUiState()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("مخصص") },
                                onClick = {
                                    isCustomCountryCode = true
                                    countryMenuExpanded = false
                                    persistUiState()
                                },
                            )
                        }
                    }
                    if (isCustomCountryCode) {
                        OutlinedTextField(
                            value = customCountryCode,
                            onValueChange = {
                                if (it.length <= 2 && it.all(Char::isLetter)) {
                                    customCountryCode = it.uppercase()
                                    selectedCountryCode = customCountryCode
                                    persistUiState()
                                }
                            },
                            label = { Text("رمز ISO مخصص — حرفان") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            enabled = !controller.operationPending,
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
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("اسم المشغّل", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { carrierMenuExpanded = true }, modifier = Modifier.fillMaxWidth(), enabled = !controller.operationPending) {
                            Text(selectedCarrier?.label ?: "اختر مشغّلاً")
                        }
                        DropdownMenu(
                            expanded = carrierMenuExpanded,
                            onDismissRequest = { carrierMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 460.dp),
                        ) {
                            SimNrfrPresets.carriers.filterNot { it.custom }.groupBy { it.region }.forEach { (region, carriers) ->
                                val regionLabel = SimNrfrPresets.countries.firstOrNull { it.code == region }?.label ?: region
                                Text(regionLabel, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                                carriers.forEach { carrier ->
                                    DropdownMenuItem(
                                        text = { Text(carrier.label) },
                                        onClick = {
                                            selectedCarrierId = carrier.id
                                            customCarrierName = carrier.displayName
                                            carrierMenuExpanded = false
                                            message = null
                                            persistUiState()
                                        },
                                    )
                                }
                            }
                            DropdownMenuItem(
                                text = { Text("مخصص") },
                                onClick = {
                                    selectedCarrierId = SimNrfrPresets.carriers.first { it.custom }.id
                                    carrierMenuExpanded = false
                                    persistUiState()
                                },
                            )
                        }
                    }
                    selectedCarrier?.let { carrier ->
                        if (!carrier.custom && carrier.displayName.isNotBlank()) {
                            Text("القيمة المرسلة: ${carrier.displayName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (selectedCarrier?.custom == true) {
                        OutlinedTextField(
                            value = customCarrierName,
                            onValueChange = {
                                customCarrierName = it
                                persistUiState()
                            },
                            label = { Text("اسم المشغّل المخصص") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !controller.operationPending,
                        )
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = if (airplaneMode) KunPalette.SuccessSoft else MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (airplaneMode) "وضع الطيران مفعّل" else "وضع الطيران غير مفعّل", fontWeight = FontWeight.Bold, color = if (airplaneMode) KunPalette.Success else KunPalette.Warning)
                    Text("وضع الطيران غير مطلوب؛ أبقِ Wi‑Fi وWireless debugging فعالين.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = ::refreshConnection, enabled = !busy) { Text("تحديث حالة ADB") }
                }
            }
        }
        if (controller.operationPending) {
            item {
                NoticeCard(
                    title = "جاري تنفيذ أمر النظام",
                    body = "يعمل ICarrierConfigLoader داخل Instrumentation المضمّن. اترك Wireless debugging فعالاً حتى يعود تأكيد Android النهائي.",
                    color = KunPalette.WarningSoft,
                    textColor = KunPalette.Ink,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val sim = selectedSim ?: return@OutlinedButton
                        scope.launch {
                            busy = true
                            val result = withContext(Dispatchers.IO) { controller.resetCarrierConfig(sim) }
                            if (result.isSuccess) {
                                clearNrfrSelections()
                                message = "أُرسل طلب إعادة التعيين؛ سيظهر التأكيد بعد عودة التطبيق."
                            } else {
                                message = "فشل بدء إعادة التعيين: ${result.exceptionOrNull()?.message}"
                            }
                            busy = false
                            refreshLogs()
                            if (result.isSuccess) waitForOperationResult()
                        }
                    },
                    enabled = selectedSim != null && connected && !busy && !controller.operationPending,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("إعادة تعيين") }
                Button(
                    onClick = {
                        val sim = selectedSim ?: return@Button
                        val country = if (isCustomCountryCode) customCountryCode.takeIf { it.length == 2 } else selectedCountryCode.takeIf { it.isNotEmpty() }
                        val carrierName = when {
                            selectedCarrier?.custom == true -> customCarrierName.takeIf { it.isNotEmpty() }
                            selectedCarrier != null -> selectedCarrier.displayName
                            else -> null
                        }
                        scope.launch {
                            busy = true
                            val result = withContext(Dispatchers.IO) { controller.saveCarrierConfig(sim, country, carrierName) }
                            message = if (result.isSuccess) "أُرسل طلب الحفظ؛ سيظهر التأكيد بعد عودة التطبيق." else "فشل بدء الحفظ: ${result.exceptionOrNull()?.message}"
                            busy = false
                            refreshLogs()
                            if (result.isSuccess) waitForOperationResult()
                        }
                    },
                    enabled = selectedSim != null && connected && !busy && !controller.operationPending && (
                        (isCustomCountryCode && customCountryCode.length == 2) ||
                            (!isCustomCountryCode && selectedCountryCode.isNotEmpty()) ||
                            (selectedCarrier != null && (!selectedCarrier.custom || customCarrierName.isNotEmpty()))
                        ),
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("حفظ وتطبيق")
                }
            }
        }
        message?.let { text -> item { InlineMessage(text) } }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Text("ستظهر هنا حالة ADB وأمر النظام ونتيجة العملية أو سبب الخطأ.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(
                            Modifier.fillMaxWidth().height(320.dp)
                                .background(Color(0xFF101510), RoundedCornerShape(14.dp))
                                .verticalScroll(logScrollState).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            logs.takeLast(80).forEach { entry ->
                                val lineColor = when (entry.level) {
                                    "ERR" -> Color(0xFFFF7777)
                                    "CMD" -> Color(0xFF9BE9A8)
                                    "OUT" -> Color(0xFFD6DED6)
                                    else -> Color(0xFF8CC8FF)
                                }
                                val command = entry.message.removePrefix("$ ").replace("\\n", "\n").replace("\\r", "\r")
                                val prompt = when (entry.level) {
                                    "CMD" -> "adb$ $command"
                                    "ERR" -> "ERROR  $command"
                                    "OUT" -> command
                                    else -> "INFO   $command"
                                }
                                Text("[${entry.timestamp}] $prompt", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = lineColor, lineHeight = 16.sp)
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
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = textColor)
            Text(body, style = MaterialTheme.typography.bodySmall, color = textColor)
        }
    }
}

@Composable
private fun InlineMessage(message: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start)
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
