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
 * list shows (it may add the address to [name] when another pair shares the same name); [name] is the
 * plain Bluetooth name, used as the device screen title. [connected] is the real Bluetooth state
 * toward the phone (ACL, A2DP or headset), or the controller's link for the pair it holds; the list
 * shows connected pairs on top in full colour and greys out the rest. [battery] is only set while
 * [connected], so a reading never passes for live.
 */
data class PairRow(
    val address: String,
    val name: String,
    val model: String?,
    val connected: Boolean,
    val battery: String?,
    val label: String = name,
    /** The profile's `art` shape, which picks the product image when there is no [profileId] override. */
    val art: String = "generic",
    /** The profile id, which picks a model-specific product image when one exists (see ProductArt). */
    val profileId: String? = null,
) {
    /** Shown greyed out in the list: known, still tappable, but not connected to the phone. */
    val greyed: Boolean get() = !connected
}

/**
 * The pairs in list order: the demo earbuds first when [showDemo], then the connected pairs, then all
 * other known pairs (greyed out in the list). Within each group the most recently connected comes
 * first ([Detection.lastConnectedAt]), then by name, then by address, so the order never jumps
 * around between reads. Two detected pairs with the same name get the address appended to their
 * label so they are tellable apart.
 */
fun pairRows(
    detection: Detection,
    state: BudsState,
    showDemo: Boolean,
    profileOf: (String) -> Profile?,
): List<PairRow> {
    fun row(
        address: String,
        name: String,
        model: String?,
        audioUp: Boolean,
        label: String = name,
        art: String = "generic",
        profileId: String? = null,
    ): PairRow {
        val current = address.equals(state.address, ignoreCase = true)
        val connected = audioUp || (current && state.isConnected)
        return PairRow(
            address = address,
            name = name,
            model = model,
            connected = connected,
            battery = state.battery?.takeIf { current && state.isConnected }?.let(::batterySummaryNoBreak),
            label = label,
            art = art,
            profileId = profileId,
        )
    }
    val demoProfile = profileOf(state.profileId)
    val demo = if (showDemo) {
        listOf(row(DEMO_ADDRESS, DEMO_NAME, demoProfile?.name, audioUp = false, art = demoProfile?.art ?: "generic", profileId = demoProfile?.id))
    } else {
        emptyList()
    }
    val labels = disambiguatedLabels(detection.buds)
    val lastConnectedAt = detection.lastConnectedAt
    val detected = detection.buds.map {
        val profile = profileOf(it.profileId)
        row(it.address, it.name, it.model, detection.isConnected(it.address), labels.getValue(it.address), profile?.art ?: "generic", profile?.id)
    }.sortedWith(
        compareByDescending<PairRow> { it.connected }
            .thenByDescending { lastConnectedAt[it.address.uppercase()] ?: Long.MIN_VALUE }
            .thenBy { it.label.lowercase() }
            .thenBy { it.address.uppercase() }
    )
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

/**
 * The pair to open on top of the list when the app starts, or null to stay on the list:
 * [launchAddress] when the app was opened for a pair that is still known; else, when a pair is
 * connected to the phone or held by the controller, the one [chooseStartPair] picks; else the only
 * known pair, if there is just one. With several pairs and none connected the list shows.
 */
fun startPair(pairs: List<PairRow>, lastConnectedAt: Map<String, Long>, state: BudsState, launchAddress: String? = null): PairRow? {
    launchAddress?.let { address -> pairs.firstOrNull { it.address.equals(address, ignoreCase = true) }?.let { return it } }
    val held = state.address?.takeIf { state.link != LinkState.DISCONNECTED }
    val active = pairs.any { it.connected || it.address.equals(held, ignoreCase = true) }
    if (active) return chooseStartPair(pairs, lastConnectedAt, state)?.let { address -> pairs.first { it.address == address } }
    return pairs.singleOrNull()
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
 * The list row's subtitle: the model, plus the battery while connected. A greyed-out row says nothing
 * more; its grey already tells it is not connected. Kept as a plain function so it is unit testable.
 */
fun pairDescriptionText(model: String, battery: String?): String =
    listOfNotNull(model, battery).joinToString(" · ")
