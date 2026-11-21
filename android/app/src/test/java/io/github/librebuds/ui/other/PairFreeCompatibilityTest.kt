// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.other

import io.github.omegadestroyer.pairfree.protocol.Identify
import io.github.omegadestroyer.pairfree.protocol.Maker
import io.github.omegadestroyer.pairfree.brands.Brands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards both the breadth of the PairFree model registry and the FreeBuds
 * backend boundary; these are identification checks, not physical device tests.
 */
class PairFreeCompatibilityTest {
    @Test fun identifiesBrandNamesAcrossNativeProtocolDrivers() {
        val examples = listOf(
            Maker.SOUNDPEATS to "SOUNDPEATS Air4 Pro",
            Maker.SOUNDCORE to "soundcore Liberty 4",
            Maker.SAMSUNG to "Galaxy Buds2 Pro",
            Maker.SONY to "Sony WF-1000XM5",
            Maker.BOSE to "Bose QuietComfort Earbuds",
            Maker.JBL to "JBL Live Buds",
            Maker.APPLE to "AirPods Pro",
            Maker.NOTHING to "Nothing Ear (2)",
            Maker.PIXEL to "Pixel Buds Pro",
            Maker.QCY to "QCY Melobuds",
            Maker.ONEMORE to "1MORE EVO",
            Maker.EARFUN to "EarFun Air Pro",
            Maker.ONEPLUS to "OnePlus Buds Pro",
            Maker.SENNHEISER to "Sennheiser Momentum True Wireless",
            Maker.JABRA to "Jabra Elite",
            Maker.SKULLCANDY to "Skullcandy Rail ANC"
        )
        examples.forEach { (expected, name) ->
            assertEquals("Wrong brand for $name", expected, Identify.maker(name, emptyList()))
        }
    }

    @Test fun pairedRegistryIncludesAll17VendorDrivers() {
        assertEquals(17, Brands.ALL.map { it.brand }.distinct().size)
        assertTrue(Brands.ALL.any { it.brand == Maker.CAMBRIDGE })
    }

    @Test fun huaweiFreeBudsAreReservedForOriginalLibreBudsBackend() {
        assertTrue(OtherEarbudsCatalog.isHuaweiManaged("HUAWEI FreeBuds 6i"))
        assertTrue(OtherEarbudsCatalog.isHuaweiManaged("FreeBuds Pro 4"))
        assertNull(Identify.maker("HUAWEI FreeBuds 6i", emptyList()))
        assertFalse(OtherEarbudsCatalog.isHuaweiManaged("Galaxy Buds3"))
        assertFalse(OtherEarbudsCatalog.isHuaweiManaged("Nothing Ear (2)"))
    }

    @Test fun unrelatedDevicesAreNotMisidentifiedAsEarbuds() {
        listOf("Galaxy Watch", "Pixel Watch", "Nothing Phone (2)",
            "OnePlus Watch", "office printer").forEach {
            assertNull("Unexpected match: $it", Identify.maker(it, emptyList()))
        }
    }
}
