// SPDX-License-Identifier: GPL-3.0-or-later
// Read-only TOZO GATT protocol adapter. Protocol A/J wire formats documented by
// arkivx0/BluntyBuds (MIT, Copyright (c) 2026 arkivx0); see NOTICE.
package io.github.librebuds.ui.other

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Opt-in, read-only TOZO A/J adapter. The device's actual GATT characteristics
 * decide the protocol; a TOZO model name alone never authorizes a write.
 * Sends battery queries only. No ANC, EQ, gesture, or firmware writes.
 */
class TozoController(private val context: Context, private val address: String) : VendorController {
    private val _state = MutableStateFlow(NothingStatus())
    override val state = _state.asStateFlow()
    private var gatt: BluetoothGatt? = null
    private var protocol: Char? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    private val jService = UUID.fromString("0000faa0-0000-1000-8000-00805f9b34fb")
    private val jWrite = UUID.fromString("0000faa1-0000-1000-8000-00805f9b34fb")
    private val jNotify = UUID.fromString("0000faa2-0000-1000-8000-00805f9b34fb")
    private val aWrite = UUID.fromString("4157b611-d129-aeb5-859b-d547e30ffcb1")
    private val aNotify = UUID.fromString("4157b612-d129-aeb5-859b-d547e30ffcb1")
    private val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private fun frameA(cmd: Int) = byteArrayOf((cmd shr 8).toByte(), cmd.toByte(), 0, 0)
    private fun frameJ(cmd: Int): ByteArray {
        val checksum = (0xAA + cmd) and 0xff
        return byteArrayOf(0xAA.toByte(), cmd.toByte(), 0, checksum.toByte())
    }

    private fun batteryByte(v: Byte): Int? {
        val n = v.toInt() and 0xff
        val level = if (n and 0x80 != 0) n and 0x7f else n
        return level.takeIf { n != 0xff && it in 0..100 }
    }

    private fun handleA(data: ByteArray) {
        if (data.size < 4) return
        val length = data[2].toInt() and 0xff
        if (length + 4 != data.size) return
        val payload = data.copyOfRange(3, 3 + length)
        val checksum = payload.fold(0) { sum, b -> sum + (b.toInt() and 0xff) } and 0xff
        if (checksum != (data.last().toInt() and 0xff)) return
        val command = ((data[0].toInt() and 0xff) shl 8) or (data[1].toInt() and 0xff)
        val battery = _state.value.battery.toMutableMap()
        when (command) {
            0x0002, 0x200e -> {
                if (payload.isNotEmpty()) batteryByte(payload[0])?.let { battery[2] = it }
                if (payload.size > 1) batteryByte(payload[1])?.let { battery[3] = it }
            }
            0x0102, 0x210e -> if (payload.isNotEmpty()) {
                batteryByte(payload[0])?.let { battery[4] = it }
            }
            else -> return
        }
        _state.value = _state.value.copy(battery = battery)
    }

    private fun handleJ(data: ByteArray) {
        var index = 0
        while (index + 4 <= data.size) {
            if ((data[index].toInt() and 0xff) != 0xa5) { index++; continue }
            val command = data[index + 1].toInt() and 0xff
            val len = data[index + 2].toInt() and 0xff
            val end = index + 3 + len
            if (end >= data.size) return
            val sum = (index until end).sumOf { data[it].toInt() and 0xff } and 0xff
            if (sum == (data[end].toInt() and 0xff) && len == 1) {
                val side = when (command) { 0x14 -> 2; 0x15 -> 3; else -> null }
                if (side != null) batteryByte(data[index + 3])?.let {
                    _state.value = _state.value.copy(battery = _state.value.battery + (side to it))
                }
            }
            index = end + 1
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (gatt !== g) return
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                _state.value = _state.value.copy(status = "Discovering TOZO GATT services…")
                g.discoverServices()
            } else {
                _state.value = _state.value.copy(status = "TOZO control disconnected", ready = false,
                    error = if (status == BluetoothGatt.GATT_SUCCESS) null else "GATT status $status")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (gatt !== g || status != BluetoothGatt.GATT_SUCCESS) return
            val j = g.getService(jService)
            val jw = j?.getCharacteristic(jWrite)
            val jn = j?.getCharacteristic(jNotify)
            val aw = g.services.firstNotNullOfOrNull { it.getCharacteristic(aWrite) }
            val an = g.services.firstNotNullOfOrNull { it.getCharacteristic(aNotify) }
            when {
                jw != null && jn != null -> { protocol = 'J'; writeChar = jw; notifyChar = jn }
                aw != null && an != null -> { protocol = 'A'; writeChar = aw; notifyChar = an }
                else -> {
                    _state.value = NothingStatus(status = "Generic Bluetooth audio",
                        error = "This TOZO hardware revision does not expose a supported A/J GATT service.")
                    return
                }
            }
            val n = notifyChar ?: return
            if (!g.setCharacteristicNotification(n, true)) {
                _state.value = NothingStatus(error = "TOZO notifications unavailable")
                return
            }
            val descriptor = n.getDescriptor(cccd) ?: run {
                _state.value = NothingStatus(error = "TOZO notification descriptor missing")
                return
            }
            _state.value = NothingStatus(status = "TOZO protocol ${protocol} detected (battery read-only)", ready = true)
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (gatt !== g || descriptor.uuid != cccd || status != BluetoothGatt.GATT_SUCCESS) return
            sendBatteryQuery(g, first = true)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (gatt !== g || status != BluetoothGatt.GATT_SUCCESS) return
            if (protocol == 'J') sendBatteryQuery(g, first = false)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (gatt !== g || characteristic.uuid != notifyChar?.uuid) return
            if (protocol == 'A') handleA(value) else if (protocol == 'J') handleJ(value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            onCharacteristicChanged(g, characteristic, characteristic.value ?: return)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendBatteryQuery(g: BluetoothGatt, first: Boolean) {
        val characteristic = writeChar ?: return
        val data = when (protocol) {
            'A' -> if (first) frameA(0x0002) else return
            'J' -> frameJ(if (first) 0x14 else 0x15)
            else -> return
        }
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = data
            @Suppress("DEPRECATION")
            g.writeCharacteristic(characteristic)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun run() {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            _state.value = NothingStatus(error = "Bluetooth permission required")
            return
        }
        try {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: error("Bluetooth unavailable")
            _state.value = NothingStatus(status = "Connecting to TOZO control service…")
            gatt = adapter.getRemoteDevice(address).connectGatt(context, false, callback)
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            _state.value = NothingStatus(error = e.message ?: "Could not open TOZO GATT link")
        } finally {
            close()
        }
    }

    override suspend fun changeNoise(mode: Int) = Unit // Read-only until physical validation.
    override suspend fun changeEq(preset: Int) = Unit // Read-only until physical validation.
    @SuppressLint("MissingPermission")
    override fun close() {
        val old = gatt
        gatt = null
        runCatching { old?.disconnect() }
        runCatching { old?.close() }
        _state.value = _state.value.copy(ready = false)
    }
}
