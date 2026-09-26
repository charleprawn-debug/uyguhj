package com.proxyplatform.app

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Persistent, local-only, CMD-style diagnostics for advanced proxy operations. */
internal object AdvancedOperationLog {
    private const val TAG = "AdvancedOperationLog"
    private const val FILE_NAME = "advanced-operation.log"
    private const val MAX_ENTRY_CHARS = 16_384
    private const val MAX_LOG_LINES = 5_000
    private const val MAX_LOG_BYTES = 2_000_000L
    private val lock = Any()
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "advanced-operation-log").apply { isDaemon = true }
    }
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val secretPatterns = listOf(
        Regex("(?i)(password|passwd|token|authorization|pairing[ _-]?code)(\\s*[:=]\\s*)([^\\s,;]+)"),
        Regex("(?i)(https?://)([^/@\\s:]+):([^/@\\s]+)@")
    )

    fun info(context: Context, message: String) = append(context, "INFO", message)
    fun command(context: Context, command: String) = append(context, "CMD", "\$ $command")
    fun output(context: Context, output: String) = append(
        context,
        "OUT",
        output.trimEnd().ifBlank { "<لا يوجد مخرجات من الأمر>" }
    )
    fun error(context: Context, message: String) = append(context, "ERR", message)

    fun append(context: Context, level: String, message: String) {
        val safe = redact(message)
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .take(MAX_ENTRY_CHARS)
        val timestamp = synchronized(timeFormat) { timeFormat.format(Date()) }
        val line = "$timestamp [$level] $safe\n"
        val file = logFile(context)
        writer.execute {
            try {
                synchronized(lock) {
                    file.appendText(line, Charsets.UTF_8)
                    if (file.length() > MAX_LOG_BYTES) {
                        val retainedNewestFirst = ArrayList<String>()
                        var retainedBytes = 0L
                        for (entry in file.readLines(Charsets.UTF_8).asReversed().take(MAX_LOG_LINES)) {
                            val entryBytes = entry.toByteArray(Charsets.UTF_8).size + 1L
                            if (retainedNewestFirst.isNotEmpty() && retainedBytes + entryBytes > MAX_LOG_BYTES) break
                            retainedNewestFirst += entry
                            retainedBytes += entryBytes
                        }
                        file.writeText(
                            retainedNewestFirst.asReversed().joinToString("\n", postfix = "\n"),
                            Charsets.UTF_8
                        )
                    }
                }
            } catch (failure: Exception) {
                Log.e(TAG, "Unable to write advanced operation log", failure)
            }
        }
    }

    fun readLines(context: Context): List<String> = runCatching {
        writer.submit<List<String>> {
            synchronized(lock) {
                logFile(context).takeIf(File::exists)?.readLines(Charsets.UTF_8).orEmpty()
            }
        }.get()
    }.onFailure { Log.e(TAG, "Unable to read advanced operation log", it) }
        .getOrDefault(emptyList())

    fun clear(context: Context) {
        val file = logFile(context)
        writer.execute {
            try {
                synchronized(lock) {
                    file.writeText("", Charsets.UTF_8)
                }
            } catch (failure: Exception) {
                Log.e(TAG, "Unable to clear advanced operation log", failure)
            }
        }
    }

    private fun redact(value: String): String {
        var result = value
        secretPatterns.forEach { pattern ->
            result = pattern.replace(result) { match ->
                if (match.groupValues.size >= 4 && match.groupValues[1].startsWith("http", true)) {
                    "${match.groupValues[1]}[REDACTED]@"
                } else {
                    "${match.groupValues[1]}${match.groupValues[2]}[REDACTED]"
                }
            }
        }
        return result
    }

    private fun logFile(context: Context): File = File(context.filesDir, FILE_NAME)
}
