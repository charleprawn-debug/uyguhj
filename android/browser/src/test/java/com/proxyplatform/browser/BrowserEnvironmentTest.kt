package com.proxyplatform.browser

import org.junit.Assert.assertEquals
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
    fun acceptsIanaTimeZonesAndUtc() {
        assertEquals("America/Toronto", BrowserEnvironment.normalizeTimeZone("America/Toronto"))
        assertEquals("UTC", BrowserEnvironment.normalizeTimeZone("UTC"))
    }

    @Test
    fun rejectsUnknownOrMalformedTimeZonesInsteadOfFallingBackToGmt() {
        assertNull(BrowserEnvironment.normalizeTimeZone("America/NotARealCity"))
        assertNull(BrowserEnvironment.normalizeTimeZone("America/Toronto\n"))
        assertNull(BrowserEnvironment.normalizeTimeZone(""))
    }
}
