// LibreBuds - SPDX-License-Identifier: GPL-3.0-or-later
// PairFree GPLv3 runtime and Bluetooth drivers are sourced from the pinned upstream submodule.
// The LibreBuds UI remains independent of the upstream PairFree UI.
package io.github.librebuds.ui.other

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import io.github.omegadestroyer.pairfree.bluetooth.AndroidTransport
import io.github.omegadestroyer.pairfree.bluetooth.androidPlatform
import io.github.omegadestroyer.pairfree.bluetooth.EarbudsConnection
import io.github.omegadestroyer.pairfree.bluetooth.LinkStatus
import io.github.omegadestroyer.pairfree.brands.Brands
import io.github.omegadestroyer.pairfree.eq.EqOpener
import io.github.omegadestroyer.pairfree.eq.EqProfile
import io.github.omegadestroyer.pairfree.eq.EqState
import io.github.omegadestroyer.pairfree.eq.Equalizer
import io.github.omegadestroyer.pairfree.eq.TextStore
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Identify
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Delegates control to PairFree's 17 per-brand native drivers, link transports,
 * state decoders and capability checks. It never handles Huawei FreeBuds.
 *
 * Capability state comes from actual reports and model tables, not name guesses.
 * A paired model may still be unsupported on this firmware or Android device.
 */
class PairFreeController(
    private val context: Context,
    private val address: String,
    private val deviceName: String,
) : VendorController {
    private val _state = MutableStateFlow(NothingStatus())
    override val state: StateFlow<NothingStatus> = _state.asStateFlow()
    private val _groups = MutableStateFlow<List<SettingGroup>>(emptyList())
    val groups: StateFlow<List<SettingGroup>> = _groups.asStateFlow()
    private var connection: EarbudsConnection? = null
    private val _eqState = MutableStateFlow<EqState?>(null)
    val eqState: StateFlow<EqState?> = _eqState.asStateFlow()
    private var equalizer: Equalizer? = null

    override suspend fun run() {
        if (OtherEarbudsCatalog.isHuaweiManaged(deviceName)) {
            _state.value = NothingStatus(error = "Huawei earbuds use the original FreeBuds controller.")
            return
        }
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            _state.value = NothingStatus(error = "Bluetooth permission is required")
            return
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = NothingStatus(error = "Bluetooth is off")
            return
        }
        try {
            @Suppress("MissingPermission")
            val pairedDevice = adapter.getRemoteDevice(address)
            @Suppress("MissingPermission")
            val uuids = pairedDevice.uuids?.map { it.toString() }.orEmpty()
            val maker = Identify.maker(deviceName, uuids)
            val matched = maker?.let { Brands[it].paired(address, deviceName, uuids) }
            if (matched == null) {
                _state.value = NothingStatus(status = "Standard Bluetooth audio",
                    error = "A vendor control protocol could not be identified for this model.")
                return
            }
            coroutineScope {
                val controller = EarbudsConnection(
                    scope = this,
                    transport = AndroidTransport(context.applicationContext, adapter),
                    platform = androidPlatform(),
                    log = null,
                )
                connection = controller
                val shared = context.getSharedPreferences("pairfree_eq_profiles", Context.MODE_PRIVATE)
                val store = object : TextStore {
                    override fun get(key: String): String? = shared.getString(key, null)
                    override fun put(key: String, value: String) {
                        shared.edit().putString(key, value).apply()
                    }
                }
                val engine = Equalizer(store, this, send = controller::sendEq)
                equalizer = engine
                val eqOpener = EqOpener(controller, engine, this)
                val eqJob = launch { engine.state.collect { _eqState.value = it } }
                val statusJob = launch {
                    controller.status.collect { status ->
                        when (status) {
                            is LinkStatus.Connected -> {
                                _state.value = _state.value.copy(status = "Connected", ready = true, error = null)
                                eqOpener.open(matched, Brands[matched.brand].model(matched, controller.state.value))
                            }
                            is LinkStatus.Connecting ->
                                _state.value = _state.value.copy(status = "Connecting to vendor control channel…", ready = false)
                            is LinkStatus.Reconnecting ->
                                _state.value = _state.value.copy(status = "Reconnecting…", ready = false)
                            is LinkStatus.Failed ->
                                _state.value = _state.value.copy(status = "Control channel unavailable", ready = false,
                                    error = status.reason.name.lowercase().replace('_', ' '))
                            is LinkStatus.Idle ->
                                _state.value = _state.value.copy(status = "Not connected", ready = false)
                        }
                    }
                }
                val valuesJob = launch {
                    controller.state.collect { status ->
                        val batteries = listOfNotNull(
                            status.batteryLeft?.takeIf { it in 0..100 }?.let { 2 to it },
                            status.batteryRight?.takeIf { it in 0..100 }?.let { 3 to it },
                            status.batteryCase?.takeIf { it in 0..100 }?.let { 4 to it }
                        ).toMap()
                        val noise = status.number(Command.ANC_MODE)?.let { code ->
                            when (Command.ANC_MODE.labels[code]) {
                                "off" -> NothingProtocol.OFF
                                "anc", "adaptive", "weak" -> NothingProtocol.ANC_HIGH
                                "transparency" -> NothingProtocol.TRANSPARENCY
                                else -> null
                            }
                        }
                        _state.value = _state.value.copy(battery = batteries, noise = noise)
                        if (controller.status.value is LinkStatus.Connected) {
                            eqOpener.open(matched, Brands[matched.brand].model(matched, status))
                        }
                    }
                }
                val groupsJob = launch {
                    controller.settings.collect { _groups.value = it }
                }
                try {
                    controller.connect(matched)
                    awaitCancellation()
                } finally {
                    controller.disconnect()
                    connection = null
                    equalizer = null
                    _eqState.value = null
                    eqJob.cancel()
                    statusJob.cancel()
                    valuesJob.cancel()
                    groupsJob.cancel()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            _state.value = _state.value.copy(status = "Connection unavailable", ready = false,
                error = exception.message ?: "Could not initialize vendor control")
        }
    }

    override suspend fun changeNoise(mode: Int) {
        val value = when (mode) {
            NothingProtocol.OFF -> 0
            NothingProtocol.ANC_HIGH -> 1
            NothingProtocol.TRANSPARENCY -> 2
            else -> return
        }
        val active = connection ?: return
        // Only write after the earbuds have reported the shared ANC control.
        if (active.status.value !is LinkStatus.Connected ||
            !active.state.value.supports(Command.ANC_MODE) ||
            value !in Command.ANC_MODE.labels.keys) return
        active.set(Command.ANC_MODE, value)
    }

    override suspend fun changeEq(preset: Int) {
        // EQ transport differs by brand. PairFree's vendor-specific groups and EQ
        // targets are the authoritative source; don't send a guessed generic code.
    }

    fun selectEq(profile: EqProfile) {
        if (connection?.status?.value is LinkStatus.Connected) {
            equalizer?.select(profile)
        }
    }

    fun setEqGain(index: Int, db: Double) {
        val state = _eqState.value ?: return
        if (connection?.status?.value !is LinkStatus.Connected || !state.target.hasCustom) return
        if (index !in state.curve.indices) return
        equalizer?.setGain(index, db.coerceIn(
            -state.layout.userRangeDb.toDouble(), state.layout.userRangeDb.toDouble()))
    }

    fun changeSetting(change: SettingChange) {
        if (connection?.status?.value is LinkStatus.Connected) {
            connection?.changeSetting(change)
        }
    }

    override fun close() {
        connection?.disconnect()
        connection = null
    }
}
