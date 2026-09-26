package com.proxyplatform.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun AdvancedOperationLogPanel(
    lines: List<String>,
    copied: Boolean,
    exportStatus: String?,
    exportPath: String?,
    onCopy: () -> Unit,
    onExport: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("سجل العملية — CMD", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "الأوامر والردود والأحداث بالترتيب الزمني (${lines.size} سطر)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 360.dp)
                    .background(Color(0xFF050A0E), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                if (lines.isEmpty()) {
                    Text(
                        "بانتظار بدء عملية…\nستظهر هنا أوامر ADB والردود والتوقيت والأخطاء.",
                        color = Color(0xFF8EA39A),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        itemsIndexed(lines) { _, line ->
                            Text(
                                line,
                                color = when {
                                    "[FATAL]" in line || "[ERR]" in line || "[TRACE]" in line || "[EXIT_TRACE]" in line -> Color(0xFFFF7777)
                                    "[CMD]" in line -> Color(0xFF75C7FF)
                                    "[OUT]" in line -> Color(0xFF8CE99A)
                                    else -> Color(0xFFD2D9D5)
                                },
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onCopy, enabled = lines.isNotEmpty()) {
                    Text(if (copied) "تم النسخ" else "نسخ")
                }
                TextButton(onClick = onExport, enabled = lines.isNotEmpty()) { Text("حفظ كملف") }
            }
            exportStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            exportPath?.let { path ->
                Text(
                    "سحب مباشر من الكمبيوتر: adb pull \"$path\"",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF75C7FF)
                )
            }
            Text(
                "يُسجّل التطبيق منذ بدء التشغيل؛ يظهر التقرير في المرة التالية بعد الإغلاق غير المتوقع. قد يظهر عنوان البروكسي، لكن رموز الاقتران وكلمات المرور لا تُسجّل.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
