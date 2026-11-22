// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.service

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallAudioGuardTest {
    @Test fun voiceCallDefersControlReconnect() {
        assertTrue(shouldDeferControlConnect(AudioManager.MODE_IN_CALL, false))
    }

    @Test fun whatsappStyleCommunicationDefersControlReconnect() {
        assertTrue(shouldDeferControlConnect(AudioManager.MODE_IN_COMMUNICATION, false))
    }

    @Test fun headsetAudioNegotiationDefersEvenBeforeModeChanges() {
        assertTrue(shouldDeferControlConnect(AudioManager.MODE_NORMAL, true))
    }

    @Test fun normalMusicListeningStillAllowsConnection() {
        assertFalse(shouldDeferControlConnect(AudioManager.MODE_NORMAL, false))
    }
}
