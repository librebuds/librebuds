// SPDX-License-Identifier: GPL-3.0-or-later
// Nothing / CMF control protocol adapted from PairFree (GPLv3); see NOTICE.
package io.github.librebuds.ui.other

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

data class NothingStatus(
    val status: String = "Not connected",
    val ready: Boolean = false,
    val battery: Map<Int, Int> = emptyMap(),
    val noise: Int? = null,
    val eq: Int? = null,
    val error: String? = null,
)

/**
 * A real, independently managed Nothing/CMF RFCOMM link. Never opens a Huawei
 * control channel; writes only the limited PairFree-documented read/noise/EQ
 * commands, and reads back settings after changes.
 *
 * BluetoothClassic sockets require the earbud to be bonded already.
 */
class NothingController(private val context: Context, private val address: String, private val deviceName: String) : VendorController {
    private val _state = MutableStateFlow(NothingStatus())
    override val state = _state.asStateFlow()
    private val lock = Mutex()
    private val requestLock = Mutex()
    private var pendingResponse: Pair<Int, CompletableDeferred<NothingProtocol.Response>>? = null
    private val sequence = AtomicInteger()
    private var socket: BluetoothSocket? = null
    private val parser = NothingProtocol.Parser()
    private val useCrc = !deviceName.equals("ear (1)", ignoreCase = true) &&
        !deviceName.equals("Nothing Ear (1)", ignoreCase = true)
    private var needsActivation = false
    private var activeScope: CoroutineScope? = null

    @SuppressLint("MissingPermission")
    override suspend fun run() {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            _state.value = NothingStatus(error = "Bluetooth permission is required")
            return
        }
        _state.value = NothingStatus(status = "Connecting to earbuds…")
        try {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: throw IOException("Bluetooth adapter unavailable")
            val remote = adapter.getRemoteDevice(address)
            val opened = withContext(Dispatchers.IO) {
                remote.createRfcommSocketToServiceRecord(UUID.fromString(NothingProtocol.SERVICE_UUID))
                    .also {
                        socket = it // published early so dispose can close a pending connect
                        it.connect()
                    }
            }
            _state.value = NothingStatus(status = "Reading earbud settings…", ready = true)
            // Firmware may not support each read. A missing reply is not a
            // reason to make up a setting; the respective control stays hidden.
            val requests = listOf(
                NothingProtocol.GET_PROTOCOL_VERSION,
                NothingProtocol.GET_DEVICE_MODEL,
                NothingProtocol.GET_BATTERY,
                NothingProtocol.GET_NOISE,
                NothingProtocol.GET_EQ_MODE,
            )
            // Read concurrently with the command writer; classic Bluetooth
            // InputStream.read() blocks without affecting the UI thread.
            kotlinx.coroutines.coroutineScope {
                activeScope = this
                launchReads(requests)
                val buffer = ByteArray(2048)
                while (coroutineContext.isActive) {
                    val count = withContext(Dispatchers.IO) { opened.inputStream.read(buffer) }
                    if (count < 0) throw IOException("Earbud connection closed")
                    if (count == 0) continue
                    parser.feed(buffer.copyOf(count)).forEach(::receive)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            _state.value = _state.value.copy(status = "Connection unavailable", ready = false,
                error = e.message ?: "Device refused the control channel")
        } finally {
            activeScope = null
            close()
            _state.value = _state.value.copy(ready = false)
        }
    }

    private fun kotlinx.coroutines.CoroutineScope.launchReads(requests: List<Int>) = launch {
        for (command in requests) {
            if (!isActive) break
            request(command, if (command == NothingProtocol.GET_NOISE) byteArrayOf(3) else byteArrayOf())
            delay(180)
        }
        // Poll device battery and noise while the controls screen is open.
        while (isActive) {
            delay(30000)
            request(NothingProtocol.GET_BATTERY)
            delay(180)
            request(NothingProtocol.GET_NOISE, byteArrayOf(3))
        }
    }

    private fun receive(response: NothingProtocol.Response) {
        // Reply commands clear bit 15; notifications retain it.
        val answer = response.command or 0x8000
        pendingResponse?.let { (command, deferred) ->
            if (command == answer && !deferred.isCompleted) deferred.complete(response)
        }
        if (response.responseCode == 2 && !needsActivation) {
            needsActivation = true
            // Activation is handled by a response to the version read, not
            // continuously spammed on devices which don't need it.
            activeScope?.launch {
                send(0xF001)
                delay(150)
                send(NothingProtocol.GET_BATTERY)
                send(NothingProtocol.GET_NOISE)
                send(NothingProtocol.GET_EQ_MODE)
            }
            return
        }
        if (!response.success) return
        when {
            answer == NothingProtocol.GET_BATTERY || response.command == NothingProtocol.EVENT_BATTERY ->
                _state.value = _state.value.copy(battery = NothingProtocol.battery(response.payload), status = "Connected")
            answer == NothingProtocol.GET_NOISE || response.command == NothingProtocol.EVENT_NOISE ->
                NothingProtocol.noiseMode(response.payload)?.let { mode ->
                    _state.value = _state.value.copy(noise = mode, status = "Connected")
                }
            answer == NothingProtocol.GET_EQ_MODE ->
                if (response.payload.size >= 2) {
                    val v = (response.payload[0].toInt() and 0xff) or ((response.payload[1].toInt() and 0xff) shl 8)
                    _state.value = _state.value.copy(eq = v, status = "Connected")
                }
        }
    }

    override suspend fun changeNoise(mode: Int) {
        if (!_state.value.ready || mode !in listOf(1, 5, 7)) return
        // This is Nothing X SET_CURRENT_NOISE_REDUCTION (group 1, mode, zero).
        request(NothingProtocol.SET_NOISE, byteArrayOf(1, mode.toByte(), 0))
        delay(350)
        request(NothingProtocol.GET_NOISE, byteArrayOf(3))
    }

    override suspend fun changeEq(preset: Int) {
        if (!_state.value.ready || _state.value.eq == null || preset !in 0..3) return
        request(NothingProtocol.SET_EQ_MODE, byteArrayOf(preset.toByte(), 0))
        delay(350)
        request(NothingProtocol.GET_EQ_MODE)
    }

    /**
     * Only one vendor request at a time. Nothing X and PairFree expect replies
     * in order; a firmware that ignores a command times out in one second.
     */
    private suspend fun request(command: Int, payload: ByteArray = byteArrayOf()): NothingProtocol.Response? =
        requestLock.withLock {
            val waiter = CompletableDeferred<NothingProtocol.Response>()
            pendingResponse = command to waiter
            try {
                send(command, payload)
                withTimeoutOrNull(1000) { waiter.await() }
            } finally {
                pendingResponse = null
            }
        }

    private suspend fun send(command: Int, payload: ByteArray = byteArrayOf()) {
        lock.withLock {
            val activeSocket = socket ?: return
            val id = sequence.getAndUpdate { (it + 1) % 254 }
            try {
                val bytes = NothingProtocol.frame(command, id, payload, crc = useCrc)
                withContext(Dispatchers.IO) {
                    activeSocket.outputStream.write(bytes)
                    activeSocket.outputStream.flush()
                }
            } catch (_: IOException) {
                _state.value = _state.value.copy(ready = false, error = "Could not send the earbud command")
            }
        }
    }

    override fun close() {
        val old = socket
        socket = null
        runCatching { old?.close() }
    }
}
