package com.proxyplatform.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserEnvironmentTest {
    @Test
    fun acceptsAndCanonicalizesLanguageLists() {
        assertEquals("en-CA,en-US", BrowserEnvironment.normalizeAcceptLanguages(" en-ca, en-us "))
        assertEquals("ar-SA", BrowserEnvironment.normalizeAcceptLanguages("ar-sa"))
    }

    @Test
    fun rejectsMalformedOrOversizedLanguageLists() {
        assertNull(BrowserEnvironment.normalizeAcceptLanguages("en-US,not a tag"))
        assertNull(BrowserEnvironment.normalizeAcceptLanguages("en-US,fr-FR,de-DE,es-ES,it-IT,pt-BR,ja-JP"))
        assertNull(BrowserEnvironment.normalizeAcceptLanguages(""))
    }

    @Test
    fun proxyLanguageListUsesExitCountryAndNeverAddsTheDeviceLocale() {
        val canadaLanguages = BrowserEnvironment.proxyAcceptLanguages("ca")
        assertEquals("en-CA,en-US", canadaLanguages)
        assertFalse("ar-SY" in canadaLanguages)
        assertEquals("en-US", BrowserEnvironment.proxyAcceptLanguages("unknown"))
    }

    @Test
    fun requestedLocalesPreservesOnlyConfiguredTags() {
        assertEquals(listOf("en-CA", "en-US"), BrowserEnvironment.requestedLocales("en-CA,en-US").toList())
        assertEquals(listOf("en-US"), BrowserEnvironment.requestedLocales("malformed tag").toList())
        assertEquals("UTC", BrowserEnvironment.PRIVACY_TIME_ZONE)
        assertEquals("+JSDateTimeUTC", BrowserEnvironment.FINGERPRINTING_TIME_ZONE_TARGET)
    }

    @Test
    fun acceptsIanaTimeZonesAndUtc() {
        assertEquals("America/Toronto", BrowserEnvironment.normalizeTimeZone(" America/Toronto "))
        assertEquals("UTC", BrowserEnvironment.normalizeTimeZone("UTC"))
    }

    @Test
    fun rejectsUnknownOrMalformedTimeZonesInsteadOfFallingBackToGmt() {
        assertNull(BrowserEnvironment.normalizeTimeZone("America/NotARealCity"))
        assertNull(BrowserEnvironment.normalizeTimeZone("America/\nToronto"))
        assertNull(BrowserEnvironment.normalizeTimeZone(""))
    }
}
