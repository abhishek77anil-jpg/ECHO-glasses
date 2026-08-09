package com.fersaiyan.cyanbridge.cue.output

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays Tier 0 earcons (PRD §5.1).
 *
 * Every earcon is rendered to PCM once, at construction, and cached. The budget is 200ms
 * from event to sound, and synthesizing on the hot path would spend most of it — so the
 * only work at play time is handing an existing byte array to an [AudioTrack].
 *
 * Synthesized rather than shipped as assets: eight short tone sequences cost a few hundred
 * lines of samples, and generating them keeps the earcon vocabulary editable in one enum
 * instead of scattered across binary files.
 */
class AndroidEarconPlayer(
    private val sampleRate: Int = SAMPLE_RATE,
    private val amplitude: Float = DEFAULT_AMPLITUDE,
) : EarconSink {

    private val cache = ConcurrentHashMap<Earcon, ByteArray>()

    /**
     * Single-threaded so earcons queue behind each other rather than overlapping into mush.
     * A rapid enter-then-leave still reads as two distinct events this way.
     */
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "cue-earcon").apply { isDaemon = true }
    }

    @Volatile
    private var released = false

    init {
        // Warm the cache off the main thread so the first earcon is as fast as the rest.
        executor.execute {
            Earcon.entries.forEach { earcon ->
                runCatching { cache[earcon] = render(earcon) }
                    .onFailure { Log.w(TAG, "Failed to pre-render $earcon", it) }
            }
        }
    }

    override fun play(earcon: Earcon) {
        if (released) return
        executor.execute {
            if (released) return@execute
            runCatching {
                val pcm = cache.getOrPut(earcon) { render(earcon) }
                writeAndBlock(pcm)
            }.onFailure { Log.w(TAG, "Failed to play $earcon", it) }
        }
    }

    fun release() {
        released = true
        executor.shutdown()
        cache.clear()
    }

    private fun writeAndBlock(pcm: ByteArray) {
        val track = buildTrack(pcm.size) ?: return
        try {
            track.play()
            var offset = 0
            while (offset < pcm.size) {
                val written = track.write(pcm, offset, pcm.size - offset)
                if (written <= 0) break
                offset += written
            }
            // Let the buffer drain before releasing, or the tail is clipped.
            val durationMs = (pcm.size / BYTES_PER_SAMPLE) * 1_000L / sampleRate
            Thread.sleep(min(durationMs + TAIL_DRAIN_MS, MAX_BLOCK_MS))
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    private fun buildTrack(byteCount: Int): AudioTrack? {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            Log.w(TAG, "AudioTrack reported no usable buffer size")
            return null
        }
        val bufferSize = maxOf(minBuffer, byteCount)

        val attributes = AudioAttributes.Builder()
            // Accessibility usage keeps earcons audible when media is ducked, which matters
            // because they are the channel carrying presence changes.
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM,
            )
        }
    }

    /** Renders a tone sequence to 16-bit mono PCM. */
    private fun render(earcon: Earcon): ByteArray {
        val totalSamples = earcon.tones.sumOf { samplesFor(it.durationMs) }
        val out = ByteArray(totalSamples * BYTES_PER_SAMPLE)
        var index = 0

        earcon.tones.forEach { tone ->
            val count = samplesFor(tone.durationMs)
            if (tone.isRest) {
                index += count * BYTES_PER_SAMPLE
                return@forEach
            }

            val attack = min(samplesFor(ENVELOPE_ATTACK_MS), count / 2)
            val release = min(samplesFor(ENVELOPE_RELEASE_MS), count / 2)

            for (i in 0 until count) {
                // A raw sine that starts and stops at full amplitude clicks audibly. The
                // envelope is what makes eight synthesized beeps sound deliberate.
                val envelope = when {
                    i < attack -> i.toFloat() / attack
                    i >= count - release -> (count - i).toFloat() / release
                    else -> 1f
                }
                val angle = 2.0 * PI * tone.frequencyHz * i / sampleRate
                val value = (sin(angle) * amplitude * envelope * Short.MAX_VALUE).toInt()
                val clamped = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                out[index++] = (clamped and 0xFF).toByte()
                out[index++] = ((clamped shr 8) and 0xFF).toByte()
            }
        }
        return out
    }

    private fun samplesFor(durationMs: Int): Int = durationMs * sampleRate / 1_000

    companion object {
        private const val TAG = "CueEarcon"
        private const val SAMPLE_RATE = 44_100
        private const val BYTES_PER_SAMPLE = 2
        private const val DEFAULT_AMPLITUDE = 0.35f
        private const val ENVELOPE_ATTACK_MS = 5
        private const val ENVELOPE_RELEASE_MS = 12
        private const val TAIL_DRAIN_MS = 60L
        private const val MAX_BLOCK_MS = 1_500L
    }
}
