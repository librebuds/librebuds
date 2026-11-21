// LibreBuds - SPDX-License-Identifier: GPL-3.0-or-later
// PairFree model names: Omega-Destroyer/PairFree (GPL-3.0); see NOTICE.
package io.github.librebuds.ui.other

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Intent
import java.util.concurrent.ConcurrentHashMap
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
        "jabra" to "Jabra", "skullcandy" to "Skullcandy", "tozo" to "TOZO"
    )

    private val tozoModelNames = listOf(
        "A1", "A3", "AeroSound", "AeroSound2", "AeroSound3", "AeroSound4",
        "AeroSound6", "AeroSound6Pro", "AgileDots", "AgileDots2", "AgileNova",
        "AgileSolo", "AnybudsFits", "AnybudsPro", "ColorPods", "CrystalPods",
        "CrystalPods 2", "E1", "E2", "E2Pro", "EvoScreen", "G1", "G1S",
        "GoldenX2Pro", "H10", "H100BD", "H20", "HA1", "HiFi", "HT2",
        "HT3", "HT3V2", "NC20", "NC20Pro", "NC3", "NC9", "NC9Pro",
        "O2", "Openbuds", "OpenEarRing", "OpenEgo", "OpenReal",
        "OpenRhyme", "OpenWear", "OpenX2Pro", "PA1", "PA2", "PE1",
        "PM1", "PM1V2", "QuietFit", "SoundGo", "T10", "T10S",
        "T12", "T20", "T6", "T6S", "T9", "T9S"
    )

    fun load(context: Context): List<KnownModel> =
        context.assets.open("pairfree-models.tsv").bufferedReader().useLines { lines ->
            lines.filterNot { it.isBlank() || it.startsWith("#") }
                .mapNotNull { line ->
                    val columns = line.split('\t', limit = 2)
                    if (columns.size != 2) null
                    else KnownModel(brandNames[columns[0]] ?: columns[0], columns[1])
                }.toList()
        } + tozoModelNames.map { KnownModel("TOZO", it) }

    /** Strict model match; don't mistake a car stereo or phone for a set of earbuds. */
    fun match(name: String, catalog: List<KnownModel>): KnownModel? {
        val nameKey = normalize(name)
        if (nameKey.isBlank()) return null
        val tozoKey = nameKey.removePrefix("tozo ").takeIf { nameKey.startsWith("tozo ") }
        return catalog
            .asSequence()
            .filter { (it.brand != "TOZO" && normalize(it.model) == nameKey) ||
                (it.brand == "TOZO" && normalize(it.model) == tozoKey) }
            .maxByOrNull { it.model.length }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Huawei's existing control stack must always take priority over generic discovery. */
    fun isHuaweiManaged(name: String): Boolean =
        name.contains("freebuds", ignoreCase = true) ||
        name.startsWith("HUAWEI ", ignoreCase = true)

    /**
     * A recognizable family name is useful for display even when the exact model
     * cannot be determined. This is NOT a claim of vendor protocol support.
     */
    fun displayBrand(name: String): String? {
        val normalized = normalize(name)
        return when {
            normalized.startsWith("tozo ") -> "TOZO"
            normalized.contains("airpods") -> "Apple / Beats"
            normalized.startsWith("beats ") -> "Apple / Beats"
            normalized.contains("galaxy buds") -> "Samsung"
            normalized.contains("pixel buds") -> "Google"
            normalized.contains("nothing ear") || normalized.contains("cmf buds") -> "Nothing / CMF"
            else -> null
        }
    }

    /** Explicit earbud/headphone names that do not appear in the imported model table. */
    fun looksLikeHeadphones(name: String): Boolean {
        val n = normalize(name)
        return listOf("earbuds", "headphones", "headset", "freebuds", "airpods", "tozo ", "galaxy buds",
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
            if (OtherEarbudsCatalog.isHuaweiManaged(name)) {
                return@mapNotNull null // Existing LibreBuds protocol always takes priority.
            }
            val match = OtherEarbudsCatalog.match(name, catalog)
            val audioClass = device.bluetoothClass?.deviceClass in setOf(
                BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
                BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
                BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
            )
            if (match == null && !audioClass && !OtherEarbudsCatalog.looksLikeHeadphones(name)) return@mapNotNull null
            val level = OtherBatteryReadings.level(device.address).takeIf { isAudioConnected(device.address) }
            OtherEarbud(device.address, name, match?.brand ?: OtherEarbudsCatalog.displayBrand(name), match?.model,
                isAudioConnected(device.address), level)
        }.sortedWith(compareByDescending<OtherEarbud> { it.connected }.thenBy { it.name.lowercase(Locale.ROOT) })
    } catch (_: SecurityException) {
        emptyList()
    }
}

/**
 * Android does not expose a public battery-level getter for arbitrary Bluetooth
 * accessories. Some ROMs broadcast an overall headset level; cache those only
 * when the system supplies them. Never fabricate left/right/case percentages.
 */
object OtherBatteryReadings {
    const val ACTION_BATTERY_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    private val levels = ConcurrentHashMap<String, Int>()

    fun level(address: String): Int? = levels[address.uppercase(Locale.ROOT)]

    fun update(intent: Intent, address: String?) {
        if (intent.action != ACTION_BATTERY_CHANGED || address == null) return
        val percentage = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
        if (percentage in 0..100) levels[address.uppercase(Locale.ROOT)] = percentage
    }
}
