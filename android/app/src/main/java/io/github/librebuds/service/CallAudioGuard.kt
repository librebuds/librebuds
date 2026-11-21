// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.service

import android.media.AudioManager

/**
 * During a phone or VoIP call the OS owns the Bluetooth hands-free path. A competing vendor
 * control-channel reconnect is unnecessary and can overlap SCO/HFP setup on some devices.
 *
 * This does not change microphone gain, choose the communication device, or affect an
 * already-established earbud control session. Resume normal auto-connect once audio ends.
 */
internal fun shouldDeferControlConnect(audioMode: Int, headsetAudioActive: Boolean): Boolean =
    headsetAudioActive ||
        audioMode == AudioManager.MODE_IN_CALL ||
        audioMode == AudioManager.MODE_IN_COMMUNICATION ||
        audioMode == AudioManager.MODE_CALL_SCREENING
