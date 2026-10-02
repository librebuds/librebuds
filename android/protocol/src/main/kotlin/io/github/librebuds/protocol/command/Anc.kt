package io.github.librebuds.protocol.command

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.u8

enum class AncMode(val code: Int) {
    OFF(0),
    CANCELLATION(1),
    AWARENESS(2);

    companion object {
        fun of(code: Int): AncMode? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Noise-control state as reported by the device. [level] meaning depends on the mode and model (in
 * awareness it is the sub-mode, see [Anc.AWARENESS_VOICE]); [awarenessLevel] is the adaptive
 * awareness slider (0..10) on models that report it.
 */
data class AncState(val modeCode: Int, val level: Int, val awarenessLevel: Int? = null) {
    val mode: AncMode? get() = AncMode.of(modeCode)
}

/**
 * Noise control. Read with 2B/2A (reply TLV 1 = [level, mode]); write with 2B/04
 * (TLV 1 = [mode, level], note the reversed order). The device acknowledges invalid
 * writes too, so a write is only trusted after a re-read that [confirms] it.
 */
object Anc {
    val GET = CommandId(0x2B, 0x2A)
    val SET = CommandId(0x2B, 0x04)

    fun readRequest(): Packet = Packet.read(GET, 1)

    fun writeRequest(mode: AncMode, level: Int): Packet {
        require(level in 0..255) { "ANC level out of range: $level" }
        return Packet(SET, listOf(Tlv.of(1, mode.code, level)))
    }

    fun parseState(packet: Packet): AncState? {
        if (packet.id != GET) return null
        val value = packet.find(1)?.takeIf { it.size == 2 } ?: return null
        val slider = packet.find(2)?.takeIf { it.size == 1 }?.get(0)?.u8()
        return AncState(modeCode = value[1].u8(), level = value[0].u8(), awarenessLevel = slider)
    }

    /**
     * The adaptive awareness level write: awareness sub-mode [AWARENESS_ADAPTIVE] with the slider
     * value on TLV 4; TLV 3 = 1 is part of the vendor app's frame and sent as it is.
     */
    fun awarenessLevelRequest(level: Int): Packet {
        require(level in 0..AWARENESS_LEVEL_MAX) { "Awareness level out of range: $level" }
        return Packet(SET, listOf(Tlv.of(1, AncMode.AWARENESS.code, AWARENESS_ADAPTIVE), Tlv.of(3, 1), Tlv.of(4, level)))
    }

    /** true when the device acknowledged the write, false on a non-zero status, null if not an ANC ack. */
    fun isWriteAccepted(packet: Packet): Boolean? {
        if (packet.id != SET) return null
        val status = packet.find(2)?.takeIf { it.size == 1 } ?: return null
        return status[0].u8() == 0
    }

    // SPEC-GAP: in awareness mode the firmware picks the level itself, so only the mode is compared.
    fun confirms(state: AncState, mode: AncMode): Boolean = state.mode == mode

    /** An awareness sub-mode write is confirmed by awareness read back with that sub-mode. */
    fun confirmsAwareness(state: AncState, subMode: Int): Boolean = state.mode == AncMode.AWARENESS && state.level == subMode

    /** Awareness sub-modes, sent as the level byte with [AncMode.AWARENESS]. */
    const val AWARENESS_VOICE = 1
    const val AWARENESS_STANDARD = 2
    const val AWARENESS_ADAPTIVE = 4
    const val AWARENESS_LEVEL_MAX = 10

    /** An explicit cancellation level write is confirmed only by both the mode and the level read back. */
    fun confirmsLevel(state: AncState, level: Int): Boolean = state.mode == AncMode.CANCELLATION && state.level == level
}
