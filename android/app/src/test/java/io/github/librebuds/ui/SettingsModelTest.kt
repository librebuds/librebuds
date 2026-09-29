// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import io.github.librebuds.R
import io.github.librebuds.protocol.command.EqualizerState
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.protocol.command.GestureSetting
import io.github.librebuds.protocol.command.LanguageInfo
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.DeviceSettings
import io.github.librebuds.state.SettingChange
import io.github.librebuds.ui.model.OptionGroup
import io.github.librebuds.ui.model.SettingOption
import io.github.librebuds.ui.model.controlKey
import io.github.librebuds.ui.model.keyOf
import io.github.librebuds.ui.model.optionLabelRes
import io.github.librebuds.ui.model.settingsModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsModelTest {
    private fun profile(json: String): Profile = ProfileRegistry.fromJson(listOf(json)).profiles.single()

    /** Shaped like the shipped FreeBuds 6 profile (inline, so profile updates do not change this test). */
    private val freebuds6: Profile = profile(
        """
        {"id": "fb6", "name": "FB6", "capabilities": {
          "battery": {"verified": "2026-09-28"}, "anc": {"verified": "2026-09-28"},
          "wear": {"verified": null},
          "gestures": {"verified": null,
            "doubleTap": {"inCall": true, "options": {"-1": "off", "1": "pause", "2": "next", "7": "previous", "0": "assistant"},
                          "inCallOptions": {"-1": "off", "0": "answer"}},
            "swipe": {"options": {"-1": "off", "0": "volume"}}},
          "equalizer": {"verified": null, "presets": {"1": "default", "2": "bass", "3": "treble", "9": "voice"}},
          "lowLatency": {"verified": null},
          "soundQuality": {"verified": null, "options": {"0": "connectivity", "1": "quality"}},
          "multipoint": {"verified": null}, "language": {"verified": null}
        }}
        """.trimIndent(),
    )

    private val fullSettings = DeviceSettings(
        wearDetection = true,
        gestures = mapOf(
            Gesture.DOUBLE_TAP to GestureSetting(left = 1, right = 2, inCall = 0, supported = emptyList()),
            Gesture.SWIPE to GestureSetting(left = 0, right = 0, inCall = null, supported = emptyList()),
        ),
        equalizer = EqualizerState(active = 2, available = emptyList()),
        lowLatency = false,
        soundQuality = 1,
        language = LanguageInfo(current = "en-GB", supported = emptyList()),
    )

    @Test
    fun showsControlsTheProfileListsAndTheDeviceReported() {
        val model = settingsModel(freebuds6, BudsState(settings = fullSettings, multipointEnabled = true))
        assertEquals(true, model.wear)
        assertEquals(listOf("doubleTap", "swipe"), model.gestures.map { it.subKey })
        val doubleTap = model.gestures[0]
        val left = doubleTap.left!!
        assertEquals(listOf(-1, 1, 2, 7, 0), left.options.map { it.code })
        assertEquals("pause", left.keyOf(1))
        assertEquals(listOf(SettingOption(-1, "off"), SettingOption(0, "answer")), doubleTap.inCall!!.options)
        // Swipe carries one mirrored value, so it has no right-side picker.
        assertNull(model.gestures[1].right)
        val equalizer = model.equalizer!!
        assertEquals(OptionGroup.EQUALIZER, equalizer.group)
        assertEquals("bass", equalizer.keyOf(2))
        assertEquals("quality", model.soundQuality!!.keyOf(1))
        assertEquals("en-GB", model.language!!.current)
        assertEquals(true, model.multipointEnabled)
        assertEquals(setOf("wear", "gestures", "equalizer", "lowLatency", "soundQuality", "multipoint", "language"), model.experimental)
    }

    @Test
    fun hidesUnlistedUnreportedAndUnansweredControls() {
        val basic = profile("""{"id": "b", "name": "B", "capabilities": {"battery": {}, "anc": {}}}""")
        val none = settingsModel(basic, BudsState(settings = fullSettings, multipointEnabled = true))
        assertNull(none.wear)
        assertEquals(emptyList<Any>(), none.gestures)
        assertNull(none.equalizer)
        assertNull(none.language)
        assertNull(none.multipointEnabled)

        val unreported = settingsModel(freebuds6, BudsState())
        assertNull(unreported.wear)
        assertEquals(emptyList<Any>(), unreported.gestures)
        assertNull(unreported.multipointEnabled)

        val unanswered = fullSettings.copy(unanswered = setOf("wear", "gestures.doubleTap", "equalizer", "multipoint"))
        val model = settingsModel(freebuds6, BudsState(settings = unanswered, multipointEnabled = true))
        assertNull(model.wear)
        assertEquals(listOf("swipe"), model.gestures.map { it.subKey })
        assertNull(model.equalizer)
        assertNull(model.multipointEnabled)
        assertEquals(false, model.lowLatency)
    }

    @Test
    fun verifiedCapabilityIsNotExperimental() {
        val p = profile("""{"id": "v", "name": "V", "capabilities": {"wear": {"verified": "2026-10-01"}, "lowLatency": {"verified": null}}}""")
        assertEquals(setOf("lowLatency"), settingsModel(p, BudsState()).experimental)
    }

    @Test
    fun withoutAProfileTableTheDeviceCodesAreOfferedUnnamed() {
        val p = profile("""{"id": "g", "name": "G", "capabilities": {"gestures": {"tripleTap": {}}}}""")
        val settings = DeviceSettings(gestures = mapOf(Gesture.TRIPLE_TAP to GestureSetting(1, 1, null, supported = listOf(-1, 1, 5))))
        val control = settingsModel(p, BudsState(settings = settings)).gestures.single()
        val left = control.left!!
        assertEquals(listOf(-1, 1, 5), left.options.map { it.code })
        assertNull(left.keyOf(5))
    }

    @Test
    fun pickerWithoutOptionsIsHidden() {
        // No profile table and no device list: nothing to choose from, so no row at all.
        val p = profile(
            """{"id": "e", "name": "E", "capabilities": {"gestures": {"tripleTap": {}, "longPress": {"inCall": true}}, "equalizer": {}, "soundQuality": {}}}""",
        )
        val settings = DeviceSettings(
            gestures = mapOf(
                Gesture.TRIPLE_TAP to GestureSetting(1, 1, null, supported = emptyList()),
                Gesture.LONG_PRESS to GestureSetting(1, 1, 0, supported = listOf(-1, 1)),
            ),
            equalizer = EqualizerState(active = 1, available = emptyList()),
            soundQuality = 0,
        )
        val model = settingsModel(p, BudsState(settings = settings))
        assertEquals(listOf("longPress"), model.gestures.map { it.subKey })
        // Long press has options for its sides but none for the in-call action.
        assertNull(model.gestures.single().inCall)
        assertNull(model.equalizer)
        assertNull(model.soundQuality)
    }

    @Test
    fun knownKeysHaveStringsAndUnknownKeysFallBack() {
        assertEquals(R.string.gesture_pause, optionLabelRes(OptionGroup.GESTURE, "pause"))
        assertEquals(R.string.noise_cycle_off_on_awareness, optionLabelRes(OptionGroup.NOISE_CYCLE, "off_on_awareness"))
        assertEquals(R.string.eq_voice, optionLabelRes(OptionGroup.EQUALIZER, "voice"))
        assertEquals(R.string.sound_connectivity, optionLabelRes(OptionGroup.SOUND_QUALITY, "connectivity"))
        assertNull(optionLabelRes(OptionGroup.GESTURE, "teleport"))
        assertNull(optionLabelRes(OptionGroup.EQUALIZER, null))
    }

    @Test
    fun controlKeysSeparateSidesAndHosts() {
        val left = SettingChange.GestureChange(Gesture.DOUBLE_TAP, left = 1, right = null, inCall = null)
        val right = SettingChange.GestureChange(Gesture.DOUBLE_TAP, left = null, right = 1, inCall = null)
        assertNotEquals(left.controlKey(), right.controlKey())
        assertEquals(
            SettingChange.PreferredHost("AA:00:00:00:00:01").controlKey(),
            SettingChange.PreferredHost("AA:00:00:00:00:02").controlKey(),
        )
    }
}
