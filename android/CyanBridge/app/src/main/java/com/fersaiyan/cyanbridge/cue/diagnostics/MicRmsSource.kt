package com.fersaiyan.cyanbridge.cue.diagnostics

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Reads the live microphone and reports RMS levels, routed to the glasses where possible.
 *
 * This exists to answer Spike B (PRD §13): is the glasses' HFP microphone usable, and does
 * the noise floor in a real room leave enough headroom for the gap detector to find gaps?
 *
 * Uses [AudioRecord] rather than `SpeechRecognizer.onRmsChanged` because the latter reports
 * an uncalibrated, engine-defined value that stops entirely between recognition restarts.
 * Raw PCM gives a continuous dBFS figure that is directly comparable across devices and
 * rooms, which is what a spike needs to produce.
 */
class MicRmsSource(
    context: Context,
    private val onRms: (rmsDb: Float, nowMs: Long) -> Unit,
    private val onRoute: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null
    private var usingCommunicationDevice = false

    /** @return false when the mic could not be opened, e.g. permission missing. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true

        routeBluetoothMic()

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) {
            onError("Microphone unavailable at ${SAMPLE_RATE}Hz")
            return false
        }
        val bufferSize = minBuffer * 2

        val audioRecord = try {
            AudioRecord(
                // VOICE_COMMUNICATION follows the communication route, which is how the
                // glasses mic is reached once SCO is up.
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                bufferSize,
            )
        } catch (security: SecurityException) {
            onError("Microphone permission denied")
            return false
        } catch (error: Exception) {
            onError("Could not open microphone: ${error.message}")
            return false
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { audioRecord.release() }
            onError("Microphone failed to initialise")
            return false
        }

        record = audioRecord
        running = true
        runCatching { audioRecord.startRecording() }
            .onFailure {
                onError("Could not start recording: ${it.message}")
                stop()
                return false
            }

        thread = Thread({ readLoop(audioRecord, bufferSize) }, "cue-mic-rms").apply {
            isDaemon = true
            start()
        }
        return true
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        record?.let { rec ->
            runCatching { if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) rec.stop() }
            runCatching { rec.release() }
        }
        record = null
        clearBluetoothMicRoute()
    }

    private fun readLoop(audioRecord: AudioRecord, bufferSize: Int) {
        val buffer = ShortArray(bufferSize / 2)
        while (running && !Thread.currentThread().isInterrupted) {
            val read = try {
                audioRecord.read(buffer, 0, buffer.size)
            } catch (error: Exception) {
                if (running) onError("Microphone read failed: ${error.message}")
                break
            }
            if (read <= 0) continue

            var sumSquares = 0.0
            for (i in 0 until read) {
                val sample = buffer[i].toDouble()
                sumSquares += sample * sample
            }
            val rms = sqrt(sumSquares / read)

            // dBFS: 0 is full scale, quiet rooms land around -55 to -45. The gap detector
            // only cares about differences, so any monotonic scale works — this one is
            // reported because it is comparable between phones and rooms.
            val db = if (rms <= 1.0) MIN_DB else (20.0 * log10(rms / Short.MAX_VALUE)).toFloat()
            onRms(db.coerceAtLeast(MIN_DB), System.currentTimeMillis())
        }
    }

    /**
     * Points capture at the glasses.
     *
     * Mirrors the routing already used by the app's voice plugins. If no Bluetooth input is
     * available Android silently falls back to the phone mic, which is itself a useful Spike
     * B result — hence [onRoute] reporting which one was obtained.
     */
    private fun routeBluetoothMic() {
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val device = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (device != null && audioManager.setCommunicationDevice(device)) {
                    usingCommunicationDevice = true
                    onRoute("Bluetooth (type ${device.type})")
                    return
                }
            }
            @Suppress("DEPRECATION")
            run {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            }
            onRoute("Bluetooth SCO (legacy)")
        }.onFailure {
            Log.w(TAG, "Bluetooth mic route unavailable", it)
            onRoute("Phone microphone (Bluetooth unavailable)")
        }
    }

    private fun clearBluetoothMicRoute() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && usingCommunicationDevice) {
                audioManager.clearCommunicationDevice()
            }
            @Suppress("DEPRECATION")
            run {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
            audioManager.mode = AudioManager.MODE_NORMAL
        }
        usingCommunicationDevice = false
    }

    private companion object {
        const val TAG = "CueMicRms"
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val MIN_DB = -90f
    }
}
