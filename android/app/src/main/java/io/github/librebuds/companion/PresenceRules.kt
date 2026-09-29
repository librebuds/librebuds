// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.companion

/** A presence change for some earbuds, from the companion device service or an ACL broadcast. */
enum class Presence { APPEARED, DISAPPEARED }

/** What to do with the connection service in response to a presence change. */
enum class PresenceAction { START, STOP, IGNORE }

/** Companion presence events only matter for the stored primary association. */
fun presenceAction(presence: Presence?, associationId: Int, stored: Stored?): PresenceAction =
    if (stored == null || stored.associationId != associationId) PresenceAction.IGNORE else presence.toAction()

/** ACL broadcasts carry a Bluetooth address; only the stored primary earbuds start or stop the service. */
fun aclAction(presence: Presence?, address: String?, stored: Stored?): PresenceAction =
    if (stored == null || address == null || !stored.address.equals(address, ignoreCase = true)) {
        PresenceAction.IGNORE
    } else {
        presence.toAction()
    }

private fun Presence?.toAction(): PresenceAction = when (this) {
    Presence.APPEARED -> PresenceAction.START
    Presence.DISAPPEARED -> PresenceAction.STOP
    null -> PresenceAction.IGNORE
}
