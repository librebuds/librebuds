// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import androidx.annotation.StringRes
import io.github.librebuds.R

/** Typed screen-level errors, so screens localize the message instead of showing a raw exception string. */
enum class UiError { REJECTED, NOT_CONNECTED, NO_REPLY, UNKNOWN }

@StringRes
fun UiError.messageRes(): Int = when (this) {
    UiError.REJECTED -> R.string.error_rejected
    UiError.NOT_CONNECTED -> R.string.error_not_connected
    UiError.NO_REPLY -> R.string.error_no_reply
    UiError.UNKNOWN -> R.string.error_unknown
}
