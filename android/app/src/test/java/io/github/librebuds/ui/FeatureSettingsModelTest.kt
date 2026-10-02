// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import io.github.librebuds.R
import io.github.librebuds.protocol.command.AncState
import io.github.librebuds.protocol.command.CustomPreset
import io.github.librebuds.protocol.command.EqOperation
import io.github.librebuds.protocol.command.EqualizerState
import io.github.librebuds.protocol.command.Feature
import io.github.librebuds.protocol.command.FeatureAbilities
import io.github.librebuds.protocol.command.FeatureState
import io.github.librebuds.protocol.command.PinchSetting
import io.github.librebuds.protocol.command.PinchSlot
import io.github.librebuds.protocol.command.Side
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.protocol.profile.cancellationLevelKey
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.DeviceSettings
import io.github.librebuds.state.SettingChange
import io.github.librebuds.ui.model.OptionGroup
import io.github.librebuds.ui.model.applyTo
import io.github.librebuds.ui.model.cancellationLevelPicker
import io.github.librebuds.ui.model.controlKey
import io.github.librebuds.ui.model.optionLabelRes
import io.github.librebuds.ui.model.settingsModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** What the settings screen offers per model for the extras: the shipped profile lists, the earbuds' answers decide. */
class FeatureSettingsModelTest {
    private val registry: ProfileRegistry by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "profiles/freebuds-pro-5.json").isFile) dir = dir.parentFile
        ProfileRegistry.fromJson(File(dir!!, "profiles").listFiles { f -> f.name.endsWith(".json") }!!.sorted().map { it.readText() })
    }

    private fun profile(id: String) = registry.profiles.first { it.id == id }

    private val everything = DeviceSettings(
        abilities = FeatureAbilities(false, mapOf(0x07 to 1, 0x03 to 0, 0x19 to 1, 0x09 to 1, 0x0B to 1)),
        features = mapOf(Feature.EAR_TIP to FeatureState(8, 2), Feature.HEAD_CONTROL to FeatureState(0x0B, 1, 1, 0)),
        pinch = mapOf(PinchSlot(0, 2) to PinchSetting(PinchSlot(0, 2), 2, 2), PinchSlot(3, 0) to PinchSetting(PinchSlot(3, 0), 6, 255)),
        equalizer = EqualizerState(active = 5, available = listOf(5, 9, 2), custom = listOf(CustomPreset(100, List(10) { 0 }, "Flat"))),
        equalizerExtended = true,
        restReminder = true,
        hdCall = true,
        pickupMode = 0,
        ringing = mapOf(Side.LEFT to false, Side.RIGHT to true),
    )

    private val awareness = AncState(modeCode = 2, level = 4, awarenessLevel = 3)

    @Test
    fun pro5ShowsEverythingTheEarbudsReported() {
        val m = settingsModel(profile("freebuds-pro-5"), BudsState(anc = awareness, settings = everything))
        assertEquals(listOf(2, 1, 4), m.awareness!!.options.map { it.code })
        assertEquals(4, m.awareness!!.current)
        assertEquals(3, m.awarenessLevel)
        // Drop detection is offered by the earbuds but not listed for Pro 5, so it stays hidden.
        assertEquals(setOf(Feature.SINGLE_BUD_ANC, Feature.ADAPTIVE_VOLUME, Feature.AI_CONVERSATION), m.switches.keys)
        assertEquals(true, m.switches[Feature.AI_CONVERSATION])
        assertEquals(0, m.headControl!!.shake!!.current)
        assertEquals(2, m.earTip!!.current)
        assertEquals(listOf(PinchSlot(0, 2), PinchSlot(3, 0)), m.pinch.map { it.slot })
        assertEquals(255, m.pinch[1].right!!.current)
        assertEquals(listOf(5, 9, 2, 201, 100), m.equalizer!!.options.map { it.code })
        assertEquals("Flat", m.equalizer!!.options.last().label)
        assertEquals(101, m.customEqualizer!!.freeSlot)
        assertNull("not listed for Pro 5", m.hdCall)
        assertEquals(0, m.pickupMode!!.current)
        assertEquals(true, m.findEarbuds!![Side.RIGHT])
        assertTrue("pinch" in m.experimental)
    }

    @Test
    fun withoutAnswersNothingExtraShows() {
        val m = settingsModel(profile("freebuds-pro-5"), BudsState(anc = AncState(1, 3)))
        assertNull(m.awareness)
        assertNull(m.awarenessLevel)
        assertEquals(emptyMap<Feature, Boolean>(), m.switches)
        assertNull(m.headControl)
        assertNull(m.earTip)
        assertEquals(emptyList<Any>(), m.pinch)
        assertNull(m.restReminder)
        assertNull(m.pickupMode)
        assertNull(m.findEarbuds)
    }

    @Test
    fun extendedPresetsWaitForTheEarbudsAnswer() {
        val m = settingsModel(profile("freebuds-pro-5"), BudsState(settings = everything.copy(equalizerExtended = false)))
        assertEquals(listOf(5, 9, 2, 100), m.equalizer!!.options.map { it.code })
    }

    @Test
    fun awarenessSliderOnlyWithTheAdaptiveSubMode() {
        val voice = settingsModel(profile("freebuds-pro-5"), BudsState(anc = awareness.copy(level = 1), settings = everything))
        assertNull(voice.awarenessLevel)
        assertEquals(1, voice.awareness!!.current)
        val pro4 = settingsModel(profile("freebuds-pro-4"), BudsState(anc = awareness, settings = everything))
        assertEquals(listOf(2, 1), pro4.awareness!!.options.map { it.code })
        assertNull("no adaptive awareness on Pro 4", pro4.awarenessLevel)
    }

    @Test
    fun freebuds6HasNoAwarenessPicker() {
        val m = settingsModel(profile("freebuds-6"), BudsState(anc = awareness, settings = everything))
        assertNull(m.awareness)
        assertEquals(setOf(Feature.SINGLE_BUD_ANC, Feature.ADAPTIVE_VOLUME, Feature.DROP_DETECTION), m.switches.keys)
        assertNull(m.earTip)
        assertEquals(emptyList<Any>(), m.pinch)
        assertEquals(true, m.headControl!!.enabled)
    }

    @Test
    fun freebuds4LevelsAndNoCustomPresets() {
        val p = profile("freebuds-4")
        val picker = cancellationLevelPicker(p, 1)!!
        assertEquals(listOf(1, 0), picker.options.map { it.code })
        val m = settingsModel(p, BudsState(settings = everything))
        assertNull(m.customEqualizer)
        assertTrue("HD calls and pickup mode make the sound section", m.hasSound)
        assertEquals(true, m.hdCall)
        assertNotNull(m.pickupMode)
    }

    @Test
    fun levelNames() {
        assertEquals(R.string.cancellation_level_dual_engine, optionLabelRes(OptionGroup.CANCELLATION_LEVEL, profile("freebuds-pro-5").cancellationLevelKey(3)))
        assertEquals(R.string.cancellation_level_dynamic, optionLabelRes(OptionGroup.CANCELLATION_LEVEL, profile("freebuds-pro-3").cancellationLevelKey(3)))
        assertEquals(R.string.eq_hifi_live, optionLabelRes(OptionGroup.EQUALIZER, "hifi_live"))
        assertEquals(R.string.awareness_adaptive, optionLabelRes(OptionGroup.AWARENESS, "adaptive"))
        assertEquals(R.string.gesture_answer_end, optionLabelRes(OptionGroup.GESTURE, "answer_end"))
    }

    @Test
    fun optimisticValues() {
        val state = BudsState(anc = awareness, settings = everything)
        val tip = SettingChange.FeatureValue(Feature.EAR_TIP, 1).applyTo(state)
        assertEquals(1, tip.settings.features[Feature.EAR_TIP]?.state)
        val tap = SettingChange.PinchChange(PinchSlot(0, 2), 255, null).applyTo(state)
        assertEquals(PinchSetting(PinchSlot(0, 2), 255, 255), tap.settings.pinch[PinchSlot(0, 2)])
        val hold = SettingChange.PinchChange(PinchSlot(3, 0), null, 5).applyTo(state)
        assertEquals(PinchSetting(PinchSlot(3, 0), 6, 5), hold.settings.pinch[PinchSlot(3, 0)])
        assertEquals(1, SettingChange.AwarenessMode(1).applyTo(state).anc?.level)
        assertEquals(9, SettingChange.AwarenessLevel(9).applyTo(state).anc?.awarenessLevel)
        val saved = SettingChange.CustomEqualizer(101, List(10) { 1 }, "B", EqOperation.SAVE).applyTo(state)
        assertEquals(listOf(100, 101), saved.settings.equalizer?.custom?.map { it.id })
        assertEquals(101, saved.settings.equalizer?.active)
        val deleted = SettingChange.CustomEqualizer(100, List(10) { 0 }, "Flat", EqOperation.DELETE).applyTo(state)
        assertEquals(emptyList<CustomPreset>(), deleted.settings.equalizer?.custom)
        assertEquals(state, SettingChange.CustomEqualizer(100, List(10) { 3 }, "Flat", EqOperation.PREVIEW).applyTo(state))
    }

    @Test
    fun controlKeysKeepSidesAndFieldsApart() {
        assertEquals("feature.HEAD_CONTROL.3", SettingChange.FeatureValue(Feature.HEAD_CONTROL, 1, 3).controlKey())
        assertEquals("pinch.3/0.right", SettingChange.PinchChange(PinchSlot(3, 0), null, 5).controlKey())
        assertEquals("pinch.3/0.left", SettingChange.PinchChange(PinchSlot(3, 0), 5, null).controlKey())
    }
}
