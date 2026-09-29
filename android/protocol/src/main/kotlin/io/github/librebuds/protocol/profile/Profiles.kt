package io.github.librebuds.protocol.profile

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class Match(
    val sku: List<String> = emptyList(),
    val modelId: List<String> = emptyList(),
    val productId: List<String> = emptyList(),
    val btName: List<String> = emptyList(),
)

/** One product. Capabilities are data so test results update JSON, not code. */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val match: Match = Match(),
    val art: String = "generic",
    val capabilities: Map<String, JsonObject> = emptyMap(),
) {
    fun supports(capability: String): Boolean = capability in capabilities

    /** Date the capability was confirmed on a real device, or null when unverified. */
    fun verifiedOn(capability: String): String? =
        (capabilities[capability]?.get("verified") as? JsonPrimitive)?.takeIf { it.isString }?.content
}

class ProfileRegistry(val profiles: List<Profile>) {
    /** Most reliable identifier first: SKU, then beacon ModelId, then the (user-renamable) Bluetooth name. */
    fun match(sku: String? = null, modelId: String? = null, btName: String? = null): Profile {
        sku?.let { value -> find { p -> p.match.sku.any { it.equals(value, ignoreCase = true) } }?.let { return it } }
        modelId?.let { value -> find { p -> p.match.modelId.any { it.equals(value, ignoreCase = true) } }?.let { return it } }
        btName?.trim()?.let { value -> find { p -> value in p.match.btName }?.let { return it } }
        return GENERIC
    }

    private fun find(predicate: (Profile) -> Boolean): Profile? = profiles.firstOrNull(predicate)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        val GENERIC = Profile(
            id = "generic",
            name = "FreeBuds",
            capabilities = mapOf("battery" to JsonObject(emptyMap()), "anc" to JsonObject(emptyMap())),
        )

        fun fromJson(documents: List<String>): ProfileRegistry =
            ProfileRegistry(documents.map { json.decodeFromString(Profile.serializer(), it) })
    }
}
