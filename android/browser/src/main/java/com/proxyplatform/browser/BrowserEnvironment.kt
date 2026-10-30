package com.proxyplatform.browser

import java.time.ZoneId
import java.util.Locale

/** Validates profile values before they are passed to Gecko or a content script. */
internal object BrowserEnvironment {
    const val PRIVACY_TIME_ZONE = "UTC"
    const val FINGERPRINTING_TIME_ZONE_TARGET = "+JSDateTimeUTC"
    const val FINGERPRINTING_PROTECTION_OVERRIDES =
        FINGERPRINTING_TIME_ZONE_TARGET +
            ",+CanvasRandomization,+CanvasExtractionFromThirdPartiesIsBlocked,+CanvasExtractionBeforeUserInputIsBlocked"

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

    /** Uses only profile-selected languages; never appends Android's locale implicitly. */
    fun requestedLocales(value: String): Array<String> =
        normalizeAcceptLanguages(value)?.split(',')?.toTypedArray() ?: arrayOf("en-US")

    /** Proxy locale comes from the exit country, never from saved or current device locales. */
    fun proxyAcceptLanguages(countryCode: String): String {
        val primary = when (countryCode.uppercase()) {
            "SY" -> "ar-SY"
            "SA" -> "ar-SA"
            "AE" -> "ar-AE"
            "EG" -> "ar-EG"
            "JO" -> "ar-JO"
            "LB" -> "ar-LB"
            "IQ" -> "ar-IQ"
            "TR" -> "tr-TR"
            "GB" -> "en-GB"
            "US" -> "en-US"
            "CA" -> "en-CA"
            "DE" -> "de-DE"
            "FR" -> "fr-FR"
            "ES" -> "es-ES"
            "IT" -> "it-IT"
            "NL" -> "nl-NL"
            "RU" -> "ru-RU"
            "IN" -> "hi-IN"
            "JP" -> "ja-JP"
            "KR" -> "ko-KR"
            "BR" -> "pt-BR"
            "CN" -> "zh-CN"
            else -> "en-US"
        }
        return listOf(primary, "en-US").distinct().joinToString(",")
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
