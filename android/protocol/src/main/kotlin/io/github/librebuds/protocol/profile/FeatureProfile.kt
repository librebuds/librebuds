// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.profile

import io.github.librebuds.protocol.command.AncMode
import io.github.librebuds.protocol.command.Feature
import io.github.librebuds.protocol.command.PinchSlot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** One code with the profile's semantic name for it. */
data class NamedOption(val code: Int, val key: String)

/** A pinch slot the profile lists, with the actions it offers in display order. */
data class PinchSlotSpec(val slot: PinchSlot, val options: List<NamedOption>)

/** Capability key of each feature switch in the profile documents. */
val FEATURE_CAPABILITIES: Map<Feature, String> = mapOf(
    Feature.SINGLE_BUD_ANC to "singleBudAnc",
    Feature.ADAPTIVE_VOLUME to "adaptiveVolume",
    Feature.AI_CONVERSATION to "aiConversation",
    Feature.DROP_DETECTION to "dropDetection",
    Feature.HEAD_CONTROL to "headControl",
    Feature.EAR_TIP to "earTip",
)

/** The feature switches the profile lists. */
fun Profile.features(): List<Feature> = FEATURE_CAPABILITIES.filter { supports(it.value) }.keys.toList()

/**
 * Awareness sub-modes (`anc.awarenessModes`, in display order: 2 standard, 1 voice, 4 adaptive).
 * Empty when the profile lists none or does not offer awareness.
 */
fun Profile.awarenessModes(): List<Int> {
    if (AncMode.AWARENESS !in ancModes()) return emptyList()
    return (capabilities["anc"]?.get("awarenessModes") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }?.distinct().orEmpty()
}

/** Whether the profile offers the adaptive awareness slider (`anc.awarenessLevel`). */
fun Profile.hasAwarenessLevel(): Boolean = (capabilities["anc"]?.get("awarenessLevel") as? JsonPrimitive)?.booleanOrNull == true

/** The pinch slots of the `pinch` capability, each with its options. */
fun Profile.pinchSlots(): List<PinchSlotSpec> {
    val slots = capabilities["pinch"]?.get("slots") as? JsonArray ?: return emptyList()
    return slots.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val type = (obj["type"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        val scene = (obj["scene"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        PinchSlotSpec(PinchSlot(type, scene), named(obj["options"] as? JsonObject))
    }
}

/** Head control's nod and shake actions (`headControl.actions`). */
fun Profile.headActions(): List<NamedOption> = named(capabilities["headControl"]?.get("actions") as? JsonObject)

/** The extended equalizer presets with their ten gains (`equalizer.extended`), in the profile's order. */
fun Profile.extendedPresets(): Map<Int, List<Int>> {
    val table = capabilities["equalizer"]?.get("extended") as? JsonObject ?: return emptyMap()
    return table.mapNotNull { (id, gains) ->
        val values = (gains as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull } ?: return@mapNotNull null
        id.toIntOrNull()?.takeIf { values.size == 10 }?.let { it to values }
    }.toMap()
}

/** Whether the profile offers custom equalizer presets (`equalizer.custom`). */
fun Profile.customEqualizer(): Boolean = (capabilities["equalizer"]?.get("custom") as? JsonPrimitive)?.booleanOrNull == true

private fun named(table: JsonObject?): List<NamedOption> = table?.mapNotNull { (code, key) ->
    val name = (key as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
    code.toIntOrNull()?.let { NamedOption(it, name) }
}.orEmpty()
