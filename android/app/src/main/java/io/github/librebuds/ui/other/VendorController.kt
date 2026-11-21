// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.other

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/** One brand-agnostic set of controls exposed through the existing LibreBuds UI. */
data class VendorControlsState(
    val status: String = "Not connected",
    val ready: Boolean = false,
    val battery: Map<Int, Int> = emptyMap(),
    val noise: Int? = null,
    val eq: Int? = null,
    val error: String? = null,
)

/**
 * Add a PairFree-derived protocol implementation here to make it available
 * without touching the Home/Controls/Details composables. Unsupported operations
 * must not advertise capabilities; callers show controls only after device replies.
 */
interface VendorController {
    val state: StateFlow<VendorControlsState>
    suspend fun run()
    suspend fun changeNoise(mode: Int)
    suspend fun changeEq(preset: Int)
    fun close()
}

object VendorProtocols {
    fun supportsNothing(name: String, brand: String?): Boolean =
        brand == "Nothing / CMF" ||
            name.startsWith("Nothing ", ignoreCase = true) ||
            name.startsWith("CMF ", ignoreCase = true)

    fun open(context: Context, name: String, address: String, brand: String?): VendorController? =
        when {
            supportsNothing(name, brand) -> NothingController(context.applicationContext, address, name)
            else -> null // No vendor commands for this brand until a protocol driver exists.
        }
}
