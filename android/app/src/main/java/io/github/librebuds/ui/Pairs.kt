// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import io.github.librebuds.companion.disambiguatedLabels
import io.github.librebuds.protocol.profile.Profile
import io.github.librebuds.state.BudsState
import io.github.librebuds.state.DEMO_ADDRESS
import io.github.librebuds.state.DEMO_NAME
import io.github.librebuds.state.LinkState
import io.github.librebuds.state.batterySummaryNoBreak

/**
 * One pair of FreeBuds the app can show, with what is known about it right now. [label] is what the
 * pair switcher shows (it may add the address to [name] when another pair shares the same name);
 * [name] is the plain Bluetooth name, used as the device screen title. [connected] is the real
 * Bluetooth state toward the phone (ACL, A2DP or headset), or the controller's link for the pair it
 * holds. [battery] is only set while [connected], so a reading never passes for live;
 * [lastSeenMillis], set only while not connected, is when the last known values were updated.
 */
data class PairRow(
    val address: String,
    val name: String,
    val model: String?,
    val connected: Boolean,
    val battery: String?,
    val lastSeenMillis: Long? = null,
    val label: String = name,
    /** The profile's `art` shape, which picks the product image. */
    val art: String = "generic",
    val selected: Boolean = false,
)

/**
 * The pairs to choose from: the detected FreeBuds (battery from the controller when it holds that
 * pair), plus the demo earbuds first when [showDemo]. Connected pairs come first, then by name, the
 * order the switcher lists them in. [selected] marks the pair on screen. Two detected pairs with the
 * same name get the address appended to their label so they are tellable apart.
 */
fun pairRows(
    detection: Detection,
    state: BudsState,
    showDemo: Boolean,
    profileOf: (String) -> Profile?,
    selected: String? = null,
): List<PairRow> {
    fun row(address: String, name: String, model: String?, audioUp: Boolean, label: String = name, art: String = "generic"): PairRow {
        val current = address.equals(state.address, ignoreCase = true)
        val connected = audioUp || (current && state.isConnected)
        return PairRow(
            address = address,
            name = name,
            model = model,
            connected = connected,
            battery = state.battery?.takeIf { current && state.isConnected }?.let(::batterySummaryNoBreak),
            lastSeenMillis = state.updatedAtMillis?.takeIf { current && !connected },
            label = label,
            art = art,
            selected = address.equals(selected, ignoreCase = true),
        )
    }
    val demoProfile = profileOf(state.profileId)
    val demo = if (showDemo) listOf(row(DEMO_ADDRESS, DEMO_NAME, demoProfile?.name, audioUp = false, art = demoProfile?.art ?: "generic")) else emptyList()
    val labels = disambiguatedLabels(detection.buds)
    val detected = detection.buds.map {
        row(it.address, it.name, it.model, detection.isConnected(it.address), labels.getValue(it.address), profileOf(it.profileId)?.art ?: "generic")
    }.sortedWith(compareByDescending<PairRow> { it.connected }.thenBy { it.label.lowercase() })
    return demo + detected
}

/**
 * The pair the app opens on, by address, or null when there is none (no FreeBuds paired):
 * 1. a pair connected to the phone: the one the controller holds with a live or pending link, else
 *    the most recently connected ([lastConnectedAt], by uppercase address);
 * 2. none connected: the pair the controller used last ([state] survives restarts), if still paired;
 * 3. else the most recently connected pair ever, else the first one listed.
 * Ties fall back to the order of [pairs].
 */
fun chooseStartPair(pairs: List<PairRow>, lastConnectedAt: Map<String, Long>, state: BudsState): String? {
    if (pairs.isEmpty()) return null
    val held = state.address?.takeIf { state.link != LinkState.DISCONNECTED }
    val connected = pairs.filter { it.connected }
    if (connected.isNotEmpty()) {
        connected.firstOrNull { it.address.equals(held, ignoreCase = true) }?.let { return it.address }
        return connected.newest(lastConnectedAt).address
    }
    pairs.firstOrNull { it.address.equals(state.address, ignoreCase = true) }?.let { return it.address }
    return pairs.newest(lastConnectedAt).address
}

/** The entry with the newest connect time; the first listed among equals (and among never connected). */
private fun List<PairRow>.newest(lastConnectedAt: Map<String, Long>): PairRow {
    var best = first()
    var bestAt = lastConnectedAt[best.address.uppercase()] ?: Long.MIN_VALUE
    for (row in drop(1)) {
        val at = lastConnectedAt[row.address.uppercase()] ?: Long.MIN_VALUE
        if (at > bestAt) {
            best = row
            bestAt = at
        }
    }
    return best
}

/**
 * The pair switcher row's subtitle: model, connection state, and whichever one of [battery] (while
 * connected) or [lastSeenText] (while not, when a last known update time exists) fits. Kept as a
 * plain function so it is unit testable.
 */
fun pairDescriptionText(model: String, connectedLabel: String, battery: String?, lastSeenText: String?): String =
    listOfNotNull(model, connectedLabel, battery ?: lastSeenText).joinToString(" · ")
