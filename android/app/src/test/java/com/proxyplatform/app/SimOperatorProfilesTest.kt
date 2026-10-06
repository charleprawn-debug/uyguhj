package com.proxyplatform.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimOperatorProfilesTest {
    @Test
    fun canadaProfileMapsToRogersMccMncAndCountry() {
        val profile = SimOperatorProfiles.find("canada_rogers")!!

        assertEquals("302720", profile.operatorNumeric)
        assertEquals("Rogers", profile.operatorAlpha)
        assertEquals("ca", profile.isoCountry)
        assertEquals(6, SimOperatorProfiles.values(profile).size)
    }

    @Test
    fun unitedStatesProfileMapsToTMobileMccMncAndCountry() {
        val profile = SimOperatorProfiles.find("usa_tmobile")!!

        assertEquals("310260", profile.operatorNumeric)
        assertEquals("T-Mobile", profile.operatorAlpha)
        assertEquals("us", profile.isoCountry)
        assertEquals(6, SimOperatorProfiles.values(profile).size)
    }

    @Test
    fun shellQuotingEscapesSingleQuotes() {
        assertEquals("'a'\\''b'", SimOperatorCommands.quote("a'b"))
        assertTrue(SimOperatorCommands.setAndRead("gsm.operator.alpha", "T-Mobile").contains("setprop"))
    }

    @Test
    fun parsesSetterStatusAndReadBackValue() {
        val result = SimOperatorCommands.parseSetResult("permission warning\n${SimOperatorCommands.RESULT_MARKER}0|Rogers\n")

        assertEquals(0, result.exitCode)
        assertEquals("Rogers", result.value)
    }
}