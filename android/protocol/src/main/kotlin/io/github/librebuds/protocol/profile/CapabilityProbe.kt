// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.protocol.profile

import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.protocol.command.Battery
import io.github.librebuds.protocol.command.Equalizer
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.protocol.command.Gestures
import io.github.librebuds.protocol.command.LowLatency
import io.github.librebuds.protocol.command.Multipoint
import io.github.librebuds.protocol.command.SoundQuality
import io.github.librebuds.protocol.command.Status
import io.github.librebuds.protocol.command.VoiceLanguage
import io.github.librebuds.protocol.command.WearDetection

/** What a device answered when asked about one feature. */
enum class Support {
    /** Replied with data: the feature works. */
    SUPPORTED,

    /** Replied with status 100003: the command exists but this model does not offer it. */
    UNSUPPORTED,

    /** Said nothing: the service does not know the command at all. */
    SILENT,
}

/** One feature worth probing, together with the read request that settles it. */
data class Probe(val capability: String, val request: Packet) {
    val id: CommandId get() = request.id
}

/**
 * Derives a device's capabilities by asking it, instead of trusting a hand-maintained list.
 *
 * Earbuds answer an unsupported command with status 100003 and stay silent on commands their
 * service does not know, so three outcomes are distinguishable ([Support]). A model nobody has
 * tested can therefore be supported on first connection: probe, cache the answers, and let the
 * shipped [Profile] supply only the name and artwork.
 *
 * Replies also carry the legal value sets (gesture actions, equalizer presets), so screens can
 * be built from the device's own answer rather than a table that has to be kept in sync.
 *
 * Capability names match the keys used in the shipped profile documents.
 */
object CapabilityProbe {
    /** Read requests, in the order they should be sent. */
    val PROBES: List<Probe> = listOf(
        Probe("battery", Battery.request()),
        Probe("wear", WearDetection.read()),
        Probe("equalizer", Equalizer.read()),
        Probe("gestures", Gestures.read(Gesture.LONG_PRESS)),
        Probe("multipoint", Multipoint.readToggle()),
        Probe("lowLatency", LowLatency.read()),
        Probe("soundQuality", SoundQuality.read()),
        Probe("language", VoiceLanguage.read()),
    )

    /** Classifies one reply; pass null for [reply] when the request timed out. */
    fun classify(reply: Packet?): Support = when {
        reply == null -> Support.SILENT
        Status.isUnsupported(reply) -> Support.UNSUPPORTED
        else -> Support.SUPPORTED
    }

    /**
     * Folds probe replies into capability names.
     * [replies] maps each probed command to its reply, or to null when nothing came back.
     */
    fun capabilities(replies: Map<CommandId, Packet?>): Set<String> =
        PROBES.filter { classify(replies[it.id]) == Support.SUPPORTED }
            .map { it.capability }
            .toSet()
}
