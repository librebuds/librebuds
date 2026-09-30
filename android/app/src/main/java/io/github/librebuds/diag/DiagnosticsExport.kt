// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.diag

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** What the first line of the diagnostics export reports about the app, the phone and the link. */
data class DiagnosticsHeader(
    val versionName: String,
    val versionCode: Int,
    val commit: String,
    val sdkInt: Int,
    val release: String,
    val manufacturer: String,
    val model: String,
    val bluetoothEnabled: Boolean?,
    val connectGranted: Boolean,
    val scanGranted: Boolean,
    val notificationsGranted: Boolean,
    val overlayGranted: Boolean,
    val associations: Int?,
    val link: String,
    val lastError: String?,
    val profileId: String,
    val frames: Int,
    val exportedAt: Long,
) {
    fun toJson(): String = buildJsonObject {
        put("type", JsonPrimitive("header"))
        put("versionName", JsonPrimitive(versionName))
        put("versionCode", JsonPrimitive(versionCode))
        put("commit", JsonPrimitive(commit))
        put("sdkInt", JsonPrimitive(sdkInt))
        put("release", JsonPrimitive(release))
        put("manufacturer", JsonPrimitive(manufacturer))
        put("model", JsonPrimitive(model))
        put("bluetoothEnabled", JsonPrimitive(bluetoothEnabled))
        put("permissions", buildJsonObject {
            put("connect", JsonPrimitive(connectGranted))
            put("scan", JsonPrimitive(scanGranted))
            put("notifications", JsonPrimitive(notificationsGranted))
            put("overlay", JsonPrimitive(overlayGranted))
        })
        put("associations", JsonPrimitive(associations))
        put("link", JsonPrimitive(link))
        put("lastError", JsonPrimitive(lastError))
        put("profileId", JsonPrimitive(profileId))
        put("frames", JsonPrimitive(frames))
        put("exportedAt", JsonPrimitive(exportedAt))
    }.toString()
}

/**
 * The diagnostics file: the header line, then recent app events, then the frames with the serial
 * number and full addresses removed; one JSON object per line.
 */
object DiagnosticsExport {
    fun build(header: DiagnosticsHeader, events: EventLog, frames: FrameLog): String =
        listOf(header.toJson(), events.toJsonl(), frames.toExportJsonl())
            .filter { it.isNotEmpty() }
            .joinToString("\n", postfix = "\n")
}
