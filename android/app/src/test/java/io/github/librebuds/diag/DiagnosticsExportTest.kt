// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.diag

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsExportTest {
    private val header = DiagnosticsHeader(
        versionName = "0.1.0",
        versionCode = 1,
        commit = "abc1234",
        sdkInt = 37,
        release = "17",
        manufacturer = "Google",
        model = "Pixel \"9\"",
        bluetoothEnabled = true,
        connectGranted = true,
        scanGranted = false,
        notificationsGranted = true,
        overlayGranted = false,
        associations = 0,
        link = "DISCONNECTED",
        lastError = null,
        profileId = "generic",
        frames = 0,
        exportedAt = 1234L,
    )

    @Test
    fun exportWithoutFramesStillHasTheHeader() {
        val text = DiagnosticsExport.build(header, EventLog(), FrameLog())
        assertTrue(text.isNotBlank())
        val lines = text.trimEnd('\n').split("\n")
        assertEquals(1, lines.size)
        val json = Json.parseToJsonElement(lines[0]).jsonObject
        assertEquals("header", json.getValue("type").jsonPrimitive.content)
        assertEquals("abc1234", json.getValue("commit").jsonPrimitive.content)
        assertEquals("Pixel \"9\"", json.getValue("model").jsonPrimitive.content)
        assertEquals(JsonNull, json.getValue("lastError"))
        assertEquals("false", json.getValue("permissions").jsonObject.getValue("overlay").jsonPrimitive.content)
    }

    @Test
    fun headerThenEventsThenFrames() {
        val events = EventLog(clock = { 1L }).apply { record("BudsController", "connect 11:22:33:44:55:66") }
        val frames = FrameLog(clock = { 2L }).apply { record(FrameDirection.TX, byteArrayOf(0x5A)) }
        val lines = DiagnosticsExport.build(header, events, frames).trimEnd('\n').split("\n")
        assertEquals(listOf("header", "event", "frame"), lines.map { Json.parseToJsonElement(it).jsonObject.getValue("type").jsonPrimitive.content })
        assertTrue(lines[1].contains("**:**:**:**:55:66"))
    }
}
