// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds

import io.github.librebuds.protocol.command.Battery
import io.github.librebuds.protocol.util.toHex
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolLinkTest {
    @Test
    fun appSeesProtocolModule() {
        assertEquals("5A 00 09 00 01 08 01 00 02 00 03 00 FB B9", Battery.request().toFrame().toHex())
    }
}
