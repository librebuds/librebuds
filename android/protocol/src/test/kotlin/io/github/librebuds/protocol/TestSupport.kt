package io.github.librebuds.protocol

import io.github.librebuds.protocol.frame.FrameReassembler
import io.github.librebuds.protocol.frame.RxEvent
import io.github.librebuds.protocol.util.hexToBytes
import java.io.File

/** Decodes one complete frame (hex) into a packet, failing loudly if it is not exactly one valid frame. */
fun packetOf(frameHex: String): Packet {
    val events = FrameReassembler().feed(frameHex.hexToBytes())
    val payload = events.single() as RxEvent.Payload
    return requireNotNull(Packet.fromPayload(payload.bytes)) { "Payload too short: $frameHex" }
}

/** Repository root, passed in by Gradle (see protocol/build.gradle.kts). */
fun repoRoot(): File = File(requireNotNull(System.getProperty("librebuds.repoRoot")) { "librebuds.repoRoot not set" })
