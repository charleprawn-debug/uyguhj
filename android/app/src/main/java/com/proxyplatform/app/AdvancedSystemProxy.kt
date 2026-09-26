package com.proxyplatform.app

import android.content.Context
import org.json.JSONObject

/** Applies the advanced-mode system proxy and restores the user's prior settings on stop. */
internal object AdvancedSystemProxy {
    private const val PREFS = "advanced_system_proxy"
    private const val SNAPSHOT = "snapshot"
    private const val SNAPSHOT_SAVED = "snapshot_saved"
    private val managedSettings = listOf(
        "global_http_proxy_host",
        "global_http_proxy_port",
        "http_proxy",
    )

    fun hasPendingRestore(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(SNAPSHOT_SAVED, false)

    fun apply(context: Context, proxy: String): Result<Unit> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(SNAPSHOT_SAVED, false)) {
            AdvancedOperationLog.info(context, "قراءة إعدادات بروكسي Android الحالية لحفظ نسخة استعادة.")
            val snapshot = JSONObject()
            managedSettings.forEach { key ->
                snapshot.put(key, read(context, key).getOrThrow())
            }
            check(
                prefs.edit()
                    .putString(SNAPSHOT, snapshot.toString())
                    .putBoolean(SNAPSHOT_SAVED, true)
                    .commit()
            ) { "تعذر حفظ إعدادات البروكسي السابقة." }
            AdvancedOperationLog.output(context, "حُفظت نسخة الاستعادة محليًا داخل مساحة التطبيق الخاصة.")
        }

        AdvancedOperationLog.info(context, "تعيين بروكسي النظام إلى $proxy.")
        write(context, "http_proxy", proxy).getOrThrow()
        val actual = read(context, "http_proxy").getOrThrow()
        check(actual == proxy) { "إعداد النظام '$actual' لا يطابق '$proxy'." }
        AdvancedOperationLog.output(context, "تأكد ضبط بروكسي النظام والتحقق منه: $actual.")
    }.onFailure {
        AdvancedOperationLog.error(context, "فشل ضبط بروكسي النظام: ${it.message ?: it.javaClass.simpleName}")
        // If setting the new proxy partially succeeded, do not leave it behind.
        restore(context, proxy)
    }

    /**
     * Restores settings captured by [apply]. For an installation upgraded while
     * an older advanced session is active, only clears the old loopback proxy
     * when it still points at this app; unrelated user settings are untouched.
     */
    fun restore(context: Context, expectedProxy: String? = null): Result<Unit> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        AdvancedOperationLog.info(context, "بدء استعادة إعدادات بروكسي Android السابقة.")
        if (!prefs.getBoolean(SNAPSHOT_SAVED, false)) {
            if (expectedProxy != null && read(context, "http_proxy").getOrThrow() == expectedProxy) {
                write(context, "http_proxy", ":0").getOrThrow()
                write(context, "global_http_proxy_host", null).getOrThrow()
                write(context, "global_http_proxy_port", null).getOrThrow()
                AdvancedOperationLog.output(context, "أُزيل إعداد loopback قديم تابع للوضع المتقدم.")
            } else {
                AdvancedOperationLog.info(context, "لا توجد لقطة محفوظة؛ لا توجد إعدادات سابقة تابعة لهذه الجلسة للاستعادة.")
            }
            return@runCatching
        }
        if (expectedProxy != null && read(context, "http_proxy").getOrThrow() != expectedProxy) {
            // The proxy was changed externally after we installed ours. Keep
            // that newer setting and drop only our recovery snapshot.
            check(prefs.edit().remove(SNAPSHOT).remove(SNAPSHOT_SAVED).commit()) {
                "تعذر تحرير لقطة إعدادات البروكسي القديمة."
            }
            AdvancedOperationLog.output(context, "لم تتغير قيمة النظام خارجيًا بواسطة التطبيق؛ حُفظت القيمة الحالية وحُذفت لقطة الاستعادة.")
            return@runCatching
        }

        val snapshot = JSONObject(prefs.getString(SNAPSHOT, "{}") ?: "{}")
        // Restore host/port first and the primary setting last so Android sees
        // the complete original value when it refreshes its proxy tracker.
        listOf("global_http_proxy_host", "global_http_proxy_port", "http_proxy").forEach { key ->
            val value = snapshot.optString(key, "null")
            write(context, key, value.takeUnless { it == "null" }).getOrThrow()
            val restored = read(context, key).getOrThrow()
            check(restored == value) { "تعذر استعادة إعداد النظام $key." }
        }
        check(prefs.edit().remove(SNAPSHOT).remove(SNAPSHOT_SAVED).commit()) {
            "تعذر إنهاء استعادة إعدادات البروكسي."
        }
        AdvancedOperationLog.output(context, "تمت استعادة قيم بروكسي Android والتحقق منها.")
    }.onFailure {
        AdvancedOperationLog.error(context, "فشلت استعادة بروكسي Android: ${it.message ?: it.javaClass.simpleName}")
    }

    private fun read(context: Context, key: String): Result<String> =
        WirelessDebuggingManager.executeCommandResult(context, "settings get global $key")
            .map { it.trim() }

    private fun write(context: Context, key: String, value: String?): Result<Unit> {
        val command = if (value == null || value == "null") {
            "settings delete global $key"
        } else {
            "settings put global $key ${shellQuote(value)}"
        }
        return WirelessDebuggingManager.executeCommandResult(context, command).map { Unit }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}
