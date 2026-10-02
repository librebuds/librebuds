// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.session

import io.github.librebuds.bt.LinkFactory
import io.github.librebuds.protocol.CommandId
import io.github.librebuds.protocol.Packet
import io.github.librebuds.diag.FrameLog
import io.github.librebuds.protocol.command.DeviceInfoCommand
import io.github.librebuds.protocol.command.Gesture
import io.github.librebuds.protocol.command.Gestures
import io.github.librebuds.protocol.frame.FrameReassembler
import io.github.librebuds.protocol.frame.LinkFrame
import io.github.librebuds.protocol.frame.RxEvent
import io.github.librebuds.protocol.profile.ProfileRegistry
import io.github.librebuds.protocol.tlv.Tlv
import io.github.librebuds.protocol.util.hexToBytes
import io.github.librebuds.protocol.util.toHex
import io.github.librebuds.state.LinkState
import io.github.librebuds.state.SettingChange
import io.github.librebuds.ui.SettingsViewModel
import io.github.librebuds.ui.model.GestureControl
import io.github.librebuds.ui.model.Picker
import io.github.librebuds.ui.model.SettingsModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Replays a FreeBuds 5 session captured from the phone's Bluetooth HCI log: every request the app
 * sends is answered with the exact reply frame the earbuds sent (only the device info reply is
 * rebuilt, with test serials), and the pushes the earbuds sent after a write follow its ack. The real
 * controller, view model and settings model run on top, and the assertions are what the settings
 * screen shows with the shipped freebuds-5 profile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Fb5ReplayTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** The captured earbuds. State changes only through the writes the capture shows them answering. */
    private class Fb5Earbuds {
        var equalizer = 1
        var soundQuality = 0
        var tripleTapLeft = 2
        val requests = mutableListOf<String>()
        val unanswered = mutableListOf<String>()
        private val reassembler = FrameReassembler()

        fun link(): FakeLink = FakeLink { bytes ->
            for (event in reassembler.feed(bytes)) {
                if (event !is RxEvent.Payload) continue
                val request = event.bytes.toHex()
                requests += request
                val replies = answer(event.bytes)
                if (replies.isEmpty()) unanswered += request
                // Frames the earbuds sent back to back arrive in one read, as on the socket.
                if (replies.isNotEmpty()) deliver(replies.fold(ByteArray(0)) { acc, frame -> acc + frame })
            }
        }

        /** What the earbuds sent unasked right after the link opened. */
        val greeting = listOf(frame("5A 00 06 00 0A 0E 01 01 00 A2 6F"), frame("5A 00 03 00 01 06 3E BD"))

        private fun answer(payload: ByteArray): List<ByteArray> {
            val hex = payload.toHex()
            if (hex.startsWith("01 07")) return listOf(DEVICE_INFO)
            CAPTURED[hex]?.let { return listOf(frame(it)) }
            return when (hex) {
                "01 26 01 00 02 00" -> listOf(tripleTap())
                "2B 4A 02 00" -> listOf(frame(EQUALIZER.getValue(equalizer)))
                "2B A3 01 00" -> listOf(frame(SOUND_QUALITY.getValue(soundQuality)))
                // Equalizer select: the status reply, then the 2B/4A push with the new active preset.
                in EQUALIZER.keys.map { "2B 49 01 01 %02X".format(it) } -> {
                    equalizer = payload[4].toInt()
                    listOf(frame("5A 00 09 00 2B 49 7F 04 00 01 86 A0 B3 C6"), frame(EQUALIZER.getValue(equalizer)))
                }
                // Sound quality write: the status reply, then the 2B/A3 push; in-ear and 2B/5E pushes around it.
                "2B A2 01 01 00", "2B A2 01 01 01" -> {
                    soundQuality = payload[4].toInt()
                    listOf(
                        frame("5A 00 09 00 2B A2 7F 04 00 01 86 A0 E0 0A"),
                        Packet(CommandId(0x2B, 0x5E), listOf(Tlv.of(1, 1))).toFrame(),
                        frame(SOUND_QUALITY.getValue(soundQuality)),
                        Packet(CommandId(0x2B, 0x03), listOf(Tlv.of(8, 1))).toFrame(),
                    )
                }
                // Triple tap, left side: the gesture ack reports the left side applied (TLV 3 = 0).
                "01 25 01 01 07" -> {
                    tripleTapLeft = 7
                    listOf(Packet(Gesture.TRIPLE_TAP.set, listOf(Tlv.of(Gestures.ACK_TYPE, Gestures.LEFT_OK))).toFrame())
                }
                else -> emptyList()
            }
        }

        /** The captured triple tap reply, with the left value the earbuds hold now. */
        private fun tripleTap(): ByteArray =
            LinkFrame.encode("01 26 01 01 %02X 02 01 02 03 06 02 04 05 06 07 FF".format(tripleTapLeft).hexToBytes())

        companion object {
            /** A captured frame, checked to be whole and intact. */
            fun frame(hex: String): ByteArray {
                val bytes = hex.hexToBytes()
                val events = FrameReassembler().feed(bytes)
                check(events.size == 1 && events[0] is RxEvent.Payload) { "not one valid frame: $hex" }
                return bytes
            }

            /** Request payload -> the earbuds' reply frame, as captured. */
            val CAPTURED = mapOf(
                "01 08 01 00 02 00 03 00" to "5A 00 14 00 01 08 01 01 49 02 03 50 49 4D 03 03 00 00 00 04 02 14 0A C6 EA",
                "2B 2A 01 00" to "5A 00 07 00 2B 2A 01 02 03 00 40 62",
                "2B 11 01 00" to "5A 00 06 00 2B 11 01 01 01 DF E2",
                "01 20 01 00 02 00 04 00" to "5A 00 1A 00 01 20 01 01 01 02 01 01 03 05 01 07 02 00 FF 04 01 00 05 01 00 06 02 00 FF 74 D7",
                "2B 17 01 00 02 00 04 00" to "5A 00 24 00 2B 17 01 01 FF 02 01 FF 03 0F 00 01 02 03 04 05 06 07 08 09 0A 0E 0F 11 FF " +
                    "04 01 00 05 01 00 06 02 00 FF F2 C4",
                "2B 1F 01 00 02 00" to "5A 00 0E 00 2B 1F 01 01 00 02 01 00 03 03 00 01 FF 24 DF",
                "2B 6C 03 00" to "5A 00 06 00 2B 6C 03 01 00 DA 50",
                "2B 6C 02 00" to "5A 00 06 00 2B 6C 02 01 00 ED 60",
                "0C 02 01 00 02 00 03 00" to "5A 00 13 00 0C 02 03 0B 7A 68 2D 43 4E 2C 65 6E 2D 47 42 04 01 01 EE A7",
                "2B 2F 01 00" to "5A 00 06 00 2B 2F 01 01 01 51 51",
            )

            /** 2B/4A replies and pushes by active preset, as captured. */
            val EQUALIZER = mapOf(
                1 to "5A 00 14 00 2B 4A 01 01 01 02 01 01 03 04 01 02 03 09 04 01 01 08 00 74 2D",
                2 to "5A 00 14 00 2B 4A 01 01 01 02 01 02 03 04 01 02 03 09 04 01 01 08 00 71 B2",
                9 to "5A 00 14 00 2B 4A 01 01 01 02 01 09 03 04 01 02 03 09 04 01 01 08 00 6F 85",
            )

            /** 2B/A3 replies and pushes by value, as captured. */
            val SOUND_QUALITY = mapOf(
                0 to "5A 00 09 00 2B A3 01 01 01 02 01 00 A6 02",
                1 to "5A 00 09 00 2B A3 01 01 01 02 01 01 B6 23",
            )

            /** The captured device info reply rebuilt with test strings in place of the serials and firmware. */
            val DEVICE_INFO: ByteArray = Packet(
                CommandId(0x01, 0x07),
                listOf(
                    Tlv.of(2, 0x01, 0x41),
                    Tlv(3, "TESTMODEL".toByteArray()),
                    Tlv(7, "1.0.0.100".toByteArray()),
                    Tlv(9, "TESTSERIAL000001".toByteArray()),
                    Tlv(0x0A, "BTFT0013-000141".toByteArray()),
                    Tlv(0x0F, "BTFT0013".toByteArray()),
                    Tlv(0x18, "TESTSERIAL000001".toByteArray()),
                    Tlv.of(0x19, 0),
                ),
            ).toFrame()
        }
    }

    private fun profiles(): ProfileRegistry {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "profiles/freebuds-5.json").isFile) dir = dir.parentFile
        val root = requireNotNull(dir) { "profiles/ not found above ${File("").absolutePath}" }
        return ProfileRegistry.fromJson(File(root, "profiles").listFiles { f -> f.name.endsWith(".json") }!!.sorted().map { it.readText() })
    }

    private class Session(val earbuds: Fb5Earbuds, val controller: BudsController, val vm: SettingsViewModel, val logcat: List<String>) {
        val model: SettingsModel get() = vm.ui.value.model
        fun gesture(subKey: String): GestureControl = model.gestures.firstOrNull { it.subKey == subKey } ?: throw AssertionError("no $subKey row in ${model.gestures.map { it.subKey }}")
    }

    private suspend fun TestScope.connect(): Session {
        val registry = profiles()
        val earbuds = Fb5Earbuds()
        val logcat = mutableListOf<String>()
        val controller = BudsController(
            linkFactory = LinkFactory { earbuds.link().also { link -> earbuds.greeting.forEach { link.deliver(it) } } },
            registry = registry,
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
            frameLog = FrameLog(logcat = { logcat += it }),
        )
        val vm = SettingsViewModel(controller) { id -> registry.profiles.firstOrNull { it.id == id } ?: ProfileRegistry.GENERIC }
        controller.connect("00:00:00:00:00:01", "Test buds")
        return Session(earbuds, controller, vm, logcat)
    }

    private fun Picker?.codes(): List<Int>? = this?.options?.map { it.code }

    private fun assertPicker(name: String, current: Int, options: List<Int>, picker: Picker?) {
        assertNotNull("$name row missing", picker)
        assertEquals("$name current", current, picker!!.current)
        assertEquals("$name options", options, picker.codes())
    }

    @Test
    fun connectShowsWhatTheEarbudsReported() = runTest(dispatcher) {
        val s = connect()
        advanceUntilIdle()
        assertEquals(LinkState.CONNECTED, s.controller.state.value.link)
        assertEquals("freebuds-5", s.controller.state.value.profileId)
        assertEquals("01 07", s.earbuds.requests.first().take(5))
        assertTrue("no session hello", s.earbuds.requests.none { it.startsWith("2B 70") })
        // Every read the app sends is one the earbuds answered in the capture (the host list has no rows there).
        assertEquals(emptyList<String>(), s.earbuds.unanswered.filterNot { it.startsWith("2B 31") })
        assertEquals(emptySet<String>(), s.controller.state.value.settings.unanswered)
        assertEquals(emptySet<String>(), s.controller.state.value.settings.unsupported)

        val m = s.model
        assertEquals(listOf("doubleTap", "tripleTap", "longPress", "swipe"), m.gestures.map { it.subKey })
        s.gesture("doubleTap").let {
            assertPicker("double tap left", 1, listOf(1, 2, 7, 0, -1), it.left)
            assertPicker("double tap right", 1, listOf(1, 2, 7, 0, -1), it.right)
            assertPicker("double tap in call", 0, listOf(0, -1), it.inCall)
        }
        s.gesture("tripleTap").let {
            assertPicker("triple tap left", 2, listOf(2, 7, -1), it.left)
            assertPicker("triple tap right", 2, listOf(2, 7, -1), it.right)
            assertNull("triple tap has no in-call action", it.inCall)
        }
        s.gesture("longPress").let {
            assertPicker("long press left", -1, listOf(3, -1), it.left)
            assertPicker("long press right", -1, listOf(3, -1), it.right)
            assertPicker("long press in call", 0, listOf(0, -1), it.inCall)
        }
        s.gesture("swipe").let {
            assertPicker("swipe", 0, listOf(0, -1), it.left)
            assertNull("swipe is one value for both earbuds", it.right)
            assertNull(it.inCall)
        }
        assertPicker("equalizer", 1, listOf(1, 2, 3, 9), m.equalizer)
        assertPicker("sound quality", 0, listOf(0, 1), m.soundQuality)
        assertEquals(false, m.lowLatency)
        assertFalse(m.dynamicLatency)
        assertEquals(true, m.wear)
        assertNotNull(m.language)
        assertEquals(true, m.multipointEnabled)

        // Logcat: one line per whole frame in both directions, the device info reply left out.
        val reassembler = FrameReassembler()
        for (line in s.logcat) {
            val bytes = line.substringAfter(' ').hexToBytes()
            assertTrue(line, line.startsWith("TX 5A") || line.startsWith("RX 5A"))
            assertEquals(line, 1, reassembler.feed(bytes).count { it is RxEvent.Payload })
        }
        assertTrue(s.logcat.none { it.startsWith("RX 5A") && it.substring(15, 20) == "01 07" })
        assertTrue(s.logcat.contains("TX " + DeviceInfoCommand.request().toFrame().toHex()))
        assertTrue(s.logcat.contains("RX 5A 00 11 00 01 26 01 01 02 02 01 02 03 06 02 04 05 06 07 FF 32 99"))
        // Every request but the host list query (2B/31, left out both ways).
        assertEquals(s.earbuds.requests.count { !it.startsWith("2B 31") }, s.logcat.count { it.startsWith("TX") })
    }

    @Test
    fun equalizerSelectionSticks() = runTest(dispatcher) {
        val s = connect()
        advanceUntilIdle()
        s.vm.apply(SettingChange.EqualizerPreset(2))
        advanceUntilIdle()
        assertEquals(emptySet<String>(), s.vm.ui.value.pending)
        assertNull(s.vm.ui.value.error)
        assertPicker("equalizer", 2, listOf(1, 2, 3, 9), s.model.equalizer)
        // A later read (the screen opened again) still shows it.
        s.controller.refresh()
        advanceUntilIdle()
        assertEquals(2, s.model.equalizer?.current)
        assertEquals(2, s.earbuds.equalizer)
    }

    @Test
    fun soundQualityCheckFollowsTheEarbuds() = runTest(dispatcher) {
        val s = connect()
        advanceUntilIdle()
        for (value in listOf(0, 1, 0)) {
            s.vm.apply(SettingChange.SoundQualityChange(value))
            advanceUntilIdle()
            assertNull("error after writing $value", s.vm.ui.value.error)
            assertEquals(emptySet<String>(), s.vm.ui.value.pending)
            assertPicker("sound quality after $value", value, listOf(0, 1), s.model.soundQuality)
            assertEquals(value, s.earbuds.soundQuality)
        }
        val writes = s.earbuds.requests.filter { it.startsWith("2B A2") }
        assertEquals(listOf("2B A2 01 01 00", "2B A2 01 01 01", "2B A2 01 01 00"), writes)
    }

    @Test
    fun tripleTapChangeIsShown() = runTest(dispatcher) {
        val s = connect()
        advanceUntilIdle()
        s.vm.apply(SettingChange.GestureChange(Gesture.TRIPLE_TAP, left = 7, right = null, inCall = null))
        advanceUntilIdle()
        assertNull(s.vm.ui.value.error)
        assertEquals(emptySet<String>(), s.vm.ui.value.pending)
        assertEquals(listOf("01 25 01 01 07"), s.earbuds.requests.filter { it.startsWith("01 25") })
        s.gesture("tripleTap").let {
            assertPicker("triple tap left", 7, listOf(2, 7, -1), it.left)
            assertPicker("triple tap right", 2, listOf(2, 7, -1), it.right)
        }
        assertEquals(7, s.earbuds.tripleTapLeft)
    }
}
