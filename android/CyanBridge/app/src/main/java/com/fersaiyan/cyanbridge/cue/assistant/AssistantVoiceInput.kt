package com.fersaiyan.cyanbridge.cue.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * One-shot speech capture for a question to Cue.
 *
 * Deliberately push-to-talk rather than always-listening. Continuous recognition holds the
 * Bluetooth SCO route open, drains both the phone and the glasses, and — per the PRD — would
 * collide with the vendor's own assistant and with on-glasses recording. A single tap costs
 * the user nothing and keeps the microphone free the rest of the time.
 *
 * Prefers the glasses microphone so the user can ask without lifting the phone, and falls
 * back to the phone's own microphone rather than failing when no headset route exists.
 */
class AssistantVoiceInput(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val languageTag: String? = null,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var recognizer: SpeechRecognizer? = null
    private var usingCommunicationDevice = false
    private var active = false

    fun start(): Boolean {
        if (active) return true
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("Speech recognition isn't available on this phone.")
            return false
        }

        routeBluetoothMic()
        active = true

        handler.post {
            val speech = SpeechRecognizer.createSpeechRecognizer(appContext)
            recognizer = speech
            speech.setRecognitionListener(listener)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                // A question to an assistant is short. Ending promptly matters more than
                // catching a trailing word, because the user is waiting to hear back.
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    900L,
                )
                languageTag?.takeIf { it.isNotBlank() }?.let {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, it)
                }
            }
            runCatching { speech.startListening(intent) }
                .onFailure {
                    active = false
                    onError("I couldn't start listening.")
                }
        }
        return true
    }

    fun stop() {
        active = false
        handler.post {
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            recognizer = null
            clearBluetoothMicRoute()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) {
            active = false
            clearBluetoothMicRoute()
            // Spoken, human, and actionable. Never a raw error code — the user cannot see it
            // and a number tells them nothing about what to do next.
            onError(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    -> "I didn't catch that. Tap and try again."

                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "I need microphone permission to hear you."

                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    -> "I can't reach the network right now."

                    else -> "Something went wrong listening. Tap and try again."
                },
            )
        }

        override fun onResults(results: Bundle?) {
            active = false
            clearBluetoothMicRoute()
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            if (text.isNullOrBlank()) {
                onError("I didn't catch that. Tap and try again.")
            } else {
                onResult(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
    }

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
                    return
                }
            }
            @Suppress("DEPRECATION")
            run {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            }
        }.onFailure { Log.w(TAG, "Falling back to the phone microphone", it) }
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
        const val TAG = "CueVoiceInput"
    }
}
