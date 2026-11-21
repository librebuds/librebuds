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
    /**
     * Prefer exact hardware identifiers. Bluetooth names are user-renamable, so match
     * only a complete known name (case/spacing independent), with a strictly formatted
     * four-hex-character factory suffix when present. Never use substring matching:
     * FreeBuds 6 and 6i, Pro and Pro 2, etc. must not be confused.
     */
    fun match(sku: String? = null, modelId: String? = null, btName: String? = null): Profile {
        sku?.trim()?.let { value -> find { p -> p.match.sku.any { it.equals(value, ignoreCase = true) } }?.let { return it } }
        modelId?.trim()?.let { value -> find { p -> p.match.modelId.any { it.equals(value, ignoreCase = true) } }?.let { return it } }
        btName?.let { raw ->
            val value = canonicalBtName(raw)
            if (value.isNotEmpty()) {
                find { p ->
                    p.match.btName.any { name ->
                        val expected = canonicalBtName(name)
                        value == expected ||
                            // Huawei frequently omits its own prefix in the OS-visible name.
                            (expected.startsWith("huawei ") && value == expected.removePrefix("huawei "))
                    }
                }?.let { return it }
            }
        }
        return GENERIC
    }

    private fun canonicalBtName(name: String): String = name
        .trim()
        .replace(Regex("""\s*\([A-Fa-f0-9]{4}\)\s*$"""), "")
        .trim()
        .replace(Regex("""\s+"""), " ")
        .lowercase(java.util.Locale.ROOT)

    private fun find(predicate: (Profile) -> Boolean): Profile? = profiles.firstOrNull(predicate)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        val GENERIC = Profile(
            id = "generic",
            name = "Unknown model",
            capabilities = mapOf("battery" to JsonObject(emptyMap()), "anc" to JsonObject(emptyMap())),
        )

        fun fromJson(documents: List<String>): ProfileRegistry =
            ProfileRegistry(documents.map { json.decodeFromString(Profile.serializer(), it) })
    }
}
