// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.other

import android.content.Context

/**
 * A capability-gated registry for additional Bluetooth vendor drivers.
 * Huawei/FreeBuds devices never pass through this registry: they continue to
 * use the original LibreBuds implementation and unchanged device UI.
 *
 * Only register a vendor after its command transport has been implemented.
 * Recognition by a model-name catalog alone is NOT protocol support.
 */
object VendorProtocols {
    fun supportsNothing(name: String, brand: String?): Boolean =
        brand == "Nothing / CMF" ||
            name.startsWith("Nothing ", ignoreCase = true) ||
            name.startsWith("CMF ", ignoreCase = true)

    fun open(context: Context, name: String, address: String, brand: String?): NothingController? =
        if (supportsNothing(name, brand)) {
            NothingController(context.applicationContext, address, name)
        } else {
            null
        }
}
