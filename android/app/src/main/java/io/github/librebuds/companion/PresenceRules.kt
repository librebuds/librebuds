// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.companion

import io.github.librebuds.bt.AclTracker
import io.github.librebuds.bt.AudioConnections

/** A presence change for some earbuds, from the companion device service or an ACL broadcast. */
enum class Presence { APPEARED, DISAPPEARED }

/**
 * What a presence change asks of the app. START: make sure the connection service runs (it then
 * connects whichever FreeBuds are connected). GONE: the earbuds left; the running service follows
 * this itself, a stopped one only needs a take-over cleared (see [io.github.librebuds.service.BudsService.onEarbudsGone]).
 */
enum class PresenceAction { START, GONE, IGNORE }

/** Companion presence events only matter for the stored association (an optional extra, see [CompanionLink]). */
fun presenceAction(presence: Presence?, associationId: Int, stored: Stored?): PresenceAction =
    if (stored == null || stored.associationId != associationId) PresenceAction.IGNORE else presence.toAction()

/**
 * ACL broadcasts carry a Bluetooth address; any paired FreeBuds ([isFreeBuds]) counts, whether or not
 * the user ever linked it through the companion dialog. Other devices (a car kit, a watch) are ignored.
 */
fun aclAction(presence: Presence?, address: String?, isFreeBuds: (String) -> Boolean): PresenceAction =
    if (address == null || !isFreeBuds(address)) PresenceAction.IGNORE else presence.toAction()

private fun Presence?.toAction(): PresenceAction = when (this) {
    Presence.APPEARED -> PresenceAction.START
    Presence.DISAPPEARED -> PresenceAction.GONE
    null -> PresenceAction.IGNORE
}

/**
 * Records a presence change for [address] in [AclTracker] (and clears [AudioConnections] when the
 * earbuds left), so a link drop after a process start is still recognised as a takeover.
 */
fun trackPresence(presence: Presence?, address: String) {
    when (presence) {
        Presence.APPEARED -> AclTracker.onConnected(address)
        Presence.DISAPPEARED -> {
            AclTracker.onDisconnected(address)
            AudioConnections.forget(address)
        }
        null -> Unit
    }
}
