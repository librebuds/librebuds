package io.github.librebuds.protocol.profile

import io.github.librebuds.protocol.repoRoot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ProfileRegistryTest {
    private val registry: ProfileRegistry by lazy {
        val files = File(repoRoot(), "profiles").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        ProfileRegistry.fromJson(files.map { it.readText() })
    }

    @Test
    fun loadsAllRepositoryProfiles() {
        assertTrue(registry.profiles.size >= 30, "expected the generated catalog, got ${registry.profiles.size}")
    }

    @Test
    fun repositoryProfilesHaveUniqueIdsAndIdentifiers() {
        fun duplicates(values: List<String>) = values.groupBy { it.uppercase() }.filterValues { it.size > 1 }.keys
        assertEquals(emptySet<String>(), duplicates(registry.profiles.map { it.id }))
        assertEquals(emptySet<String>(), duplicates(registry.profiles.flatMap { it.match.sku }))
        assertEquals(emptySet<String>(), duplicates(registry.profiles.flatMap { it.match.modelId }))
    }

    @Test
    fun matchesBySkuFirst() {
        val profile = registry.match(sku = "BTFT0020", modelId = "000149", btName = "whatever")
        assertEquals("freebuds-6", profile.id)
        assertEquals("2026-09-28", profile.verifiedOn("anc"))
    }

    @Test
    fun matchesModelIdCaseInsensitively() {
        assertEquals("freebuds-pro-3", registry.match(modelId = "000149").id)
        assertEquals("freebuds-6", registry.match(modelId = "000161").id)
        assertEquals("freebuds-5", registry.match(modelId = "000141").id)
        assertEquals("freebuds-5i", registry.match(modelId = "00013d").id)
    }

    @Test
    fun matchesExactBluetoothNameLast() {
        assertEquals("freebuds-pro-5", registry.match(btName = "HUAWEI FreeBuds Pro 5").id)
    }

    @Test
    fun unknownDeviceGetsGenericProfile() {
        val profile = registry.match(sku = "NOPE", modelId = "FFFFFF", btName = "Renamed buds")
        assertEquals("generic", profile.id)
        assertTrue(profile.supports("battery"))
        assertTrue(profile.supports("anc"))
        assertNull(profile.verifiedOn("anc"))
        assertFalse(profile.supports("multipoint"))
    }

    @Test
    fun unverifiedCapabilityReportsNull() {
        assertNull(registry.match(modelId = "000157").verifiedOn("anc"))
    }

    @Test
    fun ignoresUnknownJsonKeys() {
        val r = ProfileRegistry.fromJson(listOf("""{"id":"x","name":"X","future":1,"capabilities":{"anc":{"verified":null}}}"""))
        assertEquals("x", r.profiles.single().id)
    }
}
