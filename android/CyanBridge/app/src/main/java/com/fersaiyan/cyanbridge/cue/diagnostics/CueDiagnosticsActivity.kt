package com.fersaiyan.cyanbridge.cue.diagnostics

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.fersaiyan.cyanbridge.cue.audio.GapDetector
import com.fersaiyan.cyanbridge.cue.output.AndroidEarconPlayer
import com.fersaiyan.cyanbridge.cue.output.Earcon
import com.fersaiyan.cyanbridge.glasses.GlassesSessionCoordinator
import com.fersaiyan.cyanbridge.plugins.PluginVoicePermissions
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.LargeDataHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hardware diagnostics for Cue — the Hour 0-3 spikes from PRD §13, in an APK.
 *
 * Deliberately a separate launcher activity. `MainActivity` is 10,094 lines and is the
 * single biggest cost centre in this codebase, so this touches none of it: the diagnostics
 * build ships as its own icon alongside CyanBridge.
 *
 * What it answers:
 * - **Spike A** — how long a thumbnail actually takes to arrive over BLE.
 * - **Spike B** — whether the glasses microphone is reachable, and what the noise floor and
 *   speech headroom look like in a real room.
 * - **Spike C** — whether the pause and volume buttons reach the phone as notify frames.
 *   P0-4 and P0-6 are both built on the assumption that they do.
 *
 * Every result is on screen rather than only in logcat, because the person holding the
 * glasses is not necessarily the person holding a terminal.
 */
class CueDiagnosticsActivity : AppCompatActivity() {

    private val earconPlayer by lazy { AndroidEarconPlayer() }
    private val gapDetector = GapDetector()

    private var micSource: MicRmsSource? = null
    private var notifyLogger: NotifyFrameLogger? = null

    private var micRunning by mutableStateOf(false)
    private var micRoute by mutableStateOf("not started")
    private var lastRmsDb by mutableStateOf(0f)
    private var noiseFloorDb by mutableStateOf<Float?>(null)
    private var speaking by mutableStateOf(false)
    private var gapOpen by mutableStateOf(false)
    private var status by mutableStateOf("Ready")
    private var photoResult by mutableStateOf("not run")

    private val frames = mutableStateListOf<NotifyFrameLogger.Entry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notifyLogger = NotifyFrameLogger { entry ->
            runOnUiThread {
                frames.add(0, entry)
                while (frames.size > MAX_FRAMES) frames.removeAt(frames.lastIndex)
            }
        }.also { it.start() }

        startGapPump()

        setContent {
            CyanBridgeTheme {
                Scaffold { padding ->
                    DiagnosticsScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        micSource?.stop()
        notifyLogger?.stop()
        earconPlayer.release()
        super.onDestroy()
    }

    /** Mirrors the 100ms cadence the real engine uses, so the readout matches live behaviour. */
    private fun startGapPump() {
        lifecycleScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                speaking = gapDetector.isSpeaking
                gapOpen = gapDetector.isGapOpen(now)
                noiseFloorDb = gapDetector.noiseFloor
                delay(GAP_PUMP_INTERVAL_MS)
            }
        }
    }

    private fun toggleMic() {
        val active = micSource
        if (active != null) {
            active.stop()
            micSource = null
            micRunning = false
            micRoute = "stopped"
            gapDetector.reset()
            return
        }

        PluginVoicePermissions.ensure(this, onDenied = { status = "Microphone permission denied" }) {
            val source = MicRmsSource(
                context = this,
                onRms = { db, nowMs ->
                    lastRmsDb = db
                    gapDetector.onRms(db, nowMs)
                },
                onRoute = { route -> runOnUiThread { micRoute = route } },
                onError = { message -> runOnUiThread { status = message } },
            )
            micSource = source
            micRunning = source.start()
            if (!micRunning) {
                micSource = null
            } else {
                status = "Microphone running"
            }
        }
    }

    /**
     * Spike A: times the existing AI photo path end to end.
     *
     * Uses the same command sequence the app already ships rather than the vendor guide's
     * six-byte form, so this measures the path that actually works today instead of
     * introducing an untested variable into a latency measurement.
     */
    private fun runPhotoSpike() {
        if (!isGlassesConnected()) {
            photoResult = "glasses not connected"
            return
        }
        val permit = GlassesSessionCoordinator.tryAcquireBackgroundCommand()
        if (permit == null) {
            photoResult = "glasses busy — another workflow holds the SDK"
            earconPlayer.play(Earcon.DEVICE_BUSY)
            return
        }

        photoResult = "running..."
        earconPlayer.play(Earcon.WORKING)

        lifecycleScope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            var firstChunkAt = 0L
            var totalBytes = 0
            val completed = AtomicBoolean(false)
            val done = CompletableDeferred<Unit>()

            val callback: (Int, Boolean, ByteArray?) -> Unit = { _, isComplete, data ->
                if (data != null && data.isNotEmpty()) {
                    if (firstChunkAt == 0L) firstChunkAt = System.currentTimeMillis()
                    totalBytes += data.size
                }
                if (isComplete && completed.compareAndSet(false, true) && !done.isCompleted) {
                    done.complete(Unit)
                }
            }

            try {
                runCatching {
                    LargeDataHandler.getInstance()
                        .glassesControl(byteArrayOf(0x02, 0x01, 0x06, 0x02, 0x02)) { _, _ -> }
                }
                delay(THUMBNAIL_SETUP_DELAY_MS)
                runCatching {
                    LargeDataHandler.getInstance()
                        .glassesControl(byteArrayOf(0x02, 0x01, 0x01)) { _, _ -> }
                }
                delay(CAPTURE_SETTLE_MS)

                runCatching { LargeDataHandler.getInstance().getPictureThumbnails(callback) }
                    .onFailure { Log.w(TAG, "getPictureThumbnails failed", it) }

                val finished = withTimeoutOrNull(PHOTO_TIMEOUT_MS) { done.await() } != null
                val elapsed = System.currentTimeMillis() - startedAt
                val firstByteMs = if (firstChunkAt > 0) firstChunkAt - startedAt else -1

                withContext(Dispatchers.Main) {
                    photoResult = if (finished) {
                        "OK — total ${elapsed}ms, first bytes at ${firstByteMs}ms, $totalBytes bytes"
                    } else {
                        "TIMED OUT after ${elapsed}ms, $totalBytes bytes received"
                    }
                    earconPlayer.play(if (finished) Earcon.ADDRESSED_DIRECTLY else Earcon.FAILED)
                }
            } finally {
                GlassesSessionCoordinator.releaseBackgroundCommand(permit)
            }
        }
    }

    private fun isGlassesConnected(): Boolean =
        runCatching { BleOperateManager.getInstance().isConnected }.getOrDefault(false)

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun DiagnosticsScreen(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Cue Diagnostics", style = MaterialTheme.typography.headlineSmall)
            Text(status, style = MaterialTheme.typography.bodySmall)

            SectionCard("Connection") {
                val connected = isGlassesConnected()
                Text(if (connected) "Glasses connected" else "Glasses NOT connected")
                Text(
                    "Pair and connect in the main CyanBridge app first.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            SectionCard("Spike B — microphone and gaps") {
                Text("Route: $micRoute")
                Text("Level: ${"%.1f".format(lastRmsDb)} dBFS")
                Text("Noise floor: " + (noiseFloorDb?.let { "%.1f dBFS".format(it) } ?: "learning"))
                Text(if (speaking) "SPEAKING" else "silent")
                Text(if (gapOpen) "GAP OPEN — a whisper would play now" else "gap closed")
                Button(onClick = { toggleMic() }) {
                    Text(if (micRunning) "Stop microphone" else "Start microphone")
                }
                Text(
                    "Speak, then stop. The gap should open about 400ms after you stop talking.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            SectionCard("Earcons — do they reach the glasses?") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Earcon.entries.forEach { earcon ->
                        OutlinedButton(onClick = { earconPlayer.play(earcon) }) {
                            Text(earcon.name.replace('_', ' ').lowercase())
                        }
                    }
                }
                Text(
                    "Each should be audible through the open-ear speakers and tell apart from the others.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            SectionCard("Spike A — photo path timing") {
                Text(photoResult)
                Button(onClick = { runPhotoSpike() }) { Text("Capture and time a thumbnail") }
                Text(
                    "Point the glasses at a printed chart before pressing.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            SectionCard("Spike C — button events (${frames.size})") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { frames.clear() }) { Text("Clear") }
                }
                Text(
                    "Press the pause and volume controls on the glasses. Look for 0x0C and 0x12.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (frames.isEmpty()) {
                    Text("No frames yet.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(frames) { entry ->
                            Column {
                                Text(
                                    "${entry.clock}  0x%02X  ${entry.label}".format(entry.opcode),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    entry.payload,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SectionCard(title: String, content: @Composable () -> Unit) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                content()
            }
        }
    }

    private companion object {
        const val TAG = "CueDiagnostics"
        const val MAX_FRAMES = 200
        const val GAP_PUMP_INTERVAL_MS = 100L
        const val THUMBNAIL_SETUP_DELAY_MS = 250L
        const val CAPTURE_SETTLE_MS = 2_500L
        const val PHOTO_TIMEOUT_MS = 15_000L
    }
}
