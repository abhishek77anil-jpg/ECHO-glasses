package com.fersaiyan.cyanbridge.cue.assistant

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityManager
import android.content.Context
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fersaiyan.cyanbridge.cue.ConversationContext
import com.fersaiyan.cyanbridge.cue.output.CueSpeaker
import com.fersaiyan.cyanbridge.cue.output.TtsCueSpeaker
import java.util.Locale

/**
 * The screen a blind user actually operates.
 *
 * Designed against PRD §9, where the requirement is not "has accessibility support" but
 * "works with the screen off". The decisions that follow from that:
 *
 * - **The whole screen is the button.** Not a 48dp target in a layout that has to be hunted
 *   for by swiping — the entire surface accepts the tap, so the hand cannot miss.
 * - **Everything is spoken, not just labelled.** TalkBack narrates what is focused; Cue also
 *   speaks state changes outright, because the user's attention is on a human conversation
 *   and not on exploring a screen.
 * - **State is announced through a live region**, so a change reaches TalkBack without the
 *   user having to go looking for it.
 * - **Nothing is communicated by colour**, and the visible text exists for a sighted helper
 *   sitting beside the user, not as the primary channel.
 *
 * The conversational half of Cue lives here. The ambient half — whispers, earcons, presence —
 * runs elsewhere and stays deliberately terse; this screen is where a direct question earns
 * a full answer.
 */
class CueAssistantActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private lateinit var speaker: CueSpeaker

    private val assistant = CueAssistant()

    private var spokenState by mutableStateOf("Starting up")
    private var lastQuestion by mutableStateOf("")
    private var lastAnswer by mutableStateOf("")
    private var listening by mutableStateOf(false)

    private var voiceInput: AssistantVoiceInput? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)
        speaker = TtsCueSpeaker(engineProvider = { tts })

        setContent {
            MaterialTheme {
                AssistantScreen()
            }
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale.getDefault()
            // PRD §3: the user listens far faster than a sighted designer finds comfortable.
            tts?.setSpeechRate(DEFAULT_RATE)
            (speaker as? TtsCueSpeaker)?.attach()
            announce(
                "Cue ready. Tap anywhere and ask me something. " +
                    "Say what can you do, to hear the list.",
            )
        }
    }

    override fun onDestroy() {
        voiceInput?.stop()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    /**
     * Says something and mirrors it on screen.
     *
     * Both channels every time: speech for the user, text for anyone helping them, and the
     * live region so TalkBack picks it up even when Cue's own TTS is muted or busy.
     */
    private fun announce(text: String) {
        spokenState = text
        speaker.speak(text, DEFAULT_RATE, "assistant-${System.currentTimeMillis()}") {}
        announceForAccessibility(text)
    }

    private fun announceForAccessibility(text: String) {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (manager?.isEnabled == true) {
            window?.decorView?.announceForAccessibility(text)
        }
    }

    /** Starts listening. Every outcome, including failure, is spoken rather than shown. */
    private fun startListening() {
        if (listening) {
            voiceInput?.stop()
            listening = false
            announce("Cancelled")
            return
        }

        val input = voiceInput ?: AssistantVoiceInput(
            context = this,
            onResult = { text -> handleUtterance(text) },
            onError = { message ->
                listening = false
                announce(message)
            },
        ).also { voiceInput = it }

        listening = true
        spokenState = "Listening"
        announceForAccessibility("Listening")
        if (!input.start()) {
            listening = false
            announce("I could not open the microphone. Check the microphone permission.")
        }
    }

    private fun handleUtterance(text: String) {
        listening = false
        lastQuestion = text

        val intent = CommandParser.parse(text)
        val reply = assistant.respond(intent, currentContext(), System.currentTimeMillis())

        when (val action = reply.action) {
            is AssistantReply.Action.StopSpeaking -> {
                speaker.stop()
                spokenState = "Stopped"
                return
            }

            is AssistantReply.Action.ChangeSpeech -> {
                applySpeechChange(action.change)
            }

            is AssistantReply.Action.AskModel -> {
                // Wired to the model in the next step; until then say so plainly rather
                // than leaving the user in silence wondering whether Cue heard them.
                announce("I can't answer that one yet.")
                return
            }

            is AssistantReply.Action.CapturePhoto -> {
                announce("I can't use the camera from this screen yet.")
                return
            }

            is AssistantReply.Action.RepeatLast -> {
                val previous = lastAnswer
                if (previous.isBlank()) {
                    announce("I haven't said anything yet.")
                } else {
                    announce(previous)
                }
                return
            }

            AssistantReply.Action.None -> Unit
        }

        reply.speech?.let {
            lastAnswer = it
            announce(it)
        }
    }

    private fun applySpeechChange(change: AssistantIntent.SpeechChange) {
        when (change) {
            AssistantIntent.SpeechChange.FASTER ->
                tts?.setSpeechRate(currentRate().coerceAtMost(MAX_RATE - RATE_STEP) + RATE_STEP)

            AssistantIntent.SpeechChange.SLOWER ->
                tts?.setSpeechRate(currentRate().coerceAtLeast(MIN_RATE + RATE_STEP) - RATE_STEP)

            else -> assistant.applySpeechChange(change)
        }
    }

    private var rate = DEFAULT_RATE
    private fun currentRate(): Float = rate

    /**
     * Placeholder context.
     *
     * The live roster and transcript arrive once the session controller and speech-to-text
     * are wired; the assistant already reads them through this one call, so nothing here
     * changes when they do.
     */
    private fun currentContext(): ConversationContext =
        ConversationContext(sessionStartMs = System.currentTimeMillis())

    @Composable
    private fun AssistantScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                // The entire screen is the tap target. A blind user should never have to
                // find a control, only the phone.
                .clickable(
                    onClickLabel = if (listening) "Cancel listening" else "Ask Cue a question",
                ) { startListening() }
                .semantics {
                    contentDescription = if (listening) {
                        "Listening. Double tap to cancel."
                    } else {
                        "Ask Cue. Double tap anywhere, then speak your question."
                    }
                    onClick(label = "Ask Cue") { startListening(); true }
                }
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (listening) "Listening…" else "Tap anywhere to ask",
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 34.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            Text(
                text = spokenState,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Assertive },
            )

            if (lastQuestion.isNotBlank()) {
                Text(
                    text = "You asked: $lastQuestion",
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // A separate large control, because some users prefer an explicit button and
            // TalkBack's explore-by-touch finds a labelled one faster than a screen region.
            Button(
                onClick = { startListening() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .semantics {
                        contentDescription =
                            if (listening) "Cancel listening" else "Ask Cue a question"
                    },
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(
                    text = if (listening) "Cancel" else "Ask Cue",
                    fontSize = 28.sp,
                )
            }

            Button(
                onClick = { announce(HELP_HINT) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .semantics { contentDescription = "Hear what you can ask" },
            ) {
                Text(text = "What can I ask?", fontSize = 22.sp)
            }
        }
    }

    private companion object {
        const val DEFAULT_RATE = 1.35f
        const val MIN_RATE = 0.6f
        const val MAX_RATE = 2.5f
        const val RATE_STEP = 0.15f
        const val HELP_HINT =
            "You can ask: who's here. Who's talking. What did they just say. " +
                "Say again, to repeat. Say stop any time to interrupt me. " +
                "You can also say speak faster, speak slower, or keep it short."
    }
}
