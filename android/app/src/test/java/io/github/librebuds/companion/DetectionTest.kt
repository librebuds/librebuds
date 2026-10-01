// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.companion

import io.github.librebuds.protocol.profile.ProfileRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionTest {
    private val registry = ProfileRegistry.fromJson(
        listOf("""{"id":"freebuds-6","name":"FreeBuds 6","match":{"btName":["HUAWEI FreeBuds 6"]}}"""),
    )
    private val a2dp = "0000110b-0000-1000-8000-00805f9b34fb"

    @Test
    fun keepsOnlyFreeBuds() {
        val bonded = listOf(
            BondedDevice("Car kit", "11:11:11:11:11:11", listOf(a2dp, SPP_UUID)),
            BondedDevice("JBL Flip 6", "22:22:22:22:22:22", listOf(a2dp)),
            BondedDevice("HUAWEI FreeBuds 6", "33:33:33:33:33:33", listOf(a2dp, SPP_UUID)),
            BondedDevice("Anna's Huawei buds", "44:44:44:44:44:44", listOf(a2dp, SPP_UUID.uppercase())),
            BondedDevice("GPS logger", "55:55:55:55:55:55", listOf(SPP_UUID)),
            BondedDevice("My buds", "66:66:66:66:66:66", emptyList()),
            BondedDevice(null, "77:77:77:77:77:77", emptyList()),
        )

        val result = detectFreeBuds(bonded, registry, known = setOf("66:66:66:66:66:66"))

        assertEquals(
            listOf(
                DetectedBuds("Anna's Huawei buds", "44:44:44:44:44:44", "generic", null, "Anna's Huawei buds"),
                DetectedBuds("HUAWEI FreeBuds 6", "33:33:33:33:33:33", "freebuds-6", "FreeBuds 6", "HUAWEI FreeBuds 6"),
                DetectedBuds("My buds", "66:66:66:66:66:66", "generic", null, "My buds"),
            ),
            result,
        )
    }

    @Test
    fun vendorNameWithoutSppIsNotEnough() {
        val result = detectFreeBuds(listOf(BondedDevice("Huawei Watch GT", "11:11:11:11:11:11")), registry, emptySet())
        assertTrue(result.isEmpty())
    }

    @Test
    fun storedAddressMatchesCaseInsensitivelyAndFallsBackToAddress() {
        val result = detectFreeBuds(listOf(BondedDevice(null, "aa:bb:cc:dd:ee:ff")), registry, setOf("AA:BB:CC:DD:EE:FF"))
        assertEquals(listOf(DetectedBuds("aa:bb:cc:dd:ee:ff", "aa:bb:cc:dd:ee:ff", "generic", null, null)), result)
    }

    @Test
    fun nothingPairedMeansNothingDetected() {
        assertTrue(detectFreeBuds(emptyList(), registry, emptySet()).isEmpty())
    }

    @Test
    fun disambiguatesOnlyCollidingNames() {
        val first = DetectedBuds("HUAWEI FreeBuds 5", "11:22:33:44:55:66", "freebuds-5", "FreeBuds 5", "HUAWEI FreeBuds 5")
        val second = DetectedBuds("HUAWEI FreeBuds 5", "aa:bb:cc:dd:77:88", "freebuds-5", "FreeBuds 5", "HUAWEI FreeBuds 5")
        val unique = DetectedBuds("HUAWEI FreeBuds Pro 4", "99:88:77:66:55:44", "freebuds-pro-4", "FreeBuds Pro 4", "HUAWEI FreeBuds Pro 4")

        val labels = disambiguatedLabels(listOf(first, second, unique))

        assertEquals("HUAWEI FreeBuds 5 · 55:66", labels.getValue("11:22:33:44:55:66"))
        assertEquals("HUAWEI FreeBuds 5 · 77:88", labels.getValue("aa:bb:cc:dd:77:88"))
        assertEquals("HUAWEI FreeBuds Pro 4", labels.getValue("99:88:77:66:55:44"))
    }

    @Test
    fun caseInsensitiveCollisionStillGetsSuffixed() {
        val lower = DetectedBuds("huawei freebuds 5", "11:11:11:11:55:66", "freebuds-5", "FreeBuds 5", "huawei freebuds 5")
        val upper = DetectedBuds("HUAWEI FREEBUDS 5", "22:22:22:22:55:66", "freebuds-5", "FreeBuds 5", "HUAWEI FREEBUDS 5")

        val labels = disambiguatedLabels(listOf(lower, upper))

        assertEquals("huawei freebuds 5 · 55:66", labels.getValue("11:11:11:11:55:66"))
        assertEquals("HUAWEI FREEBUDS 5 · 55:66", labels.getValue("22:22:22:22:55:66"))
    }

    @Test
    fun noCollisionsMeansNoLabelsChange() {
        val result = detectFreeBuds(
            listOf(BondedDevice("HUAWEI FreeBuds 6", "33:33:33:33:33:33", listOf(a2dp, SPP_UUID))),
            registry,
            emptySet(),
        )
        val labels = disambiguatedLabels(result)
        assertEquals("HUAWEI FreeBuds 6", labels.getValue("33:33:33:33:33:33"))
    }
}
