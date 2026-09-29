// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import io.github.librebuds.protocol.beacon.Beacon
import io.github.librebuds.protocol.beacon.FdeeBeacon
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.protocol.util.hexToBytes

/**
 * Fixed beacon for the Settings "Show test popup" debug action: close range, reference RSSI -70,
 * model 000155 (a FreeBuds 6), left 100% charging, right 80%, case 48%.
 */
const val DEMO_BEACON_HEX = "01 01 01 02 BA 03 00 01 55 0C E4 0D 50 0E 30"

/** Parses [DEMO_BEACON_HEX]; null only if the constant itself is malformed. */
fun demoBeacon(): Beacon? = FdeeBeacon.parse(DEMO_BEACON_HEX.hexToBytes())

/**
 * The popup model "Show test popup" shows: the demo beacon matched against [registry] (the generic
 * profile when unmatched). Bypasses [PopupRules] and the cooldown entirely.
 */
fun demoPopupModel(registry: ProfileRegistry): PopupModel? =
    demoBeacon()?.let { beacon -> popupModel(beacon, registry.match(modelId = beacon.modelId)) }
