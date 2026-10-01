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
    /** Which of the beacon layouts this was read from. */
    val format: BeaconFormat = BeaconFormat.CLOSE_RANGE,
    /** Compact format only: the raw field-presence bitmap (bytes 2-3, big-endian). */
    val flags: Int? = null,
    /** Compact format only: what the flags say about the case lid and the buds. */
    val lid: LidState? = null,
    /** Compact format only: raw state byte (0x42/0x4A/0x4C buds in case, 0x8A out, 0x8B worn). */
    val state: Int? = null,
    /** Compact format only: the raw tail bytes (a bud-in-case bitmap, not decoded yet). */
    val tail: List<Int> = emptyList(),
) {
    /** True only for a compact beacon sent by the case with its lid open (flags 03FF). */
    val caseOpen: Boolean get() = lid == LidState.OPEN
}

enum class BeaconFormat {
    /** TLV records, sent in pairing mode and on some events (business id 0101/0100). */
    CLOSE_RANGE,
    /** The everyday frame, sent all the time, also with the lid closed (business id 02). */
    COMPACT,
}

/** Case and bud state of a compact beacon, derived from its field-presence flags. */
enum class LidState {
    /** Idle, lid closed: no bud battery and no tail (flags 003F). */
    CLOSED,
    /** Lid open, the case is advertising: case battery and tail present (flags 03FF). */
    OPEN,
    /** Both buds out of the case (or worn), the buds are advertising: tail but no case battery (flags 03DF). */
    BUDS_OUT,
    /** Just after the lid closed: bud batteries but no tail (flags 00FF, 007F, 00BF). */
    TRANSITIONAL,
}

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

    /**
     * Decodes 0xFDEE service data: `01 01`/`01 00` is the close-range TLV layout, `01 02` the compact
     * one. Anything else (including the extended identification frames of newer models, which carry
     * the classic Bluetooth address) is not decoded and gives null, so it is never stored or shown.
     */
    fun parse(serviceData: ByteArray): Beacon? {
        if (serviceData.size >= 2 && serviceData[0].u8() == 0x01 && serviceData[1].u8() == COMPACT_BUSINESS) {
            return parseCompact(serviceData)
        }
        return parseCloseRange(serviceData)
    }

    private const val COMPACT_BUSINESS = 0x02
    private const val COMPACT_HEAD_BITS = 0x1F
    private const val COMPACT_HEAD_LENGTH = 13
    private const val COMPACT_CASE = 0x20
    private const val COMPACT_LEFT = 0x40
    private const val COMPACT_RIGHT = 0x80
    private const val COMPACT_TAIL_1 = 0x100
    private const val COMPACT_TAIL_2 = 0x200
    private const val COMPACT_TAIL = COMPACT_TAIL_1 or COMPACT_TAIL_2

    /**
     * The compact frame:
     * `01 02 <flags:2> <st> 00 <modelId:3> <subModel> 03 <state> AB [case] [left] [right] [tail:2]`.
     * The flags are a field-presence bitmap: bits 0-4 cover the fixed head (always set), bit 5 the
     * case battery, bit 6 left, bit 7 right, bits 8 and 9 one tail byte each (not decoded here). Every
     * captured frame of hardware round 2 matches this length rule; a frame that does not is rejected.
     */
    private fun parseCompact(data: ByteArray): Beacon? {
        if (data.size < COMPACT_HEAD_LENGTH) return null
        val flags = (data[2].u8() shl 8) or data[3].u8()
        if (flags and COMPACT_HEAD_BITS != COMPACT_HEAD_BITS) return null
        if (data[5].u8() != 0x00 || data[10].u8() != 0x03 || data[12].u8() != 0xAB) return null
        val optionalBits = listOf(COMPACT_CASE, COMPACT_LEFT, COMPACT_RIGHT, COMPACT_TAIL_1, COMPACT_TAIL_2)
        if (data.size < COMPACT_HEAD_LENGTH + optionalBits.count { flags and it != 0 }) return null
        var i = COMPACT_HEAD_LENGTH
        fun optional(bit: Int): Int? = if (flags and bit == 0) null else data[i++].u8()
        val case = optional(COMPACT_CASE)
        val left = optional(COMPACT_LEFT)
        val right = optional(COMPACT_RIGHT)
        val tail = listOfNotNull(optional(COMPACT_TAIL_1), optional(COMPACT_TAIL_2))
        val lid = when {
            flags and COMPACT_TAIL != 0 && flags and COMPACT_CASE != 0 -> LidState.OPEN
            flags and COMPACT_TAIL != 0 -> LidState.BUDS_OUT
            flags and (COMPACT_LEFT or COMPACT_RIGHT) != 0 -> LidState.TRANSITIONAL
            else -> LidState.CLOSED
        }
        return Beacon(
            businessType = COMPACT_BUSINESS,
            businessFlags = 0,
            closeRangeEnabled = false,
            reconnectEnabled = false,
            modelId = data.copyOfRange(6, 9).hex(),
            subModelId = data[9].u8(),
            newModelId = null,
            total = null,
            left = left?.let(::compactBattery),
            right = right?.let(::compactBattery),
            case = case?.let(::compactBattery),
            referenceRssi = null,
            advPower = null,
            connectedDevices = null,
            pairedDevices = null,
            maxConnect = null,
            maxPair = null,
            deviceIds = emptyList(),
            dualModeKey = null,
            btFeature = null,
            sequence = null,
            custom = null,
            format = BeaconFormat.COMPACT,
            flags = flags,
            lid = lid,
            state = data[11].u8(),
            tail = tail,
        )
    }

    /** 0x00 means "not reported" (an empty case does not advertise); a level above 100 is not a level. */
    private fun compactBattery(value: Int): BeaconBattery? =
        BeaconBattery.decode(value).takeIf { value != 0 && it.percent <= 100 }

    private fun parseCloseRange(serviceData: ByteArray): Beacon? {
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
