// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import android.content.Context
import androidx.core.content.edit
import io.github.librebuds.protocol.beacon.Beacon
import io.github.librebuds.protocol.beacon.BeaconBattery
import io.github.librebuds.protocol.beacon.LidState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Battery levels from the beacons of one case opening; each one is the last value seen. */
data class BudBatteries(val left: BeaconBattery? = null, val right: BeaconBattery? = null, val case: BeaconBattery? = null) {
    /** A level missing from [beacon] never erases one already known. */
    fun mergedWith(beacon: Beacon) = BudBatteries(beacon.left ?: left, beacon.right ?: right, beacon.case ?: case)
}

/**
 * One case opening of a model, tracked from its compact beacons (see [CaseOpenings]).
 * [open] is false once a closed beacon arrived; [shown] once the popup was raised for this opening.
 */
data class Opening(val open: Boolean, val lastOpenAt: Long, val shown: Boolean, val batteries: BudBatteries)

/**
 * Follows the lid of each model across scan batches, so the popup fires once per opening.
 *
 * An open-lid beacon starts an opening when the previous beacon said closed, or when no open beacon
 * came for [GAP_MILLIS] (the closed beacons are rare and easy to miss). A closed or just-closed beacon
 * ends it. Buds-out beacons (also sent the whole time they are worn) neither start nor end one; they
 * only refresh the bud batteries of an opening in progress.
 */
object CaseOpenings {
    const val GAP_MILLIS = 30_000L

    /** The state after [beacon] arrived at [now], given the [previous] state of its key. */
    fun step(previous: Opening?, beacon: Beacon, now: Long): Opening? = when (beacon.lid) {
        LidState.OPEN -> {
            val continues = previous != null && previous.open && now - previous.lastOpenAt in 0..GAP_MILLIS
            if (continues) previous!!.copy(lastOpenAt = now, batteries = previous.batteries.mergedWith(beacon))
            else Opening(open = true, lastOpenAt = now, shown = false, batteries = BudBatteries().mergedWith(beacon))
        }
        LidState.CLOSED, LidState.TRANSITIONAL -> previous?.copy(open = false, shown = false, batteries = BudBatteries())
        LidState.BUDS_OUT -> previous?.takeIf { it.open }?.let { it.copy(batteries = it.batteries.mergedWith(beacon)) } ?: previous
        null -> previous
    }

    fun encode(openings: Map<String, Opening>): String = JsonObject(
        openings.mapValues { (_, o) ->
            JsonObject(
                mapOf(
                    "open" to JsonPrimitive(o.open),
                    "lastOpenAt" to JsonPrimitive(o.lastOpenAt),
                    "shown" to JsonPrimitive(o.shown),
                    "left" to o.batteries.left.encode(),
                    "right" to o.batteries.right.encode(),
                    "case" to o.batteries.case.encode(),
                )
            )
        }
    ).toString()

    fun decode(text: String?): Map<String, Opening> {
        if (text == null) return emptyMap()
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyMap()
        return root.mapNotNull { (key, value) ->
            runCatching {
                val o = value.jsonObject
                key to Opening(
                    open = o["open"]!!.jsonPrimitive.booleanOrNull!!,
                    lastOpenAt = o["lastOpenAt"]!!.jsonPrimitive.longOrNull!!,
                    shown = o["shown"]!!.jsonPrimitive.booleanOrNull!!,
                    batteries = BudBatteries(o["left"].battery(), o["right"].battery(), o["case"].battery()),
                )
            }.getOrNull()
        }.toMap()
    }

    /** Drops openings not seen for an hour (or stamped in the future), so the store stays small. */
    fun prune(openings: Map<String, Opening>, now: Long): Map<String, Opening> =
        openings.filterValues { now - it.lastOpenAt in 0 until PopupTimes.MAX_AGE_MILLIS }

    private fun BeaconBattery?.encode(): JsonElement = this?.let { JsonPrimitive(it.percent or if (it.charging) 0x80 else 0) } ?: JsonNull

    private fun JsonElement?.battery(): BeaconBattery? =
        (this as? JsonPrimitive)?.takeIf { it != JsonNull }?.intOrNull?.let(BeaconBattery::decode)
}

/** The [CaseOpenings] state, kept in its own preferences file so it survives the receiver's process. */
class CaseOpeningStore(context: Context) {
    private val prefs = context.getSharedPreferences("beacon_openings", Context.MODE_PRIVATE)

    fun load(): Map<String, Opening> = CaseOpenings.decode(prefs.getString(KEY, null))

    fun save(openings: Map<String, Opening>) {
        prefs.edit { putString(KEY, CaseOpenings.encode(openings)) }
    }

    private companion object {
        const val KEY = "openings"
    }
}
