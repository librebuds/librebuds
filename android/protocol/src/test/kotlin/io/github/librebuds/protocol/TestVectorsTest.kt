package io.github.librebuds.protocol

import io.github.librebuds.protocol.frame.FrameReassembler
import io.github.librebuds.protocol.frame.RxEvent
import io.github.librebuds.protocol.util.hexToBytes
import io.github.librebuds.protocol.util.toHex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File

class TestVectorsTest {
    private fun vectors(): List<JsonObject> =
        File(repoRoot(), "test-vectors").walkTopDown()
            // beacon/ holds BLE advertisement payloads, not link frames (see BeaconVectorsTest).
            .filter { it.isFile && it.extension == "jsonl" && it.parentFile.name != "beacon" }
            .sortedBy { it.path }
            .flatMap { file -> file.readLines().filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject } }
            .toList()

    @Test
    fun vectorSetIsNotEmptyAndIdsAreUnique() {
        val ids = vectors().map { it["id"]!!.jsonPrimitive.content }
        assertTrue(ids.size >= 23, "expected round 1 vectors, found ${ids.size}")
        assertEquals(ids.size, ids.toSet().size, "duplicate vector ids")
    }

    @TestFactory
    fun everyVectorDecodesToItsExpectedPacket(): List<DynamicTest> = vectors().map { vector ->
        val id = vector["id"]!!.jsonPrimitive.content
        DynamicTest.dynamicTest(id) {
            val events = FrameReassembler().feed(vector["hex"]!!.jsonPrimitive.content.hexToBytes())
            val payload = events.single() as RxEvent.Payload
            val packet = Packet.fromPayload(payload.bytes)!!
            val expect = vector["expect"]!!.jsonObject
            assertEquals(expect["cmd"]!!.jsonPrimitive.content, packet.id.toString())
            for ((type, value) in expect["tlv"]!!.jsonObject) {
                assertEquals(value.jsonPrimitive.content, packet.find(type.toInt())?.toHex(), "$id TLV $type")
            }
        }
    }
}
