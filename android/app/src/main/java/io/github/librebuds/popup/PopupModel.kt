// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import io.github.librebuds.protocol.beacon.Beacon
import io.github.librebuds.protocol.beacon.BeaconBattery
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.state.BudsState
import io.github.librebuds.ui.model.Battery
import io.github.librebuds.ui.model.BatteryComponent
import io.github.librebuds.ui.model.BatteryStatus
import io.github.librebuds.ui.model.toUiBatteries

data class PopupModel(val title: String, val art: String, val batteries: List<Battery>, val connected: Boolean)

fun popupModel(beacon: Beacon, profile: Profile): PopupModel {
    fun ui(component: Int, battery: BeaconBattery?) = battery?.let {
        Battery(component, it.percent, if (it.charging) BatteryStatus.CHARGING else BatteryStatus.NOT_CHARGING)
    }
    val batteries = listOfNotNull(
        ui(BatteryComponent.LEFT, beacon.left),
        ui(BatteryComponent.RIGHT, beacon.right),
        ui(BatteryComponent.CASE, beacon.case),
    )
    return PopupModel(title = profile.name, art = profile.art, batteries = batteries, connected = false)
}

/** Once the earbuds connect, the exact battery from the control channel replaces the beacon values. */
fun PopupModel.withState(state: BudsState): PopupModel {
    if (!state.isConnected) return this
    val live = state.battery.toUiBatteries()
    return copy(batteries = live.ifEmpty { batteries }, connected = true)
}
