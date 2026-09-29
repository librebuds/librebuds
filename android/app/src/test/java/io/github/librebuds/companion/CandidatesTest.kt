// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.companion

import io.github.librebuds.protocol.profile.ProfileRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidatesTest {
    private val registry = ProfileRegistry.fromJson(
        listOf("""{"id":"freebuds-6","name":"FreeBuds 6","match":{"btName":["HUAWEI FreeBuds 6"]}}"""),
    )

    @Test
    fun knownModelsFirstThenByName() {
        val result = candidates(
            listOf(
                BondedDevice("Car kit", "11:11:11:11:11:11"),
                BondedDevice("HUAWEI FreeBuds 6", "22:22:22:22:22:22"),
                BondedDevice(null, "33:33:33:33:33:33"),
            ),
            registry,
        )
        assertEquals(listOf("HUAWEI FreeBuds 6", "33:33:33:33:33:33", "Car kit"), result.map { it.name })
        assertEquals(listOf(true, false, false), result.map { it.known })
        assertEquals("freebuds-6", result.first().profileId)
        // Only a real Bluetooth name is offered for storing, never the address fallback.
        assertEquals(listOf("HUAWEI FreeBuds 6", null, "Car kit"), result.map { it.bondedName })
    }
}
