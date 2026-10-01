package io.github.librebuds.protocol.beacon

import io.github.librebuds.protocol.util.u8

/** Battery byte from the beacon: bit 7 = charging, bits 0-6 = percent. */
data class BeaconBattery(val percent: Int, val charging: Boolean) {
    companion object {
        fun decode(value: Int) = BeaconBattery(percent = value and 0x7F, charging = value and 0x80 != 0)
    }
}

class Beacon(
    val businessType: Int,
    /** Raw flags byte from the type-1 (business) record, byte 1: bit 0 popup allowed, bit 1 reconnect allowed. */
    val businessFlags: Int,
    val closeRangeEnabled: Boolean,
    val reconnectEnabled: Boolean,
    val modelId: String?,
    val subModelId: Int?,
    val newModelId: String?,
    val total: BeaconBattery?,
    val left: BeaconBattery?,
    val right: BeaconBattery?,
    val case: BeaconBattery?,
    val referenceRssi: Int?,
    val advPower: Int?,
    val connectedDevices: Int?,
    val pairedDevices: Int?,
    val maxConnect: Int?,
    val maxPair: Int?,
    val deviceIds: List<String>,
    val dualModeKey: String?,
    val btFeature: Int?,
    val sequence: Int?,
    val custom: ByteArray?,
)

/**
 * Proximity beacon carried as BLE service data under UUID 0xFDEE.
 * Records are [type][value] with a fixed value length per type (no length byte).
 */
object FdeeBeacon {
    const val SERVICE_UUID = "0000fdee-0000-1000-8000-00805f9b34fb"

    private const val BUSINESS = 1
    private const val DEVICE_ID = 5
    private const val CUSTOM = 0xFF

    // SPEC-GAP: business type 1 is assumed to mean "proximity"/close range; not confirmed
    // against a captured beacon.
    private const val BUSINESS_CLOSE_RANGE = 1

    private val VALUE_LENGTHS = mapOf(
        1 to 2, 2 to 1, 3 to 3, 4 to 1, 5 to 2, 6 to 1, 7 to 1, 8 to 1, 9 to 1,
        10 to 2, 11 to 1, 12 to 1, 13 to 1, 14 to 1, 15 to 1, 16 to 1, 17 to 1, 18 to 4,
    )

    fun parse(serviceData: ByteArray): Beacon? {
        val fields = mutableMapOf<Int, ByteArray>()
        var custom: ByteArray? = null
        var i = 0
        while (i < serviceData.size) {
            val type = serviceData[i].u8()
            if (type == CUSTOM) {
                custom = serviceData.copyOfRange(i + 1, serviceData.size)
                break
            }
            val length = VALUE_LENGTHS[type] ?: break
            val end = i + 1 + length
            if (end > serviceData.size) return null
            val value = serviceData.copyOfRange(i + 1, end)
            val previous = fields[type]
            fields[type] = when {
                previous == null -> value
                type == DEVICE_ID -> previous + value
                else -> return null
            }
            i = end
        }
        val business = fields[BUSINESS] ?: return null
        val businessType = business[0].u8()
        val flags = business[1].u8()
        val isCloseRange = businessType == BUSINESS_CLOSE_RANGE
        return Beacon(
            businessType = businessType,
            businessFlags = flags,
            closeRangeEnabled = isCloseRange && flags and 0x01 != 0,
            reconnectEnabled = isCloseRange && flags and 0x02 != 0,
            modelId = fields[3]?.hex(),
            subModelId = fields[4]?.single(),
            // SPEC-GAP: 6-char id = fixed "00" prefix + 4 ASCII chars from the type-18 record; unconfirmed.
            newModelId = fields[18]?.let { "00" + it.toString(Charsets.US_ASCII) },
            total = fields[11]?.battery(),
            left = fields[12]?.battery(),
            right = fields[13]?.battery(),
            case = fields[14]?.battery(),
            referenceRssi = fields[2]?.get(0)?.toInt(),
            advPower = fields[17]?.get(0)?.toInt(),
            connectedDevices = fields[6]?.single(),
            pairedDevices = fields[7]?.single(),
            maxConnect = fields[8]?.single(),
            maxPair = fields[9]?.single(),
            deviceIds = fields[DEVICE_ID]?.toList()?.chunked(2)?.map { it.toByteArray().hex() } ?: emptyList(),
            dualModeKey = fields[10]?.hex(),
            btFeature = fields[15]?.single(),
            sequence = fields[16]?.single(),
            custom = custom,
        )
    }

    private fun ByteArray.single(): Int = this[0].u8()

    private fun ByteArray.battery(): BeaconBattery = BeaconBattery.decode(this[0].u8())

    private fun ByteArray.hex(): String = joinToString("") { "%02X".format(it.u8()) }
}
