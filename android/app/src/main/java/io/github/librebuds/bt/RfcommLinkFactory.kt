// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.bt

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** Opens the earbuds' Serial Port Profile channel. Blocking calls run on the IO dispatcher. */
class RfcommLinkFactory(private val adapter: BluetoothAdapter) : LinkFactory {
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun open(address: String): Link = withContext(Dispatchers.IO) {
        val socket = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            socket.connect()
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw e
        }
        RfcommLink(socket)
    }

    private class RfcommLink(private val socket: BluetoothSocket) : Link {
        private val input = socket.inputStream
        private val output = socket.outputStream

        override suspend fun read(buffer: ByteArray): Int = withContext(Dispatchers.IO) { input.read(buffer) }

        override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
            output.write(bytes)
            output.flush()
        }

        // Closing the socket also unblocks a pending read.
        override fun close() {
            runCatching { socket.close() }
        }
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    }
}
