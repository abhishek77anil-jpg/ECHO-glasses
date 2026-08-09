package com.fersaiyan.cyanbridge.cue.diagnostics

import android.util.Log
import com.oudmon.ble.base.communication.LargeDataHandler
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyListener
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyRsp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logs decoded glasses notify frames (Spike C, PRD §13).
 *
 * P0-4 and P0-6 are both built on the claim that the pause and volume buttons reach the
 * phone as notify events. The vendor guide documents them and the existing app receives them
 * and does nothing, but nobody has confirmed on this hardware that pressing the buttons
 * actually produces them. This answers that question before the features are built on top.
 *
 * Registers on its own listener slot so it observes without disturbing whatever the rest of
 * the app has registered. It never sends a command, so it cannot clobber an in-flight
 * operation.
 */
class NotifyFrameLogger(
    private val maxEntries: Int = 200,
    private val onFrame: (Entry) -> Unit,
) {
    data class Entry(
        val atMs: Long,
        val opcode: Int,
        val label: String,
        val payload: String,
    ) {
        val clock: String
            get() = TIME_FORMAT.format(Date(atMs))

        private companion object {
            val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        }
    }

    private val listener = object : GlassesDeviceNotifyListener() {
        override fun parseData(cmdType: Int, response: GlassesDeviceNotifyRsp) {
            runCatching { record(response) }
                .onFailure { Log.w(TAG, "Failed to decode notify frame", it) }
        }
    }

    private var registered = false

    fun start() {
        if (registered) return
        runCatching {
            LargeDataHandler.getInstance().addOutDeviceListener(LISTENER_SLOT, listener)
            registered = true
        }.onFailure { Log.w(TAG, "Could not register notify listener", it) }
    }

    fun stop() {
        if (!registered) return
        runCatching { LargeDataHandler.getInstance().removeOutDeviceListener(LISTENER_SLOT) }
            .onFailure { Log.w(TAG, "Could not remove notify listener", it) }
        registered = false
    }

    private fun record(response: GlassesDeviceNotifyRsp) {
        val data = response.loadData ?: return
        if (data.size <= OPCODE_INDEX) return

        val opcode = data[OPCODE_INDEX].toInt() and 0xFF
        val entry = Entry(
            atMs = System.currentTimeMillis(),
            opcode = opcode,
            label = labelFor(opcode, data),
            payload = data.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) },
        )
        Log.i(TAG, "0x%02X %s | %s".format(opcode, entry.label, entry.payload))
        onFrame(entry)
    }

    /**
     * Names the opcode using the vendor guide, plus the two undocumented frames the media
     * transfer depends on. See `android/docs/VENDOR_SDK_REFERENCE_EN.md`.
     */
    private fun labelFor(opcode: Int, data: ByteArray): String {
        fun byteAt(index: Int): Int? =
            if (data.size > index) data[index].toInt() and 0xFF else null

        return when (opcode) {
            0x02 -> "AI photo ready" + (byteAt(9)?.let { " (byte9=$it)" } ?: "")
            0x03 -> "Microphone button" + (byteAt(7)?.let { " (state=$it)" } ?: "")
            0x04 -> "OTA progress"
            0x05 -> "Battery ${byteAt(7)}%" + (if (byteAt(8) == 1) " charging" else "")
            0x08 -> "Wi-Fi IP reported"
            0x09 -> "P2P/Wi-Fi error ${byteAt(7)}"
            0x0C -> "PAUSE BUTTON" + (byteAt(7)?.let { " (state=$it)" } ?: "")
            0x0D -> "App unbind requested"
            0x0E -> "Glasses storage low"
            0x10 -> "Translation paused"
            0x12 -> "VOLUME CHANGED music=${byteAt(10)} call=${byteAt(14)} system=${byteAt(18)}"
            else -> "Unknown opcode"
        }
    }

    companion object {
        private const val TAG = "CueNotify"

        /**
         * Distinct from the slots already in use — 100 is MainActivity's, 2 is media and
         * live preview, and OTA holds its own.
         */
        private const val LISTENER_SLOT = 101
        private const val OPCODE_INDEX = 6
    }
}
