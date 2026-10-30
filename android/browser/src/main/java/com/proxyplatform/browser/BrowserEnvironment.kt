package com.proxyplatform.browser

import java.time.ZoneId
import java.util.Locale

/** Validates profile values before they are passed to Gecko or a content script. */
internal object BrowserEnvironment {
    private val languageTagPattern = Regex("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$")

    /** Returns a canonical comma-separated BCP 47 list, or null for malformed input. */
    fun normalizeAcceptLanguages(value: String): String? {
        val tags = value.split(',').map(String::trim).filter(String::isNotEmpty)
        if (tags.isEmpty() || tags.size > MAX_LANGUAGE_TAGS) return null

        val normalized = tags.map { tag ->
            if (!languageTagPattern.matches(tag)) return null
            val canonical = Locale.forLanguageTag(tag).toLanguageTag()
            if (canonical == "und") return null
            canonical
        }.distinct()

        return normalized.takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    /** Returns a valid IANA/Java zone ID without silently falling back to GMT. */
    fun normalizeTimeZone(value: String): String? {
        val candidate = value.trim()
        if (candidate.isEmpty() || candidate.length > MAX_TIME_ZONE_LENGTH || candidate.any { it.isWhitespace() }) {
            return null
        }
        return runCatching { ZoneId.of(candidate).id }.getOrNull()
    }

    private const val MAX_LANGUAGE_TAGS = 6
    private const val MAX_TIME_ZONE_LENGTH = 64
}
