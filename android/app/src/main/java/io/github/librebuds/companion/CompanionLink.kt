// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.companion

import android.app.Activity
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.DeviceNotAssociatedException
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.util.Log
import io.github.librebuds.LibreBudsApp

/**
 * Associates earbuds with the app through the system companion device dialog and asks the system
 * to report their presence, so the app may start its service from the background later.
 */
class CompanionLink(context: Context) {
    private val store = AssociationStore(context)
    private val events = LibreBudsApp.from(context).eventLog
    private val manager: CompanionDeviceManager? = context.getSystemService(CompanionDeviceManager::class.java)

    /**
     * Shows the system confirmation for [address]; [onDone] receives the stored association, or null
     * on failure. [name] is the paired device's name, stored when the system reports no display name.
     */
    fun associate(activity: Activity, address: String, name: String?, onDone: (Stored?) -> Unit) {
        val cdm = manager ?: return onDone(null)
        cdm.myAssociations.firstOrNull { it.deviceMacAddress?.toString().equals(address, ignoreCase = true) }?.let {
            onDone(persist(it, address, name))
            return
        }
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
            .setSingleDevice(true)
            .build()
        cdm.associate(request, activity.mainExecutor, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                try {
                    activity.startIntentSenderForResult(intentSender, REQUEST_CODE, null, 0, 0, 0)
                } catch (e: IntentSender.SendIntentException) {
                    Log.w(TAG, "Cannot show the association dialog", e)
                    events.record(TAG, "association dialog not shown: ${e.javaClass.simpleName}")
                    onDone(null)
                }
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                onDone(persist(associationInfo, address, name))
            }

            override fun onFailure(error: CharSequence?) {
                Log.w(TAG, "Association failed: $error")
                events.record(TAG, "association failed: $error")
                onDone(null)
            }
        })
    }

    /** Asks the system to report when the associated earbuds appear or disappear. */
    fun observe(stored: Stored) {
        val cdm = manager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                cdm.startObservingDevicePresence(
                    ObservingDevicePresenceRequest.Builder().setAssociationId(stored.associationId).build()
                )
            } else {
                @Suppress("DEPRECATION")
                cdm.startObservingDevicePresence(stored.address)
            }
        } catch (e: DeviceNotAssociatedException) {
            Log.w(TAG, "Cannot observe device presence", e)
            events.record(TAG, "presence not observed: ${e.javaClass.simpleName}")
        } catch (e: SecurityException) {
            // Presence observation is an optimisation; the association itself stays valid without it.
            Log.w(TAG, "Cannot observe device presence", e)
            events.record(TAG, "presence not observed: ${e.javaClass.simpleName}")
        }
    }

    private fun persist(info: AssociationInfo, address: String, name: String?): Stored {
        val stored = Stored(address, info.displayName?.toString() ?: name, info.id)
        store.save(stored)
        observe(stored)
        return stored
    }

    private companion object {
        const val TAG = "CompanionLink"
        const val REQUEST_CODE = 0x4c42
    }
}
