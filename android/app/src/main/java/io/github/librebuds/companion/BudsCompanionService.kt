// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.companion

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.service.BudsService

/**
 * Bound by the system while associated earbuds are present. An optional extra: the connection service
 * also runs without any association, but presence lets the system start it from the background where
 * a plain broadcast may not. API 36+ delivers [onDevicePresenceEvent]; API 33-35 the appeared/disappeared callbacks.
 * Starting and stopping are idempotent, so a callback delivered on both paths does no harm.
 */
class BudsCompanionService : CompanionDeviceService() {
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        val presence = when (event.event) {
            DevicePresenceEvent.EVENT_BT_CONNECTED -> Presence.APPEARED
            DevicePresenceEvent.EVENT_BT_DISCONNECTED -> Presence.DISAPPEARED
            else -> null
        }
        handle(presence, event.associationId)
    }

    @Deprecated("Replaced by onDevicePresenceEvent on API 36+")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) = handle(Presence.APPEARED, associationInfo.id)

    @Deprecated("Replaced by onDevicePresenceEvent on API 36+")
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) = handle(Presence.DISAPPEARED, associationInfo.id)

    private fun handle(presence: Presence?, associationId: Int) {
        val stored = AssociationStore(this).primary()
        val action = presenceAction(presence, associationId, stored)
        LibreBudsApp.from(this).eventLog.record(
            TAG,
            "presence ${presence ?: "other"} for association $associationId (stored ${stored?.associationId ?: "none"}): $action",
        )
        // Presence is also what tells a later link drop apart: TAKEN_OVER while the earbuds are here.
        if (stored != null && action != PresenceAction.IGNORE) trackPresence(presence, stored.address)
        when (action) {
            PresenceAction.START -> BudsService.start(this)
            PresenceAction.GONE -> BudsService.onEarbudsGone(this)
            PresenceAction.IGNORE -> Unit
        }
    }

    private companion object {
        const val TAG = "BudsCompanionService"
    }
}
