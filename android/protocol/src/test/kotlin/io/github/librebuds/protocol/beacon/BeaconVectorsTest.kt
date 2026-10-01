package io.github.librebuds.protocol.beacon

import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.protocol.repoRoot
import io.github.librebuds.protocol.util.hexToBytes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File

/** FDEE service data captured from real earbuds (hardware round 2), see test-vectors/beacon/. */
class BeaconVectorsTest {
    private fun vectors(): List<JsonObject> =
        File(repoRoot(), "test-vectors/beacon").listFiles { f -> f.extension == "jsonl" }!!.sortedBy { it.name }
            .flatMap { file -> file.readLines().filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject } }

    private val JsonObject.expect get() = this["expect"]!!.takeIf { it != JsonNull }?.jsonObject

    @Test
    fun everyRoundTwoModelHasClosedOrTransitionalAndOpenVectors() {
        val profiles = ProfileRegistry.fromJson(
            File(repoRoot(), "profiles").listFiles { f -> f.extension == "json" }!!.map { it.readText() }
        )
        val compact = vectors().mapNotNull { it.expect }.filter { it["format"]!!.jsonPrimitive.content == "COMPACT" }
        val models = listOf("000027", "000129", "000141", "000155", "000131", "000149", "000157", "00016B")
        for (model in models) {
            val lids = compact.filter { it["modelId"]!!.jsonPrimitive.content == model }.map { it["lid"]!!.jsonPrimitive.content }.toSet()
            assertTrue("OPEN" in lids, "$model has no open-case vector")
            // The FreeBuds 3 was only ever captured with its lid open.
            assertTrue(model == "000027" || lids.any { it == "CLOSED" || it == "TRANSITIONAL" }, "$model has no closed vector")
            assertTrue(profiles.match(modelId = model).id != ProfileRegistry.GENERIC.id, "no profile lists modelId $model")
        }
    }

    @TestFactory
    fun everyVectorDecodesAsCaptured(): List<DynamicTest> = vectors().map { vector ->
        val id = vector["id"]!!.jsonPrimitive.content
        DynamicTest.dynamicTest(id) {
            val beacon = FdeeBeacon.parse(vector["hex"]!!.jsonPrimitive.content.hexToBytes())
            val expect = vector.expect
            if (expect == null) {
                assertNull(beacon, id)
                return@dynamicTest
            }
            beacon!!
            fun text(key: String) = expect[key]!!.jsonPrimitive.content
            assertEquals(text("format"), beacon.format.name, id)
            assertEquals(text("modelId"), beacon.modelId, id)
            assertEquals(expect["subModelId"]!!.jsonPrimitive.int, beacon.subModelId, id)
            assertEquals(expect["caseOpen"]!!.jsonPrimitive.boolean, beacon.caseOpen, id)
            if (beacon.format == BeaconFormat.CLOSE_RANGE) {
                assertEquals(expect["closeRangeEnabled"]!!.jsonPrimitive.boolean, beacon.closeRangeEnabled, id)
                return@dynamicTest
            }
            assertEquals(text("flags"), "%04X".format(beacon.flags), id)
            assertEquals(text("lid"), beacon.lid?.name, id)
            assertEquals(text("state"), "%02X".format(beacon.state), id)
            for ((key, actual) in listOf("case" to beacon.case, "left" to beacon.left, "right" to beacon.right)) {
                val battery = expect[key]!!.takeIf { it != JsonNull }?.jsonObject?.let {
                    BeaconBattery(it["percent"]!!.jsonPrimitive.int, it["charging"]!!.jsonPrimitive.boolean)
                }
                assertEquals(battery, actual, "$id $key")
            }
        }
    }
}
