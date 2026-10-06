package com.proxyplatform.app

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class SimOperatorProfile(
    val id: String,
    val label: String,
    val operatorNumeric: String,
    val operatorAlpha: String,
    val isoCountry: String,
)

internal object SimOperatorProfiles {
    val all = listOf(
        SimOperatorProfile(
            id = "canada_rogers",
            label = "كندا — Rogers",
            operatorNumeric = "302720",
            operatorAlpha = "Rogers",
            isoCountry = "ca",
        ),
        SimOperatorProfile(
            id = "usa_tmobile",
            label = "أمريكا — T-Mobile",
            operatorNumeric = "310260",
            operatorAlpha = "T-Mobile",
            isoCountry = "us",
        ),
    )

    fun find(id: String?): SimOperatorProfile? = all.firstOrNull { it.id == id }

    fun values(profile: SimOperatorProfile): LinkedHashMap<String, String> = linkedMapOf(
        "gsm.sim.operator.numeric" to profile.operatorNumeric,
        "gsm.sim.operator.alpha" to profile.operatorAlpha,
        "gsm.sim.operator.iso-country" to profile.isoCountry,
        "gsm.operator.numeric" to profile.operatorNumeric,
        "gsm.operator.alpha" to profile.operatorAlpha,
        "gsm.operator.iso-country" to profile.isoCountry,
    )
}

/** Builds fixed-property shell commands and checks both setprop's status and the resulting value. */
internal object SimOperatorCommands {
    const val RESULT_MARKER = "__KUN_SIM_PROP__"
    private val resultPattern = Regex("(?m)^${Regex.escape(RESULT_MARKER)}(\\d+)\\|(.*)$")

    data class SetResult(val exitCode: Int, val value: String)

    fun quote(value: String): String = "'${value.replace("'", "'\\''")}'"

    fun setAndRead(property: String, value: String): String =
        "setprop ${quote(property)} ${quote(value)}; __kun_sim_set_rc=\$?; " +
            "__kun_sim_actual=\$(getprop ${quote(property)}); " +
            "printf '\\n$RESULT_MARKER%s|%s\\n' \"\$__kun_sim_set_rc\" \"\$__kun_sim_actual\""

    fun parseSetResult(output: String): SetResult {
        val match = resultPattern.findAll(output).lastOrNull()
            ?: error("لم يرجع ADB علامة تحقق لنتيجة setprop.")
        return SetResult(
            exitCode = match.groupValues[1].toInt(),
            value = match.groupValues[2].trimEnd('\r'),
        )
    }
}

/**
 * Runtime gsm.* properties are not SIM provisioning. Android or the radio
 * service may reject writes; every attempted write is verified and reversible.
 */
internal class SimOperatorController(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val hasPendingRestore: Boolean
        get() = prefs.getBoolean(ACTIVE, false)

    val activeProfileId: String?
        get() = prefs.getString(PROFILE_ID, null)

    fun apply(profile: SimOperatorProfile): Result<Unit> = runCatching {
        check(isAirplaneModeOn()) {
            "فعّل وضع الطيران أولاً، ثم أعد تشغيل Wi‑Fi مع إبقاء وضع الطيران فعالاً."
        }
        check(!hasPendingRestore) {
            "توجد جلسة سابقة تحتاج إلى استعادة القيم قبل بدء جلسة جديدة."
        }

        SimOperationLog.info(appContext, "بدء اختبار خصائص المشغّل: ${profile.label}.")
        val targets = SimOperatorProfiles.values(profile)
        val original = linkedMapOf<String, String>()
        targets.keys.forEach { property ->
            val value = readProperty(property)
            original[property] = value
            SimOperationLog.output(appContext, "القيمة الأصلية: $property=$value")
        }
        // Probe write access using each property's existing value. This is a
        // no-op on success and lets ordinary, non-root ADB sessions fail before
        // creating a restore marker that Android will never let them use.
        targets.forEach { (property, _) ->
            try {
                setAndVerify(property, original.getValue(property))
            } catch (failure: Throwable) {
                val explanation = "رفض Android الكتابة إلى $property. خصائص SIM والشبكة محمية ويتحكم بها نظام الاتصالات؛ اتصال Wireless ADB العادي لا يملك صلاحية setprop. لم تُغيّر القيم ولم تُحفظ جلسة استعادة."
                SimOperationLog.error(appContext, explanation)
                throw IllegalStateException(explanation, failure)
            }
        }
        saveSnapshot(profile.id, original)
        SimOperationLog.info(appContext, "حُفظت القيم الأصلية محلياً قبل أي تعديل.")

        try {
            targets.forEach { (property, value) -> setAndVerify(property, value) }
            SimOperationLog.info(
                appContext,
                "انتهى الاختبار. هذه خصائص runtime فقط ولا تمثل تغييراً في SIM أو تسجيل الشبكة."
            )
        } catch (failure: Throwable) {
            SimOperationLog.error(
                appContext,
                "فشل تطبيق الملف ${profile.label}: ${failure.message ?: failure.javaClass.simpleName}"
            )
            val rollbackErrors = restoreSnapshot()
            val detail = if (rollbackErrors.isEmpty()) {
                "فشلت المحاولة وأُعيدت القيم الأصلية تلقائياً."
            } else {
                "فشلت المحاولة وتعذرت استعادة بعض القيم؛ أعد الاتصال ثم استخدم زر الاستعادة."
            }
            throw IllegalStateException("$detail ${failure.message ?: ""}".trim(), failure)
        }
    }.onFailure {
        SimOperationLog.error(appContext, "تعذر تشغيل اختبار SIM: ${it.message ?: it.javaClass.simpleName}")
        AdvancedOperationLog.exception(appContext, "فشل اختبار خصائص SIM", it)
    }

    fun restore(): Result<Unit> = runCatching {
        check(hasPendingRestore) { "لا توجد جلسة محفوظة للاستعادة." }
        SimOperationLog.info(appContext, "بدء استعادة خصائص SIM الأصلية.")
        val errors = restoreSnapshot()
        check(errors.isEmpty()) {
            "تعذرت استعادة ${errors.size} خاصية: ${errors.joinToString()}. أعد اتصال Wireless ADB ثم حاول مجدداً."
        }
        SimOperationLog.info(appContext, "تمت استعادة جميع القيم الأصلية والتحقق منها.")
    }.onFailure {
        SimOperationLog.error(appContext, "فشلت الاستعادة: ${it.message ?: it.javaClass.simpleName}")
        AdvancedOperationLog.exception(appContext, "فشل استعادة خصائص SIM", it)
    }

    private fun readProperty(property: String): String {
        val command = "getprop ${SimOperatorCommands.quote(property)}"
        SimOperationLog.command(appContext, command)
        val output = WirelessDebuggingManager.executeCommandResult(appContext, command).getOrThrow()
        SimOperationLog.output(appContext, output.ifBlank { "<قيمة فارغة>" })
        return output.removeSuffix("\n").removeSuffix("\r")
    }

    private fun setAndVerify(property: String, value: String) {
        val command = SimOperatorCommands.setAndRead(property, value)
        SimOperationLog.command(appContext, command)
        val output = WirelessDebuggingManager.executeCommandResult(appContext, command).getOrThrow()
        SimOperationLog.output(appContext, output.ifBlank { "<مخرجات فارغة>" })
        val result = SimOperatorCommands.parseSetResult(output)
        check(result.exitCode == 0) {
            "setprop رُفض للخاصية $property (رمز ${result.exitCode}); الرد: ${output.trim()}"
        }
        check(result.value == value) {
            "لم تتطابق قيمة $property بعد الكتابة. المطلوب='$value'، المقروء='${result.value}'. الرد: ${output.trim()}"
        }
        SimOperationLog.info(appContext, "تم التحقق من $property=${result.value}.")
    }

    private fun saveSnapshot(profileId: String, values: Map<String, String>) {
        val editor = prefs.edit().putString(PROFILE_ID, profileId).putBoolean(ACTIVE, true)
        values.entries.forEachIndexed { index, entry ->
            editor.putString("$VALUE_PREFIX$index", entry.value)
                .putString("$KEY_PREFIX$index", entry.key)
        }
        check(editor.commit()) { "تعذر حفظ نسخة الاستعادة على الجهاز." }
    }

    /** Attempts every property; keeps the snapshot if even one restoration fails. */
    private fun restoreSnapshot(): List<String> {
        val failures = mutableListOf<String>()
        repeat(SNAPSHOT_SIZE) { index ->
            val property = prefs.getString("$KEY_PREFIX$index", null) ?: return@repeat
            val original = prefs.getString("$VALUE_PREFIX$index", null)
            if (original == null) {
                failures += property
                SimOperationLog.error(appContext, "لا توجد قيمة أصلية محفوظة للخاصية $property.")
                return@repeat
            }
            val current = runCatching { readProperty(property) }.getOrElse { failure ->
                failures += property
                SimOperationLog.error(
                    appContext,
                    "تعذرت قراءة $property قبل الاستعادة: ${failure.message ?: failure.javaClass.simpleName}"
                )
                return@repeat
            }
            if (current == original) {
                SimOperationLog.info(appContext, "القيمة الأصلية لـ $property ما زالت كما هي؛ تم تجاوز الكتابة.")
                return@repeat
            }
            runCatching { setAndVerify(property, original) }
                .onFailure {
                    failures += property
                    SimOperationLog.error(
                        appContext,
                        "تعذرت استعادة $property: ${it.message ?: it.javaClass.simpleName}"
                    )
                }
        }
        if (failures.isEmpty()) {
            check(
                prefs.edit().clear().commit()
            ) { "تمت كتابة القيم الأصلية لكن تعذر إنهاء لقطة الاستعادة؛ أعد التحقق لاحقاً." }
        }
        return failures
    }

    private fun isAirplaneModeOn(): Boolean = runCatching {
        Settings.Global.getInt(
            appContext.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0,
        ) == 1
    }.getOrDefault(false)

    private companion object {
        const val PREFS = "sim_operator_restore"
        const val ACTIVE = "active"
        const val PROFILE_ID = "profile_id"
        const val KEY_PREFIX = "property_key_"
        const val VALUE_PREFIX = "property_value_"
        const val SNAPSHOT_SIZE = 6
    }
}

internal data class SimLogEntry(val timestamp: String, val level: String, val message: String)

/** Page-local log: persists the command and reply transcript on this device only. */
internal object SimOperationLog {
    private const val PREFS = "sim_operator_logs"
    private const val ENTRIES = "entries"
    private const val MAX_ENTRIES = 300
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    fun info(context: Context, message: String) { append(context, "INFO", message) }
    fun command(context: Context, command: String) { append(context, "CMD", "$ $command") }
    fun output(context: Context, output: String) { append(context, "OUT", output) }
    fun error(context: Context, message: String) { append(context, "ERR", message) }

    fun read(context: Context): List<SimLogEntry> = synchronized(lock) {
        runCatching {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ENTRIES, "[]")
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let {
                    SimLogEntry(
                        timestamp = it.optString("time"),
                        level = it.optString("level"),
                        message = it.optString("message"),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun append(context: Context, level: String, message: String) = synchronized(lock) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val current = JSONArray(prefs.getString(ENTRIES, "[]"))
            val entries = mutableListOf<JSONObject>()
            for (index in 0 until current.length()) {
                current.optJSONObject(index)?.let(entries::add)
            }
            val time = synchronized(timeFormat) { timeFormat.format(Date()) }
            entries.add(JSONObject()
                .put("time", time)
                .put("level", level)
                .put("message", message.replace("\r", "\\r").replace("\n", "\\n").take(4_000)))
            val kept = entries.takeLast(MAX_ENTRIES)
            val result = JSONArray().apply { kept.forEach { put(it) } }
            check(prefs.edit().putString(ENTRIES, result.toString()).commit()) {
                "تعذر حفظ سجل SIM محلياً."
            }
        }
    }
}
