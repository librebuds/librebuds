// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import io.github.librebuds.R
import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.protocol.command.GestureSetting
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.DeviceSettings
import io.github.librebuds.ui.model.NoiseControlMode
import io.github.librebuds.ui.model.NoiseControlMode.AWARENESS
import io.github.librebuds.ui.model.NoiseControlMode.NOISE_CANCELLATION
import io.github.librebuds.ui.model.NoiseControlMode.OFF
import io.github.librebuds.ui.model.OptionGroup
import io.github.librebuds.ui.model.cancellationLevelPicker
import io.github.librebuds.ui.model.keyOf
import io.github.librebuds.ui.model.listedMode
import io.github.librebuds.ui.model.offeredModes
import io.github.librebuds.ui.model.optionLabelRes
import io.github.librebuds.ui.model.settingsModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoiseControlModelTest {
    private fun profile(anc: String, extra: String = ""): Profile =
        ProfileRegistry.fromJson(listOf("""{"id":"p","name":"P","capabilities":{"anc":$anc$extra}}""")).profiles.single()

    private val freebuds5 = profile("""{"modes":["off","cancellation"],"cancellationLevels":[3,1,0]}""")
    private val threeModes = profile("""{"cancellationLevels":[3]}""")

    @Test
    fun offersOnlyTheProfileModes() {
        assertEquals(listOf(OFF, NOISE_CANCELLATION), offeredModes(freebuds5, showOff = true))
        assertEquals(listOf(OFF, NOISE_CANCELLATION, AWARENESS), offeredModes(threeModes, showOff = true))
        assertEquals(NoiseControlMode.entries, offeredModes(ProfileRegistry.GENERIC, showOff = true))
    }

    @Test
    fun hidingOffNeverLeavesASingleMode() {
        assertEquals(listOf(NOISE_CANCELLATION, AWARENESS), offeredModes(threeModes, showOff = false))
        assertEquals(listOf(OFF, NOISE_CANCELLATION), offeredModes(freebuds5, showOff = false))
    }

    @Test
    fun widgetButtonsOnlySendListedModes() {
        assertEquals(AncMode.CANCELLATION, listedMode(1, freebuds5))
        assertEquals(AncMode.OFF, listedMode(0, freebuds5))
        assertNull(listedMode(2, freebuds5))
        assertNull(listedMode(7, freebuds5))
        assertEquals(AncMode.AWARENESS, listedMode(2, threeModes))
    }

    @Test
    fun levelPickerFollowsTheProfileOrder() {
        val picker = cancellationLevelPicker(freebuds5, 1)!!
        assertEquals(1, picker.current)
        assertEquals(OptionGroup.CANCELLATION_LEVEL, picker.group)
        assertEquals(listOf(3 to "dynamic", 1 to "cozy", 0 to "general"), picker.options.map { it.code to it.key })
        assertNull(cancellationLevelPicker(profile("""{"modes":["off","cancellation"]}"""), 3))
    }

    @Test
    fun levelOutsideTheListIsNamedButNotOffered() {
        val ultra = cancellationLevelPicker(threeModes, 2)!!
        assertEquals(listOf(3), ultra.options.map { it.code })
        assertEquals("ultra", ultra.keyOf(2))
        val unknown = cancellationLevelPicker(freebuds5, 5)!!
        assertEquals(listOf(3, 1, 0), unknown.options.map { it.code })
        assertNull(unknown.keyOf(5))
        assertNull(optionLabelRes(OptionGroup.CANCELLATION_LEVEL, unknown.keyOf(5)))
    }

    @Test
    fun levelKeysHaveStrings() {
        assertEquals(R.string.cancellation_level_dynamic, optionLabelRes(OptionGroup.CANCELLATION_LEVEL, "dynamic"))
        assertEquals(R.string.cancellation_level_cozy, optionLabelRes(OptionGroup.CANCELLATION_LEVEL, "cozy"))
        assertEquals(R.string.cancellation_level_general, optionLabelRes(OptionGroup.CANCELLATION_LEVEL, "general"))
        assertEquals(R.string.cancellation_level_ultra, optionLabelRes(OptionGroup.CANCELLATION_LEVEL, "ultra"))
    }

    private val noiseCycle = """,
        "gestures":{"noiseCycle":{"options":{"1":"off_on","2":"off_on_awareness","3":"on_awareness","4":"off_awareness"}}}"""

    private fun cycleState(current: Int) = BudsState(
        settings = DeviceSettings(gestures = mapOf(Gesture.NOISE_CYCLE to GestureSetting(left = current, right = current, inCall = null, supported = listOf(1, 2, 3, 4)))),
    )

    @Test
    fun noiseCycleNeverOffersAnUnlistedMode() {
        val fb5 = profile("""{"modes":["off","cancellation"]}""", noiseCycle)
        val left = settingsModel(fb5, cycleState(1)).gestures.single().left!!
        assertEquals(listOf(1), left.options.map { it.code })

        val all = profile("""{}""", noiseCycle)
        assertEquals(listOf(1, 2, 3, 4), settingsModel(all, cycleState(1)).gestures.single().left!!.options.map { it.code })
    }

    @Test
    fun noiseCycleKeepsTheCurrentUnlistedValueNamedWithoutOfferingIt() {
        val fb5 = profile("""{"modes":["off","cancellation"]}""", noiseCycle)
        val left = settingsModel(fb5, cycleState(2)).gestures.single().left!!
        assertEquals(2, left.current)
        assertEquals(listOf(1), left.options.map { it.code })
        assertEquals("off_on_awareness", left.keyOf(2))
    }
}
