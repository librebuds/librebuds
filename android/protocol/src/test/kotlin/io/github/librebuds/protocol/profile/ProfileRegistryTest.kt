// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.profile

import io.github.librebuds.protocol.repoRoot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ProfileRegistryTest {
    private val registry: ProfileRegistry by lazy {
        val files = File(repoRoot(), "profiles").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        ProfileRegistry.fromJson(files.map { it.readText() })
    }

    @Test
    fun loadsAllRepositoryProfiles() {
        assertTrue(registry.profiles.size >= 30, "expected the generated catalog, got ${registry.profiles.size}")
    }

    @Test
    fun repositoryProfilesHaveUniqueIdsAndIdentifiers() {
        fun duplicates(values: List<String>) = values.groupBy { it.uppercase() }.filterValues { it.size > 1 }.keys
        assertEquals(emptySet<String>(), duplicates(registry.profiles.map { it.id }))
        assertEquals(emptySet<String>(), duplicates(registry.profiles.flatMap { it.match.sku }))
        assertEquals(emptySet<String>(), duplicates(registry.profiles.flatMap { it.match.modelId }))
    }

    @Test
    fun matchesBySkuFirst() {
        val profile = registry.match(sku = "BTFT0020", modelId = "000149", btName = "whatever")
        assertEquals("freebuds-6", profile.id)
        assertEquals("2026-09-29", profile.verifiedOn("anc"))
    }

    @Test
    fun matchesModelIdCaseInsensitively() {
        assertEquals("freebuds-pro-3", registry.match(modelId = "000149").id)
        assertEquals("freebuds-6", registry.match(modelId = "000161").id)
        assertEquals("freebuds-5", registry.match(modelId = "000141").id)
        assertEquals("freebuds-5i", registry.match(modelId = "00013d").id)
    }

    @Test
    fun matchesExactBluetoothNameLast() {
        assertEquals("freebuds-pro-5", registry.match(btName = "HUAWEI FreeBuds Pro 5").id)
    }

    @Test
    fun unknownDeviceGetsGenericProfile() {
        val profile = registry.match(sku = "NOPE", modelId = "FFFFFF", btName = "Renamed buds")
        assertEquals("generic", profile.id)
        assertTrue(profile.supports("battery"))
        assertTrue(profile.supports("anc"))
        assertNull(profile.verifiedOn("anc"))
        assertFalse(profile.supports("multipoint"))
    }

    @Test
    fun unverifiedCapabilityReportsNull() {
        assertNull(registry.match(modelId = "000157").verifiedOn("anc"))
    }

    @Test
    fun ignoresUnknownJsonKeys() {
        val r = ProfileRegistry.fromJson(listOf("""{"id":"x","name":"X","future":1,"capabilities":{"anc":{"verified":null}}}"""))
        assertEquals("x", r.profiles.single().id)
    }

    /**
     * Every gesture sub-key a target profile lists carries an `options` map (plus `inCallOptions`
     * when it declares `inCall`), and every `equalizer`/`soundQuality` block carries its own
     * options map. Option keys are the signed device codes the controller compares against, so
     * they must all parse as integers.
     *
     * Capabilities confirmed by a live round carry that round's date; anything still unconfirmed
     * on a given model stays null, so an untested model can never silently claim verification.
     */
    @Test
    fun targetProfilesDeclareSettingTables() {
        val targetIds = listOf(
            "freebuds-6",
            "freebuds-5",
            "freebuds-pro-2",
            "freebuds-pro-3",
            "freebuds-pro-4",
            "freebuds-pro-5",
            "freebuds-4",
        )
        val gestureSubKeys = listOf("doubleTap", "tripleTap", "longPress", "noiseCycle", "swipe")

        // Swept on real hardware in round 2 (2026-09-29); every other model stays unverified.
        val sweptInRound2 = mapOf(
            "freebuds-6" to setOf("battery", "anc", "wear", "gestures", "equalizer", "multipoint", "language"),
            "freebuds-5" to setOf("battery", "anc", "wear", "gestures", "equalizer", "multipoint", "language"),
            "freebuds-pro-3" to setOf("battery", "anc", "wear", "gestures", "equalizer", "multipoint", "language"),
            "freebuds-pro-2" to setOf("battery", "anc"),
        )
        val newCapabilities = listOf("wear", "gestures", "equalizer", "lowLatency", "soundQuality", "multipoint", "language")

        fun assertOptions(id: String, label: String, value: JsonElement?) {
            val options = value as? JsonObject
            assertTrue(options != null, "$id: $label has no options object")
            assertTrue(options!!.isNotEmpty(), "$id: $label options is empty")
            for (key in options.keys) assertTrue(key.toIntOrNull() != null, "$id: $label option key '$key' is not an int")
        }

        for (id in targetIds) {
            val profile = registry.profiles.first { it.id == id }

            val confirmed = sweptInRound2[id].orEmpty()
            for (capability in newCapabilities) {
                if (!profile.supports(capability)) continue
                if (capability in confirmed) {
                    assertEquals("2026-09-29", profile.verifiedOn(capability), "$id: $capability was confirmed in round 2")
                } else {
                    assertNull(profile.verifiedOn(capability), "$id: $capability should be unverified")
                }
            }

            val gestures = profile.capabilities["gestures"]
            assertTrue(gestures != null, "$id: expected a gestures capability")
            for (key in gestureSubKeys) {
                val entry = gestures!![key] as? JsonObject ?: continue
                assertOptions(id, "gestures.$key", entry["options"])
                val declaresInCall = (entry["inCall"] as? JsonPrimitive)?.booleanOrNull == true
                if (declaresInCall) assertOptions(id, "gestures.$key.inCallOptions", entry["inCallOptions"])
            }

            profile.capabilities["equalizer"]?.let { assertOptions(id, "equalizer.presets", it["presets"]) }
            profile.capabilities["soundQuality"]?.let { assertOptions(id, "soundQuality.options", it["options"]) }
        }

        // Settings follow the vendor app's per-model device page (2026-10-02).
        val freebuds4 = registry.profiles.first { it.id == "freebuds-4" }
        assertTrue(freebuds4.supports("equalizer"), "freebuds-4: the vendor app shows the equalizer")
        assertFalse(freebuds4.supports("multipoint"), "freebuds-4: the vendor app has no dual-connect page")
        assertFalse(freebuds4.supports("lowLatency"), "freebuds-4: the vendor app has no low-latency row")
        assertFalse(freebuds4.supports("soundQuality"), "freebuds-4: the vendor app has no sound-quality switch")
        assertEquals(setOf("verified", "doubleTap", "longPress"), freebuds4.capabilities.getValue("gestures").keys)
    }

    private fun gestureEntry(id: String, key: String): JsonObject? =
        registry.profiles.first { it.id == id }.capabilities.getValue("gestures")[key] as? JsonObject

    private fun codes(entry: JsonObject?, table: String): List<Int> = (entry?.get(table) as? JsonObject)?.keys?.map { it.toInt() }.orEmpty()

    @Test
    fun freebuds5GesturesMatchTheVendorApp() {
        assertEquals(listOf(1, 2, 7, 0, -1), codes(gestureEntry("freebuds-5", "doubleTap"), "options"))
        assertEquals(listOf(0, -1), codes(gestureEntry("freebuds-5", "doubleTap"), "inCallOptions"))
        assertEquals(listOf(2, 7, -1), codes(gestureEntry("freebuds-5", "tripleTap"), "options"))
        assertEquals(listOf(3, -1), codes(gestureEntry("freebuds-5", "longPress"), "options"))
        assertEquals(listOf(15), codes(gestureEntry("freebuds-5", "longPress"), "hiddenOptions"))
        assertEquals(listOf(0, -1), codes(gestureEntry("freebuds-5", "longPress"), "inCallOptions"))
        assertNull(gestureEntry("freebuds-5", "noiseCycle"))
        assertEquals(listOf(0, -1), codes(gestureEntry("freebuds-5", "swipe"), "options"))
    }

    @Test
    fun pinchModelsHaveNoTapGesturesTheyDoNotShow() {
        assertNull(gestureEntry("freebuds-pro-3", "doubleTap"))
        assertNull(gestureEntry("freebuds-pro-3", "tripleTap"))
        assertNull(gestureEntry("freebuds-pro-2", "doubleTap"))
        assertEquals(listOf(10, 0, -1), codes(gestureEntry("freebuds-pro-3", "longPress"), "options"))
        assertEquals(listOf(1, 2, 3, 4), codes(gestureEntry("freebuds-pro-3", "noiseCycle"), "options"))
        val pro5DoubleTap = gestureEntry("freebuds-pro-5", "doubleTap")
        assertEquals(true, (pro5DoubleTap?.get("bothSides") as? JsonPrimitive)?.booleanOrNull)
        assertEquals(listOf(1, -1), codes(pro5DoubleTap, "options"))
    }
}
