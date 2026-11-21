package io.github.librebuds.ui.other

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtherEarbudsCatalogTest {
    private val models = listOf(
        KnownModel("Sony", "WF-1000XM5"),
        KnownModel("SOUNDPEATS", "Air5 Pro+"),
        KnownModel("Samsung", "Galaxy Buds2 Pro")
    )

    @Test fun exactNameIgnoresPunctuationAndCase() {
        assertEquals("Sony", OtherEarbudsCatalog.match("WF 1000XM5", models)?.brand)
        assertEquals("SOUNDPEATS", OtherEarbudsCatalog.match("air5 pro+", models)?.brand)
    }

    @Test fun doesNotMatchUnrelatedDeviceBySubstring() {
        assertNull(OtherEarbudsCatalog.match("My Sony WF-1000XM5 Car Stereo", models))
        assertNull(OtherEarbudsCatalog.match("Gaming PC", models))
    }

    @Test fun freebudsAlwaysUseOriginalHuaweiBackend() {
        assertTrue(OtherEarbudsCatalog.isHuaweiManaged("HUAWEI FreeBuds 6i"))
        assertTrue(OtherEarbudsCatalog.isHuaweiManaged("FreeBuds Pro 4"))
        assertFalse(OtherEarbudsCatalog.isHuaweiManaged("Nothing Ear (2)"))
        assertFalse(OtherEarbudsCatalog.isHuaweiManaged("Galaxy Buds3 Pro"))
    }

    @Test fun renamedAirpodsAreIdentifiedByFamilyNotFabricatedModel() {
        assertEquals("Apple / Beats", OtherEarbudsCatalog.displayBrand("Vedang's AirPods"))
        assertEquals("Samsung", OtherEarbudsCatalog.displayBrand("Galaxy Buds3 Pro"))
        assertNull(OtherEarbudsCatalog.displayBrand("Living room television"))
        assertNull(OtherEarbudsCatalog.match("Vedang's AirPods", models))
    }

    @Test fun tozoNamesStaySeparateFromProtocolVerification() {
        val tozo = listOf(KnownModel("TOZO", "NC9"), KnownModel("TOZO", "AeroSound6"))
        assertEquals("TOZO", OtherEarbudsCatalog.match("TOZO NC9", tozo)?.brand)
        assertEquals("TOZO", OtherEarbudsCatalog.match("AeroSound6", tozo)?.brand)
        assertNull(OtherEarbudsCatalog.match("TOZO NC9 charging case", tozo))
        assertEquals("TOZO", OtherEarbudsCatalog.displayBrand("TOZO T10"))
        assertFalse(OtherEarbudsCatalog.isHuaweiManaged("TOZO T10"))
    }

    @Test fun headphoneNameFallbackIsConservative() {
        assertTrue(OtherEarbudsCatalog.looksLikeHeadphones("Nothing Ear (2)"))
        assertTrue(OtherEarbudsCatalog.looksLikeHeadphones("Pixel Buds Pro"))
        assertFalse(OtherEarbudsCatalog.looksLikeHeadphones("Living room television"))
        assertFalse(OtherEarbudsCatalog.looksLikeHeadphones("Office printer"))
    }
}
