// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.profile

import io.github.librebuds.protocol.command.AncMode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Noise-control modes the profile's `anc.modes` lists (`off`, `cancellation`, `awareness`), in
 * [AncMode] order. Without a `modes` list (most profiles, and the generic one) all three are
 * offered; unknown names are ignored. Empty when the profile has no `anc` capability.
 */
fun Profile.ancModes(): List<AncMode> {
    val anc = capabilities["anc"] ?: return emptyList()
    val listed = (anc["modes"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.let(ANC_MODE_NAMES::get) }
        ?.toSet()
        ?: return AncMode.entries.toList()
    return AncMode.entries.filter { it in listed }
}

/**
 * Cancellation level bytes the profile's `anc.cancellationLevels` lists, in the profile's order
 * (the order the level picker shows them). Empty when the profile lists none, or does not offer
 * cancellation at all.
 */
fun Profile.cancellationLevels(): List<Int> {
    if (AncMode.CANCELLATION !in ancModes()) return emptyList()
    val listed = capabilities["anc"]?.get("cancellationLevels") as? JsonArray ?: return emptyList()
    return listed.mapNotNull { (it as? JsonPrimitive)?.intOrNull?.takeIf { level -> level in 0..0xFE } }.distinct()
}

/**
 * The semantic name of a cancellation level byte: the profile's `anc.cancellationLevelNames`
 * entry when it has one, otherwise the meaning the byte has across models (0 general, 1 cozy,
 * 2 ultra, 3 dynamic). Null for a byte with no known meaning.
 */
fun Profile.cancellationLevelKey(level: Int): String? {
    val names = capabilities["anc"]?.get("cancellationLevelNames") as? JsonObject
    val named = (names?.get(level.toString()) as? JsonPrimitive)?.takeIf { it.isString }?.content
    return named ?: CANCELLATION_LEVEL_KEYS[level]
}

private val ANC_MODE_NAMES = mapOf(
    "off" to AncMode.OFF,
    "cancellation" to AncMode.CANCELLATION,
    "awareness" to AncMode.AWARENESS,
)

/** Cancellation level bytes as the vendor app and OpenFreebuds both read them. */
val CANCELLATION_LEVEL_KEYS: Map<Int, String> = mapOf(
    0 to "general",
    1 to "cozy",
    2 to "ultra",
    3 to "dynamic",
)
