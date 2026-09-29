// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.beacon

import io.github.librebuds.protocol.beacon.FdeeBeacon
import io.github.librebuds.protocol.util.toHex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The most recently parsed case-open beacon, for the Settings "Last beacon" debug row. Nothing here is
 * anonymised (it never leaves the device), but a shared screenshot or copy must have any serial-like
 * bytes replaced first, same as the diagnostics export.
 */
data class LastBeacon(
    val serviceDataHex: String,
    val modelId: String?,
    val subModelId: Int?,
    val rssi: Int,
    val referenceRssi: Int?,
    val atMillis: Long,
) {
    fun encode(): String = JsonObject(
        buildMap {
            put("serviceDataHex", JsonPrimitive(serviceDataHex))
            modelId?.let { put("modelId", JsonPrimitive(it)) }
            subModelId?.let { put("subModelId", JsonPrimitive(it)) }
            put("rssi", JsonPrimitive(rssi))
            referenceRssi?.let { put("referenceRssi", JsonPrimitive(it)) }
            put("atMillis", JsonPrimitive(atMillis))
        }
    ).toString()

    companion object {
        fun decode(text: String?): LastBeacon? {
            if (text == null) return null
            val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
            val serviceDataHex = root["serviceDataHex"]?.jsonPrimitive?.contentOrNull ?: return null
            val rssi = root["rssi"]?.jsonPrimitive?.intOrNull ?: return null
            val atMillis = root["atMillis"]?.jsonPrimitive?.longOrNull ?: return null
            return LastBeacon(
                serviceDataHex = serviceDataHex,
                modelId = root["modelId"]?.jsonPrimitive?.contentOrNull,
                subModelId = root["subModelId"]?.jsonPrimitive?.intOrNull,
                rssi = rssi,
                referenceRssi = root["referenceRssi"]?.jsonPrimitive?.intOrNull,
                atMillis = atMillis,
            )
        }
    }
}

/**
 * The last of [results] whose service data parses as a beacon, for the "Last beacon" debug row. Looks
 * at every result (not just the ones a popup would show), so a stranger's or a far-away beacon still
 * updates it; results with no or unparsable service data are skipped.
 */
fun lastBeaconOf(results: List<RawSighting>, now: Long): LastBeacon? {
    for (result in results.asReversed()) {
        val serviceData = result.serviceData ?: continue
        val beacon = FdeeBeacon.parse(serviceData) ?: continue
        return LastBeacon(
            serviceDataHex = serviceData.toHex(),
            modelId = beacon.modelId,
            subModelId = beacon.subModelId,
            rssi = result.rssi,
            referenceRssi = beacon.referenceRssi,
            atMillis = now,
        )
    }
    return null
}

/**
 * Whether [next] should replace the stored [previous] "Last beacon": when the service data changed, or
 * at most once per [minIntervalMillis] for the same data, so a case sitting open does not write the
 * preferences on every scan batch. A time before the stored one (clock set back) also stores.
 */
fun shouldStoreLastBeacon(previous: LastBeacon?, next: LastBeacon, minIntervalMillis: Long = 10_000): Boolean {
    if (previous == null || previous.serviceDataHex != next.serviceDataHex) return true
    return next.atMillis - previous.atMillis !in 0 until minIntervalMillis
}
