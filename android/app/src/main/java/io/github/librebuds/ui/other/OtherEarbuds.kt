// LibreBuds - SPDX-License-Identifier: GPL-3.0-or-later
// PairFree model names: Omega-Destroyer/PairFree (GPL-3.0); see NOTICE.
package io.github.librebuds.ui.other

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import io.github.librebuds.bt.isAudioConnected
import io.github.librebuds.ui.PairRow
import java.util.Locale

/** A catalog match is for display/discovery ONLY, not proof of a working vendor protocol. */
data class KnownModel(val brand: String, val model: String)

object OtherEarbudsCatalog {
    private val brandNames = mapOf(
        "soundpeats" to "SOUNDPEATS", "soundcore" to "Soundcore",
        "samsung" to "Samsung", "sony" to "Sony", "bose" to "Bose",
        "jbl" to "JBL", "apple" to "Apple / Beats", "nothing" to "Nothing / CMF",
        "pixel" to "Google", "qcy" to "QCY", "1more" to "1MORE",
        "earfun" to "EarFun", "cambridge" to "Cambridge Audio",
        "oneplus" to "OnePlus / OPPO", "sennheiser" to "Sennheiser",
        "jabra" to "Jabra", "skullcandy" to "Skullcandy"
    )

    fun load(context: Context): List<KnownModel> =
        context.assets.open("pairfree-models.tsv").bufferedReader().useLines { lines ->
            lines.filterNot { it.isBlank() || it.startsWith("#") }
                .mapNotNull { line ->
                    val columns = line.split('\t', limit = 2)
                    if (columns.size != 2) null
                    else KnownModel(brandNames[columns[0]] ?: columns[0], columns[1])
                }.toList()
        }

    /** Strict model match; don't mistake a car stereo or phone for a set of earbuds. */
    fun match(name: String, catalog: List<KnownModel>): KnownModel? {
        val nameKey = normalize(name)
        if (nameKey.isBlank()) return null
        return catalog
            .asSequence()
            .filter { normalize(it.model) == nameKey }
            .maxByOrNull { it.model.length }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Explicit earbud/headphone names that do not appear in the imported model table. */
    fun looksLikeHeadphones(name: String): Boolean {
        val n = normalize(name)
        return listOf("earbuds", "headphones", "headset", "freebuds", "airpods", "galaxy buds",
            "pixel buds", "nothing ear", "cmf buds", "jabra elite", "soundcore", "soundpeats",
            "melobuds", "melomania", "sennheiser momentum", "sony wh ", "sony wf ",
            "bose quietcomfort", "oneplus buds", "oppo enco").any { n.contains(it) }
    }
}

data class OtherEarbud(val address: String, val name: String, val brand: String?, val model: String?,
                       val connected: Boolean, val battery: Int?) {
    fun toRow(): PairRow = PairRow(
        address = address,
        name = name,
        model = model ?: brand ?: "Bluetooth audio",
        connected = connected,
        battery = battery?.let { "$it%" },
        art = "generic",
        genericAudio = true,
        brand = brand
    )
}

/**
 * Reads paired devices only. Does not open unknown vendor control channels or claim
 * ANC/EQ/gesture support where no working command implementation exists.
 */
@Suppress("MissingPermission")
fun readOtherEarbuds(context: Context, catalog: List<KnownModel>): List<OtherEarbud> {
    if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    return try {
        adapter.bondedDevices.orEmpty().mapNotNull { device ->
            val name = device.name ?: return@mapNotNull null
            if (name.contains("freebuds", ignoreCase = true) || name.startsWith("HUAWEI ", ignoreCase = true)) {
                return@mapNotNull null // Existing LibreBuds protocol always takes priority.
            }
            val match = OtherEarbudsCatalog.match(name, catalog)
            val audioClass = device.bluetoothClass?.deviceClass in setOf(
                BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
                BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
                BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
            )
            if (match == null && !audioClass && !OtherEarbudsCatalog.looksLikeHeadphones(name)) return@mapNotNull null
            val level = device.batteryLevel.takeIf { it in 0..100 && isAudioConnected(device.address) }
            OtherEarbud(device.address, name, match?.brand, match?.model,
                isAudioConnected(device.address), level)
        }.sortedWith(compareByDescending<OtherEarbud> { it.connected }.thenBy { it.name.lowercase(Locale.ROOT) })
    } catch (_: SecurityException) {
        emptyList()
    }
}
