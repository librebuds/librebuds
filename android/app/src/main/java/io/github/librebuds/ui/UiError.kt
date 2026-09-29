// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui

import androidx.annotation.StringRes
import io.github.librebuds.R
import io.github.librebuds.session.AncRejectedException
import io.github.librebuds.session.NotConnectedException
import io.github.librebuds.session.RequestTimeoutException
import io.github.librebuds.session.SessionClosedException
import io.github.librebuds.session.SettingUnavailableException

/** Typed screen-level errors, so screens localize the message instead of showing a raw exception string. */
enum class UiError { REJECTED, NOT_CONNECTED, NO_REPLY, UNKNOWN }

@StringRes
fun UiError.messageRes(): Int = when (this) {
    UiError.REJECTED -> R.string.error_rejected
    UiError.NOT_CONNECTED -> R.string.error_not_connected
    UiError.NO_REPLY -> R.string.error_no_reply
    UiError.UNKNOWN -> R.string.error_unknown
}

/** Maps a failed repository request to the error a screen shows. */
internal fun Throwable.toUiError(): UiError = when (this) {
    is AncRejectedException -> UiError.REJECTED
    is NotConnectedException -> UiError.NOT_CONNECTED
    is RequestTimeoutException, is SessionClosedException, is SettingUnavailableException -> UiError.NO_REPLY
    else -> UiError.UNKNOWN
}
