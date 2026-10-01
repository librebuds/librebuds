// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.companion

import android.content.Context
import androidx.core.content.edit

/** The earbuds the user associated through the companion device manager. */
data class Stored(val address: String, val name: String?, val associationId: Int)

/**
 * Persists the primary association (the earbuds the service connects to) and every address that was
 * ever associated, so renamed earbuds the app knows stay listed.
 */
class AssociationStore(context: Context) {
    private val prefs = context.getSharedPreferences("association", Context.MODE_PRIVATE)

    fun primary(): Stored? {
        val address = prefs.getString(KEY_ADDRESS, null) ?: return null
        if (!prefs.contains(KEY_ASSOCIATION_ID)) return null
        return Stored(address, prefs.getString(KEY_NAME, null), prefs.getInt(KEY_ASSOCIATION_ID, 0))
    }

    /** Addresses of every pair of earbuds associated so far, the primary included. */
    fun known(): Set<String> = prefs.getStringSet(KEY_KNOWN, null).orEmpty() + listOfNotNull(primary()?.address)

    fun save(stored: Stored) = prefs.edit {
        putStringSet(KEY_KNOWN, prefs.getStringSet(KEY_KNOWN, null).orEmpty() + stored.address.uppercase())
        putString(KEY_ADDRESS, stored.address)
        putString(KEY_NAME, stored.name)
        putInt(KEY_ASSOCIATION_ID, stored.associationId)
    }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val KEY_ADDRESS = "address"
        const val KEY_NAME = "name"
        const val KEY_ASSOCIATION_ID = "association_id"
        const val KEY_KNOWN = "known_addresses"
    }
}
