// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.companion

import io.github.librebuds.protocol.profile.ProfileRegistry

/** A paired Bluetooth device as the system lists it; [uuids] are the service UUIDs it advertised. */
data class BondedDevice(val name: String?, val address: String, val uuids: List<String> = emptyList())

/**
 * Paired earbuds the app recognised. [name] falls back to the address for display; [bondedName] is the
 * Bluetooth name, if any; [model] is the matched profile's product name, or null for a generic match.
 */
data class DetectedBuds(val name: String, val address: String, val profileId: String, val model: String?, val bondedName: String?)

/** Serial Port Profile: the control channel every FreeBuds model advertises. */
const val SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb"

private val HUAWEI_AUDIO_FAMILIES = listOf(
    "freebuds", "freeclip", "freearc", "freelace", "w buds", "eyewear"
)

/**
 * A clearly named, bonded Huawei audio product. Android's cached UUID list is sometimes
 * empty even though the headset supports the Huawei control channel. Names alone never
 * imply that the RFCOMM handshake will succeed; the controller still verifies the link.
 */
private fun isHuaweiAudioProductName(name: String?): Boolean {
    val normalized = name?.trim()?.lowercase(java.util.Locale.ROOT)
        ?.replace(Regex("""\s+"""), " ") ?: return false
    if (!normalized.startsWith("huawei ")) return false
    val family = normalized.removePrefix("huawei ")
    return HUAWEI_AUDIO_FAMILIES.any { family == it || family.startsWith("$it ") }
}

/**
 * Keeps only FreeBuds out of the paired devices: a Bluetooth name of a known model; or, for renamed
 * earbuds, the SPP UUID together with a name that still says huawei or freebuds; or an address the app
 * stored before ([known]). Anything else (car kits, speakers, keyboards) is never listed. Sorted by name.
 */
fun detectFreeBuds(bonded: List<BondedDevice>, registry: ProfileRegistry, known: Set<String>): List<DetectedBuds> {
    val knownUpper = known.mapTo(mutableSetOf()) { it.uppercase() }
    return bonded.mapNotNull { device ->
        val profile = registry.match(btName = device.name)
        // The upstream profile catalog also includes a Huawei smart power bank.
        // This is an earbuds UI: do not try to open an audio/control session to it.
        if (profile.id.startsWith("smart-powerbank")) return@mapNotNull null
        val recognised = profile.id != ProfileRegistry.GENERIC.id
        val spp = device.uuids.any { it.equals(SPP_UUID, ignoreCase = true) }
        // Keep historical renamed FreeBuds recognition, but avoid treating a paired Huawei
        // watch or phone as earbuds solely because it offers an SPP service.
        val vendorName = device.name?.lowercase(java.util.Locale.ROOT)?.let { name ->
            "freebuds" in name || ("huawei" in name && "buds" in name)
        } == true || isHuaweiAudioProductName(device.name)
        val huaweiAudioName = isHuaweiAudioProductName(device.name)
        val stored = device.address.uppercase() in knownUpper
        if (!recognised && !huaweiAudioName && !(spp && vendorName) && !stored) return@mapNotNull null
        DetectedBuds(
            name = device.name ?: device.address,
            address = device.address,
            profileId = profile.id,
            model = profile.name.takeIf { recognised },
            bondedName = device.name,
        )
    }.sortedBy { it.name.lowercase() }
}

/**
 * A pair switcher label per address: [buds] whose name is shared with another pair (two physical units
 * of the same model keep the stock Bluetooth name) get the last two bytes of their address appended
 * after a separating dot, so the rows stay tellable apart without reading as cramped; a name no one
 * else shares keeps its plain form.
 */
fun disambiguatedLabels(buds: List<DetectedBuds>): Map<String, String> {
    val counts = buds.groupingBy { it.name.lowercase() }.eachCount()
    return buds.associate { bud ->
        val label = if (counts.getValue(bud.name.lowercase()) > 1) {
            "${bud.name} · ${bud.address.takeLast(5).uppercase()}"
        } else {
            bud.name
        }
        bud.address to label
    }
}
