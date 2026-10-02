// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.bt.LinkFactory
import io.github.librebuds.protocol.command.CustomPreset
import io.github.librebuds.protocol.command.EqOperation
import io.github.librebuds.protocol.command.Feature
import io.github.librebuds.protocol.command.FeatureSwitch
import io.github.librebuds.protocol.command.Pinch
import io.github.librebuds.protocol.command.PinchSlot
import io.github.librebuds.protocol.command.Side
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.state.SettingChange
import io.github.librebuds.ui.model.settingsModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The per-model extras against the shipped profiles: the connect-time probe round, gating and every write. */
@OptIn(ExperimentalCoroutinesApi::class)
class BudsControllerFeaturesTest {
    private val registry: ProfileRegistry by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "profiles/freebuds-pro-5.json").isFile) dir = dir.parentFile
        val root = requireNotNull(dir) { "profiles/ not found" }
        ProfileRegistry.fromJson(File(root, "profiles").listFiles { f -> f.name.endsWith(".json") }!!.sorted().map { it.readText() })
    }

    private class Rig(val controller: BudsController, val earbuds: FakeEarbuds, val features: FeatureEarbuds)

    private fun TestScope.rig(features: FeatureEarbuds = FeatureEarbuds(), sku: String = "", name: String = PRO5): Rig {
        val earbuds = features.into(FakeEarbuds(sku = sku))
        val controller = BudsController(
            linkFactory = LinkFactory { earbuds.link() },
            registry = registry,
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
        )
        return Rig(controller, earbuds, features)
    }

    private suspend fun TestScope.connected(rig: Rig, name: String = PRO5): Rig {
        rig.controller.connect("AA:BB:CC:DD:EE:FF", name)
        advanceUntilIdle()
        return rig
    }

    private fun Rig.model() = settingsModel(registry.profiles.first { it.id == controller.state.value.profileId }, controller.state.value)

    @Test
    fun pro5ProbeRoundReadsOnlyWhatTheEarbudsOffer() = runTest {
        val r = connected(rig())
        assertEquals("freebuds-pro-5", r.controller.state.value.profileId)
        val sent = r.features.requests
        assertTrue(sent.first { it.startsWith("2B B3") }.startsWith("2B B3 01 01 01 0A 01 00"))
        // Ability answer offered single-bud ANC (07), adaptive volume (03), conversation (19) and head control (0B).
        val featureReads = sent.filter { it.startsWith("2B B4") }
        assertEquals(
            listOf("2B B4 01 01 05 02 00", "2B B4 01 01 02 02 00", "2B B4 01 01 1B 02 00", "2B B4 01 01 0B", "2B B4 01 01 08 02 00"),
            featureReads,
        )
        assertEquals(6, sent.count { it.startsWith("2B 93") })
        assertTrue(sent.contains("2B A8 01 01"))
        assertTrue(sent.contains("2B 61 01 00"))
        assertTrue(sent.contains("2B 42 01 01"))
        assertFalse("Pro 5 has no HD call row", sent.any { it.startsWith("2B 46") })
        assertEquals(listOf("2B 5E 01 01 00", "2B 5E 01 01 01"), sent.filter { it.startsWith("2B 5E") })

        val m = r.model()
        assertEquals(mapOf(Feature.SINGLE_BUD_ANC to true, Feature.ADAPTIVE_VOLUME to false, Feature.AI_CONVERSATION to false), m.switches)
        assertEquals(true, m.headControl?.enabled)
        assertEquals(1, m.headControl?.nod?.current)
        assertEquals(2, m.headControl?.shake?.current)
        assertEquals(1, m.earTip?.current)
        assertEquals(6, m.pinch.size)
        val hold = m.pinch.last()
        assertEquals(PinchSlot(3, 0), hold.slot)
        assertEquals(6, hold.left?.current)
        assertEquals(5, hold.right?.current)
        assertNull("tap slots have one value for both earbuds", m.pinch.first().right)
        assertEquals(true, m.restReminder)
        assertEquals(1, m.pickupMode?.current)
        assertNull(m.hdCall)
        assertEquals(mapOf(Side.LEFT to false, Side.RIGHT to false), m.findEarbuds)
        assertTrue("the extended preset joins the list", m.equalizer!!.options.any { it.code == 201 && it.key == "sound_classical" })
    }

    @Test
    fun silentAbilityQueryHidesTheSwitchesButNotTheEarTip() = runTest {
        val r = connected(rig(FeatureEarbuds(abilities = null)))
        val reads = r.features.requests.filter { it.startsWith("2B B4") }
        assertEquals(listOf("2B B4 01 01 08 02 00"), reads)
        val m = r.model()
        assertEquals(emptyMap<Feature, Boolean>(), m.switches)
        assertNull(m.headControl)
        assertNotNull(m.earTip)
        assertTrue("abilities" in r.controller.state.value.settings.unanswered)
    }

    @Test
    fun earbudsAskingForTheQueryAgainGetItWithTlv1Zero() = runTest {
        val r = connected(rig(FeatureEarbuds(needsReply = true)))
        val queries = r.features.requests.filter { it.startsWith("2B B3") }
        assertEquals(2, queries.size)
        assertTrue(queries[1].startsWith("2B B3 01 01 00 0A 01 00"))
    }

    @Test
    fun silentFindQueriesHideFindEarbuds() = runTest {
        val r = connected(rig(FeatureEarbuds(ringing = mutableMapOf())))
        assertNull(r.model().findEarbuds)
    }

    @Test
    fun featureWriteIsConfirmedByTheEarbudsAnswer() = runTest {
        val r = connected(rig())
        assertTrue(r.controller.apply(SettingChange.FeatureValue(Feature.SINGLE_BUD_ANC, 0)).isSuccess)
        assertTrue(r.features.requests.contains("2B B4 01 01 05 02 01 00"))
        assertEquals(0, r.features.features.getValue(5)[0])
        assertEquals(false, r.model().switches[Feature.SINGLE_BUD_ANC])

        assertTrue(r.controller.apply(SettingChange.FeatureValue(Feature.HEAD_CONTROL, 2, FeatureSwitch.NOD)).isSuccess)
        assertTrue(r.features.requests.contains("2B B4 01 01 0B 03 01 02"))
        assertEquals(2, r.model().headControl?.nod?.current)

        assertTrue(r.controller.apply(SettingChange.FeatureValue(Feature.EAR_TIP, 2)).isSuccess)
        assertEquals(2, r.model().earTip?.current)
    }

    @Test
    fun refusedFeatureWriteFails() = runTest {
        val r = connected(rig(FeatureEarbuds(refuse = setOf("2B/B4"))))
        val result = r.controller.apply(SettingChange.FeatureValue(Feature.ADAPTIVE_VOLUME, 1))
        assertTrue(result.exceptionOrNull() is AncRejectedException)
        assertEquals(false, r.model().switches[Feature.ADAPTIVE_VOLUME])
    }

    @Test
    fun featureNotOfferedIsRefusedBeforeSending() = runTest {
        val r = connected(rig(FeatureEarbuds(abilities = mapOf(0x07 to 1))))
        val result = r.controller.apply(SettingChange.FeatureValue(Feature.ADAPTIVE_VOLUME, 1))
        assertTrue(result.exceptionOrNull() is SettingUnavailableException)
        assertFalse(r.features.requests.contains("2B B4 01 01 02 02 01 01"))
    }

    @Test
    fun tapPinchWritesBothEarbudsInOneFrame() = runTest {
        val r = connected(rig())
        assertTrue(r.controller.apply(SettingChange.PinchChange(PinchSlot(1, 2), Pinch.NONE, null)).isSuccess)
        assertEquals(listOf("2B 92 01 01 01 02 01 02 03 01 FF 04 01 FF"), r.features.requests.filter { it.startsWith("2B 92") })
        assertEquals(Pinch.NONE to Pinch.NONE, r.features.pinch[PinchSlot(1, 2)])
    }

    @Test
    fun pinchAndHoldWritesOneSidePerFrame() = runTest {
        val r = connected(rig())
        assertTrue(r.controller.apply(SettingChange.PinchChange(PinchSlot(3, 0), null, 6)).isSuccess)
        assertEquals(listOf("2B 92 01 01 03 02 01 00 04 01 06"), r.features.requests.filter { it.startsWith("2B 92") })
        assertEquals(6, r.model().pinch.last().right?.current)
    }

    @Test
    fun pinchWriteTheEarbudsIgnoreFails() = runTest {
        val r = connected(rig(FeatureEarbuds(ignore = setOf("2B/92"))))
        val result = r.controller.apply(SettingChange.PinchChange(PinchSlot(0, 2), Pinch.NONE, null))
        assertTrue(result.exceptionOrNull() is AncRejectedException)
    }

    @Test
    fun ringAndStop() = runTest {
        val r = connected(rig())
        assertTrue(r.controller.ring(Side.LEFT, true).isSuccess)
        assertTrue(r.features.requests.contains("2B 5D 01 02 00 00"))
        assertEquals(true, r.controller.state.value.settings.ringing[Side.LEFT])
        assertTrue(r.controller.ring(Side.LEFT, false).isSuccess)
        assertTrue(r.features.requests.contains("2B 5D 01 02 00 01"))
        assertEquals(false, r.controller.state.value.settings.ringing[Side.LEFT])
    }

    @Test
    fun ringRefusedByTheEarbudsFails() = runTest {
        val r = connected(rig(FeatureEarbuds(ringResult = 1)))
        assertTrue(r.controller.ring(Side.RIGHT, true).exceptionOrNull() is AncRejectedException)
        assertEquals(false, r.controller.state.value.settings.ringing[Side.RIGHT])
    }

    @Test
    fun restReminderPickupAndHdCallFollowTheStatus() = runTest {
        val r = connected(rig())
        assertTrue(r.controller.apply(SettingChange.RestReminderChange(false)).isSuccess)
        assertTrue(r.features.requests.contains("2B 60 01 01 00"))
        assertEquals(false, r.model().restReminder)
        assertTrue(r.controller.apply(SettingChange.PickupModeChange(0)).isSuccess)
        assertEquals(0, r.model().pickupMode?.current)
        assertTrue("Pro 5 lists no HD calls", r.controller.apply(SettingChange.HdCallChange(true)).exceptionOrNull() is UnsupportedOperationException)

        val refused = connected(rig(FeatureEarbuds(refuse = setOf("2B/60"))))
        assertTrue(refused.controller.apply(SettingChange.RestReminderChange(false)).exceptionOrNull() is AncRejectedException)
    }

    @Test
    fun hdCallOnFreebuds5() = runTest {
        val r = connected(rig(sku = "BTFT0013"), name = "HUAWEI FreeBuds 5")
        assertEquals("freebuds-5", r.controller.state.value.profileId)
        assertTrue(r.features.requests.contains("2B 46 01 01"))
        assertFalse("no feature switches listed for FreeBuds 5", r.features.requests.any { it.startsWith("2B B3") })
        assertEquals(false, r.model().hdCall)
        assertTrue(r.controller.apply(SettingChange.HdCallChange(true)).isSuccess)
        assertTrue(r.features.requests.contains("2B 45 01 01 01"))
        assertEquals(true, r.model().hdCall)
    }

    @Test
    fun awarenessSubModeAndLevel() = runTest {
        val r = connected(rig())
        assertTrue(r.controller.apply(SettingChange.AwarenessMode(4)).isSuccess)
        assertEquals(2, r.controller.state.value.anc?.modeCode)
        assertEquals(4, r.controller.state.value.anc?.level)
        assertEquals(5, r.model().awarenessLevel)
        assertNotNull(r.model().awareness)
        assertTrue(r.controller.apply(SettingChange.AwarenessLevel(8)).isSuccess)
        assertTrue(r.earbuds.let { r.features.requests.contains("2B 04 01 02 02 04 03 01 01 04 01 08") })
        assertEquals(8, r.model().awarenessLevel)
        assertTrue(r.controller.apply(SettingChange.AwarenessMode(3)).exceptionOrNull() is UnsupportedOperationException)
    }

    @Test
    fun extendedPresetSelectSendsTheGainTable() = runTest {
        val r = connected(rig(FeatureEarbuds(customEqualizer = true)))
        assertTrue(r.controller.apply(SettingChange.EqualizerPreset(201)).isSuccess)
        assertTrue(
            r.features.requests.contains("2B 49 01 01 C9 02 01 0A 05 01 01 03 0A FB 14 1E 0A 00 00 E7 F6 0A 00 04 03 32 30 31"),
        )
        assertEquals(201, r.controller.state.value.settings.equalizer?.active)
    }

    @Test
    fun customPresetSaveSelectAndDelete() = runTest {
        val r = connected(rig(FeatureEarbuds(customEqualizer = true)))
        val gains = listOf(10, 0, 0, 0, 0, 0, 0, 0, 0, -10)
        assertTrue(r.controller.apply(SettingChange.CustomEqualizer(100, gains, "Mine", EqOperation.SAVE)).isSuccess)
        assertEquals(listOf(CustomPreset(100, gains, "Mine")), r.controller.state.value.settings.equalizer?.custom)
        val m = r.model()
        assertEquals("Mine", m.equalizer!!.options.first { it.code == 100 }.label)
        assertEquals(101, m.customEqualizer?.freeSlot)

        // Selecting a custom preset resends its gains with the save operation, as the vendor app does.
        assertTrue(r.controller.apply(SettingChange.EqualizerPreset(100)).isSuccess)
        assertTrue(r.features.requests.last { it.startsWith("2B 49") }.startsWith("2B 49 01 01 64 02 01 0A 05 01 01"))

        assertTrue(r.controller.apply(SettingChange.CustomEqualizer(100, gains, "Mine", EqOperation.DELETE)).isSuccess)
        assertEquals(emptyList<CustomPreset>(), r.controller.state.value.settings.equalizer?.custom)
    }

    @Test
    fun freebuds6HasNoAwarenessButItsSwitches() = runTest {
        val features = FeatureEarbuds(abilities = mapOf(0x07 to 1, 0x09 to 1, 0x03 to 1, 0x0B to 0), pinch = mutableMapOf())
        features.features[0x07] = intArrayOf(1, 0, 0)
        features.features[0x0B] = intArrayOf(0, 1, 2)
        val r = connected(rig(features, sku = "BTFT0020"), name = "HUAWEI FreeBuds 6")
        assertEquals("freebuds-6", r.controller.state.value.profileId)
        val m = r.model()
        assertEquals(setOf(Feature.SINGLE_BUD_ANC, Feature.DROP_DETECTION, Feature.ADAPTIVE_VOLUME), m.switches.keys)
        assertEquals(false, m.headControl?.enabled)
        assertNull(m.earTip)
        assertTrue(m.pinch.isEmpty())
        assertFalse(r.features.requests.any { it.startsWith("2B 93") })
        assertFalse(r.features.requests.any { it.startsWith("2B B4 01 01 08") })
    }

    private companion object {
        const val PRO5 = "HUAWEI FreeBuds Pro 5"
    }
}
