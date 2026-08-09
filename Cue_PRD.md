# Cue

**Conversational context for blind and low vision users, on HeyCyan CY-01 glasses.**

PRD v2, 2026-08-09. Author: Amogh Shastry. Status: hackathon build spec.

Grounded against `GLASSES_CAPABILITY_BRIEF.md`, `CLAUDE.md`, and the vendor Android SDK guide. Where those three disagree with public listings, they win.

---

## 0. Assumptions

You did not pick these. Each is cheap to change, but changing it changes the build plan.

| Assumption | Chosen | Change it if |
|---|---|---|
| Platform | **Android**, Java 17+, Kotlin | Not negotiable in practice. iOS is a CI-validated simulator host only and `QCSDK.framework` has never run on hardware. |
| Hardware | **CY-01** (unit on hand: `CY 01_994B`) | Locked. |
| Codebase | Fork `android/CyanBridge` | Locked. See Section 12 for what that costs you. |
| Build window | **24 to 36 hours** | Under 24: ship P0-1 through P0-3 and nothing else. |
| Hero demo | **Speaker identification plus presence** | See Section 14. |
| AI provider | **Claude direct** (Sonnet 5 for context, Haiku 4.5 for the fast path) | The fork currently routes through `cyanbridge.vercel.app` to OpenRouter with the vendor's own subscription billing attached. Rip that out at Hour 1. See Section 11. |

---

## 1. Problem

A sighted person in a conversation receives a continuous, free, unconscious stream of information: who is in the room, who just walked in, who is speaking, whether the last thing they said landed, whether the person they are talking to is still there.

A blind person receives almost none of it. The consequences are not abstract:

- Talking to someone who quietly walked away. Universally reported, and humiliating.
- Not knowing who is in a meeting, so you cannot address anyone by name.
- Missing the raised hand, the nod, the confused frown, the person waiting for you to finish.
- Missing every deictic reference. "This one." "Over there." "That number right here." All of it is noise.
- Not recognizing that the person greeting you warmly is someone you have met three times.

This is not a perception problem. It is a **social participation** problem. The existing assistive market solves perception: OCR, object naming, scene description. Seeing AI, Envision, OrCam, and Be My AI are all fundamentally "describe the world to me on request."

None of them are built for the thing that happens when you are in a room with other humans and the clock is running.

## 2. The insight

**Scene description is a query. Conversation is a stream.**

Every existing tool assumes you have time to stop, ask a question, and listen to a paragraph. A conversation gives you neither the time nor the silence. A tool that speaks a full sentence into your ear while someone is talking to you has made your life worse, not better.

So the product is defined less by what it knows than by **what it refuses to say**. Cue's core design problem is not perception. It is **interruption budget**.

> Cue is a low bandwidth, high frequency channel of social telemetry, delivered in the gaps of a conversation, mostly without words.

**The competitive fact to confront up front.** This hardware already ships an AI photo button: press it, ask a question about what you see, hear the answer. `GLASSES_CAPABILITY_BRIEF.md` lists it as Confirmed and working. So **scene description is the baseline this device already offers.** If your demo is "point at thing, hear description," you have rebuilt a button that exists. Everything in this section is what separates Cue from its own firmware. Say it to judges before they find it themselves.

## 3. Users

**Primary persona.** Blind or low vision adult, screen reader fluent, professionally active, attends meetings and social events. Already at expert speed with TTS (they listen at 300 to 500 words per minute, far faster than you will design for). Owns assistive tools and has abandoned most of them because they were slow, conspicuous, or wrong.

Note carefully: **your user is a TTS power user and you are not.** Do not tune voice speed to what sounds comfortable to a sighted judge. Make it configurable and default it fast.

**Jobs to be done.**

1. When I enter a room, tell me who is here so I can greet them by name.
2. While someone is talking, tell me who it is without making me ask.
3. Tell me when the social situation changes: someone arrived, someone left, someone is waiting on me.
4. When someone references something visual, let me ask what it is and get an answer fast.
5. Do all of this without announcing to the room that I am using a device.

That last one is a requirement. Conspicuousness is the top reason assistive wearables get abandoned. The glasses look like sunglasses. Keep it that way.

## 4. Non-goals

Say these out loud to the team, because each will try to sneak back in at hour 20.

- **Not a navigation aid.** No obstacle detection. Different safety bar, different liability, and a cane already wins.
- **Not an OCR tool.** Reading documents is solved and is a demo cliche.
- **Not always-narrating.** If it describes the room continuously, it has failed.
- **Not a meeting recorder.** The fork ships meeting capture and summarization. Ignore it. After-the-fact summaries are a different product.
- **Not face recognition of strangers.** See Section 10.
- **No media sync during a session.** See 7.5. This is an architectural commitment, not a preference.
- **Nothing shown on the glasses.** There is no display. Every output is spoken. Any feature phrased as "show the user X" is invalid by construction.

---

## 5. Interaction model

Get this right and mediocre perception still feels magical. Get it wrong and perfect perception is unusable.

### 5.1 Three output tiers

Ranked by interruption cost. Cue always uses the cheapest tier that carries the information. All output is **spoken or sounded**, because there is no display.

**Tier 0: Earcons (non-speech audio).** Under 200ms, no words, no language processing. Carries the high frequency events.

| Event | Earcon | Rationale |
|---|---|---|
| New person entered | Two ascending notes | "Something got added" |
| Person left | Two descending notes | Mirror of the above |
| Someone is addressing you directly | Single soft chime | Directional metaphor |
| Someone is waiting for you to respond | Slow double pulse | Ambiguity is the point, it means "your turn" |
| Cue is working on your request | Rising tick | Covers the photo path latency |
| Cue failed or is unsure | Low muted thud | Never say "I'm sorry, I didn't catch that" |
| **Glasses busy, command rejected** | Short flat buzz | See Section 6.5. Distinct from failure. |
| **Cue lost the glasses** | Three descending notes | The user must know the system has gone blind. Silence is indistinguishable from an empty room, and that ambiguity is dangerous. |

Earcons are learned in about ten minutes and cost near zero attention afterward. This is how screen readers already work and your user is fluent in the paradigm. Lean on it hard.

**Tier 1: Whispers.** One to four words, spoken at 1.5x, only in a detected speech gap. "Sarah." "Priya, on your right." "He left." A whisper is never a sentence. If it needs a verb, it belongs in Tier 2.

**Tier 2: Briefings.** A full spoken response, only ever user-initiated, or on session start.

### 5.2 The gap detector is the most important component you will build

Nothing in Tier 1 ever plays while a human is speaking. Ever. Build the voice activity detector first and be conservative: require 400ms of silence before a whisper, and abort mid-whisper if speech resumes.

If you build only one thing well, build this. A system that speaks over people is worse than no system, and judges will feel it instantly even if they cannot articulate why.

### 5.3 Input: the buttons the glasses already report

The brief is explicit that **buttons are fixed in firmware and cannot be remapped**. But it also says the glasses already report three button events to the phone, and that the existing app **receives some of them and does nothing**. You are not remapping firmware. You are interpreting events that are already arriving and currently being thrown away.

From `GLASSES_CAPABILITY_BRIEF.md` §1 and §5:

| Signal | Status in the fork | Cue uses it for |
|---|---|---|
| **AI / photo button** | Confirmed, wired to the AI photo flow | "What am I looking at" (P0-5) |
| **Pause button pressed** | **Received and ignored** | "Who's here" briefing (P0-4), and interrupt any speech in progress |
| **Volume changed on glasses** | **Received and ignored** | Repeat last (P0-6), plus adapting speech rate |
| Microphone button | Confirmed | Leave it to the vendor assistant. Do not fight it. |

The brief calls the ignored signals "cheap wins, unusually good value in a v1 scope." That is exactly right, and it gives Cue **silent, tactile, eyes-free triggers on the temple with no phone in hand and no wake word.**

Interrupt-on-pause is worth calling out separately. A blind user who has heard enough of a briefing has no way to stop it other than waiting. Wiring the pause button to cut speech immediately is small work and it is the difference between a tool that respects the user's time and one that lectures them.

**What I got wrong in v1 and why it matters.** I previously proposed inferring temple gestures from AVRCP media button events, reasoning from the Jieli audio SoC. That inference may still hold, but it is unnecessary: the SDK documents these events directly and the app already receives them. **Prefer the documented SDK events.** Treat AVRCP as a fallback only if a spike shows the SDK events are unreliable.

**Fallbacks, in order.** Phone hardware volume key (zero latency, fully reliable, good stage insurance). Then screen gestures, which require taking the phone out, but build a basic version because the app must work when the glasses are dead.

**Do not use a wake word.** "Hey, Cyan" invokes the vendor assistant, and AI conversation is a device mode (see 6.5) that will collide with everything Cue does.

### 5.4 Session lifecycle

Bind sessions to connection state and wear detection so there is no start button at all. Put the glasses on, Cue starts and briefs the room. Take them off, the session ends and the roster clears, which is also the privacy story in Section 10 expressed as a physical act rather than a settings toggle.

Zero-UI session control on a device for blind users is correct, and it demos well: the audience watches someone put on sunglasses and hear the room.

---

## 6. Scope

Effort bands follow `GLASSES_CAPABILITY_BRIEF.md` §8. Every feature states its internet dependency and its device-busy behavior, per §10.

### P0, the demo (must ship)

**P0-1. Live speaker attribution.** Cue names who is speaking, in the gaps, unprompted.
- Path: live BT headset mic (Path A), streaming STT with diarization, on the phone.
- Internet: **required.** Degrades to earcon-only if offline.
- Device busy: unaffected. Path A is the headset profile, not a glasses mode.
- Effort: **Medium.** New spoken interaction flow on existing foundations.
- Accept: with three enrolled speakers, correct name whispered within 800ms of turn start, at least 85 percent of turns.

**P0-2. Passive roll call enrollment.** The social ritual of introducing yourself is the enrollment flow. No setup screen, no training step, **no trigger at all.**
- Cue watches the transcript for self-introductions and binds diarization labels to names as they appear. Someone says "hi, I'm Sarah" and Sarah is in the roster.
- Internet: required (rides on P0-1).
- Device busy: unaffected.
- Effort: **Low.** One Haiku call over a transcript window.
- Accept: three people enrolled in under 30 seconds of natural conversation, zero taps and zero commands.

Making this passive rather than a command is a real upgrade: enrollment then happens in conversations the user did not plan for, which is most of them.

**P0-3. Presence change alerts.** New voice detected, or a known voice silent past threshold, fires the Tier 0 earcon plus optional Tier 1 name.
- Internet: required.
- Device busy: unaffected.
- Effort: **Low** once P0-1 exists.
- Accept: new speaker triggers the enter earcon within 2s of first utterance.

**P0-4. "Who's here." Pause button.** Tier 2 briefing on demand, no voice, no phone.
- Internet: not required. Answers from in-memory roster.
- Device busy: unaffected.
- Effort: **Low.** Wire an already-received signal.
- Accept: spoken answer begins within 1.5s of the press.

**P0-5. "What am I looking at." AI / photo button.** The one visual feature.
- Path: the **existing AI photo flow**, which captures and sends a thumbnail to the phone over BLE. **Not a media sync.** See 7.4.
- Cue's change: inject the last 30 seconds of transcript plus the roster into the prompt.
- Internet: **required.**
- Device busy: photo is a device mode. If the glasses are busy, play the busy earcon and do not queue. See 6.5.
- Effort: **Low.** The plumbing exists; you are changing a prompt and a context payload.
- Accept: answer begins within the measured thumbnail latency plus 2s. Transcript context is mandatory. "He is holding a laptop" is a failure. "The chart on his screen shows Q3 revenue down about 12 percent" is a pass.

**P0-6. Repeat last. Volume button.** Replays the last whisper or briefing from cache.
- Internet: not required.
- Device busy: unaffected.
- Effort: **Low.**
- Accept: replays instantly, never regenerates, works after any output tier.

Repeat-last is not a nice to have. Your user is in a live conversation with their attention on a human, they will miss whispers constantly, and audio has no scrollback. Every screen reader has this command for exactly this reason. Blind testers will rank it above anything in P1.

### P1 (if time survives)

- **P1-1. Reaction readout.** After a long user turn, one earcon summarizing room response. High wow, high risk, needs a photo so it inherits every constraint in 6.5.
- **P1-2. Spatial placement.** "On your left." Needs a photo plus face position mapping. See 7.6.
- **P1-3. Storage-full warning.** The glasses already report it and the app ignores it. Trivial, and it prevents a silent failure mid-session.
- **P1-4. Re-recognition of consented contacts.** Strictly opt in, strictly local. See Section 10.

### P2 (roadmap slide only, do not build)

Braille display output. Calendar and CRM prefetch so Cue knows who is expected. Group dynamics over time. Translation, which the hardware already does.

---

## 7. Architecture

### 7.1 The device

**HeyCyan CY-01**, unit on hand `CY 01_994B`. A camera, microphone, speaker, and onboard storage worn as glasses, talking to an Android phone over Bluetooth.

**There is no display.** Every response is spoken. This is absolute.

Vendor control map, from the manual (cross-check against the SDK events in 5.3, which are what actually reaches your code):

| Control | Action |
|---|---|
| Front button, long press 2s / 5s | Power on / off |
| Front button, short press | Take photo. Answers or ends an active call. |
| Rear button, short press | Vendor voice assistant |
| Rear button, double press | Vendor AI image recognition |
| Rear button, press and hold | Start audio recording to glasses storage |
| Touchpad, double tap / long press / triple tap | Play-pause / next / previous |
| Touchpad, swipe | Volume |

**Power behavior that will bite you:** below 15 percent battery high-power recording is disabled, below 10 percent all multimedia is suspended, and **the glasses power off 3 minutes after Bluetooth disconnection.** That last one gives your reconnect watchdog a hard deadline.

### 7.2 The two audio paths, which is the thing to understand

From the brief, and it is called out there as the single most misunderstood part of the platform:

**Path A, live microphone. Confirmed.** The glasses pair as a normal Bluetooth headset. The phone hears the mic live. Real-time transcription works here. **This is where all of Cue's real-time behavior lives.**

**Path B, on-glasses recording. Confirmed but not live.** The glasses record to internal storage. Those files are pulled later in a batch transfer that hijacks the phone's Wi-Fi.

So live conversation processing is feasible. Real-time processing of an on-glasses recording is not. **Cue uses Path A exclusively and never touches Path B during a session.**

### 7.3 Four channels

```
  ┌──────────────────────────────────────────────────────────┐
  │  CY-01 GLASSES  (no display, one mode at a time)         │
  │                                                           │
  │  [dual ENC mics] ──── A: BT headset, LIVE ──────┐        │
  │  [open-ear spkr] ◄─── D: BT audio out ──────────┤        │
  │  [8MP camera]    ──── C: AI photo, thumb / BLE ─┤        │
  │  [buttons]       ──── B: BLE notify events ─────┤        │
  └─────────────────────────────────────────────────┼────────┘
                                                     │
  ┌──────────────────────────────────────────────────▼───────┐
  │  ANDROID PHONE  (all intelligence lives here)            │
  │                                                           │
  │   A. mic in ──► VAD ──► streaming STT + diarization       │
  │                            │                              │
  │                            ▼                              │
  │                     CONTEXT ENGINE (rolling state)        │
  │                     roster · 60s transcript · last photo  │
  │                            │                              │
  │              ┌─────────────┼─────────────┐                │
  │              ▼             ▼             ▼                │
  │        fast path      slow path     photo path            │
  │        (local)        (Haiku)       (Sonnet + img)        │
  │              └─────────────┼─────────────┘                │
  │                            ▼                              │
  │                   OUTPUT ARBITER ◄── gap detector         │
  │                            │                              │
  │   D. TTS + earcon mixer ◄──┘                              │
  └───────────────────────────────────────────────────────────┘
```

**Channel A, audio in.** BT headset profile. The dual ENC mics sit at head height and point where the user's face points, which is exactly right for conversation. Fallback is the phone mic if HFP narrowband degrades diarization (Spike B).

**Channel B, button events.** BLE notify, via the SDK. See 5.3.

**Channel C, photos.** The existing AI photo flow. See 7.4.

**Channel D, audio out.** Standard Bluetooth to the open-ear speakers. Open-ear matters enormously: it does not occlude the ear canal, so the user still hears the actual conversation. Bone conduction or in-ear would be disqualifying. This is the best hardware fit in the project and it is worth saying to judges.

### 7.4 The photo path, and the correction that matters most

**I got this wrong in v1 and the internal docs are unambiguous.**

I told you that Wi-Fi Direct plus per-socket binding would let media transfer and cloud calls coexist, so the phone would keep cellular. That is false here. `CLAUDE.md` instructs the opposite: **bind the process** to the P2P network via `ConnectivityManager.bindProcessToNetwork()` before HTTP, or sockets route over the wrong default network on Samsung and other multi-network devices. And the brief §4.2 states plainly that during transfer the phone is **off the normal internet** and cloud AI calls will fail.

So the rule is: **no cloud AI during a media sync.** For a product whose every answer is a cloud call, a media sync inside a session is fatal.

**The way out is that Cue never needs one.** The brief lists this as Confirmed and working:

> AI photo, ask a question about what you see. Press button, photo is captured, **thumbnail sent to phone**, question answered aloud.

The thumbnail arrives over BLE. No transfer mode, no Wi-Fi Direct, no internet loss. That is the entire P0-5 path, it is already built, and Cue's only change is enriching the prompt.

**Design commitment: Cue performs no media sync during a session.** Full resolution media, if the product ever needs it, syncs after the glasses come off. This deletes the single worst constraint on the platform, and it is the most important architectural decision in this document.

**The open question it leaves.** The brief §7.1 notes the only documented quality control is a coarse 0 to 6 thumbnail setting, and full-resolution photo options are undocumented. **Thumbnail quality at the top setting is therefore an unmeasured input to P0-5 accuracy.** Measure it at Hour 0 (Spike A) before committing to the feature. If the thumbnail is too coarse for Claude to read a printed chart, P0-5 drops to describing people and gross objects, which is still demoable, and you say so honestly.

### 7.5 One mode at a time

The brief asks for this as an explicit PRD section, and it is right: it is the number one source of features that look fine on paper and cannot be built.

The glasses are always in exactly one mode: photo, video, audio recording, media transfer, firmware update, or AI conversation. **A command sent while busy is rejected**, and the SDK reports which mode it is stuck in.

For Cue:

- Path A live mic is the headset profile, not a glasses mode, so ordinary listening never blocks. **Confirm this in Spike C**, because if taking a photo interrupts the mic stream, Cue goes deaf for the duration and needs defined behavior.
- P0-5 is the only feature that claims a mode. Everything else is phone-side.
- **Every rejection gets the busy earcon and is dropped, never queued.** A queued action that fires 8 seconds later, into a conversation that has moved on, is worse than no action. This is a real design decision, not a shortcut.

Implementation: `glasses/GlassesSessionCoordinator.kt` enforces single access with leases (`MEDIA_SYNC`, `LIVE_PREVIEW`, `OTA`, `WIFI_ADB_DEBUG`, `META_CAMERA`) plus short-lived permits for one-shot commands. **Any command Cue sends must acquire a lease or permit or it will silently clobber an in-flight operation.** Read that file first.

### 7.6 Why P0 presence is audio only

"Sarah is on your left" needs vision. The mics are dual but you get mono, so there is no direction of arrival to extract. Rather than fake it:

- **P0 says "Sarah joined."** True, useful, honest.
- **P1 adds position** via a photo on the enter event, inheriting every constraint in 7.5.

Do not claim spatial awareness until P1 works. A judge who asks "how do you know she's on the left" and gets a hand-wave has found your weak point.

### 7.7 Speaker identification, the hackathon shortcut

Do not train voice embeddings. You do not have time and you do not need to.

1. Streaming STT with diarization (Deepgram or AssemblyAI, both give `speaker_0`, `speaker_1` labels at roughly 300ms).
2. People say their names out loud when introducing themselves, because that is what humans do.
3. One Haiku call: *"map each speaker label to a name based on this introduction transcript."*
4. Cache for the session.

That is the whole feature. It is a prompt, not a model. It also fails gracefully: an unmapped speaker becomes "someone new," which is still useful.

**Insurance:** diarization degrades badly with overlapping speech and in noisy rooms, and hackathon venues are noisy rooms. Pre-record a clean fallback session behind a mode flag, and rehearse both.

### 7.8 Latency budget

Every number is the point at which the feature stops feeling like perception and starts feeling like a computer.

| Path | Budget | How |
|---|---|---|
| Turn start to name whisper | **800ms** | Local diarization label to cached name. No LLM call. |
| Button press to briefing start | **1.5s** | Haiku, streamed, state already in memory |
| Button press to photo answer start | **measure, then budget** | Thumbnail over BLE is unmeasured. Spike A. Cover it with the working earcon. |
| Event to earcon | **200ms** | Preloaded PCM, no synthesis |

If the fast path ever needs a network call, the design is wrong.

---

## 8. The context engine

One rolling state object. This is what makes Cue answer questions rather than describe pixels.

```kotlin
data class ConversationContext(
    val sessionStart: Instant,
    val roster: List<Person>,           // name, speakerLabel, firstHeard, lastHeard, isPresent
    val turns: RingBuffer<Turn>,        // speaker, text, startMs, endMs. Keep 60s.
    val lastPhoto: PhotoContext?,       // thumbnail bytes, capturedAt, caption
    val pendingQuestion: Boolean,
    val userLastSpokeMs: Long
)
```

**Prompting principle.** Never send a photo alone. Always send photo plus last 30 seconds of transcript plus roster:

- Photo alone: "A man is holding a piece of paper."
- Photo plus context: "Grant is holding up the invoice he just mentioned. The total reads 4,200 dollars."

The second is the product. The first is the button the firmware already has.

**System prompt constraints, enforce hard:**

- Under 15 words unless explicitly asked to elaborate.
- Never describe anything the user did not ask about.
- Never open with "I see" or "The image shows."
- If uncertain, say the short uncertain thing. Never hedge across two sentences.
- Names, not descriptions, for anyone on the roster.

---

## 9. App accessibility, which is not optional

The companion app is used by a blind person. Most hackathon projects for blind users ship an app the target user cannot operate. Do not be that project.

- Every control has a `contentDescription`, including icon buttons.
- Full TalkBack traversal, **tested with the screen off.**
- Touch targets 48dp minimum.
- Primary actions within two swipes of launch.
- No state communicated by color alone.
- No timed dialogs that vanish.
- The app must work when the glasses are disconnected, falling back to phone mic and speaker.

Budget 90 minutes and do it before demo polish, not after. If a judge picks up the phone and turns on TalkBack, this becomes the whole story.

---

## 10. Privacy, consent, and the line you do not cross

**What Cue does.** Rolling 60 second in-memory audio buffer, discarded continuously. No audio persisted to disk by default. Photo sent to the model only on an explicit button press, never on a timer. Voice-to-name mappings held for the session only, cleared when the glasses come off.

**What Cue does not do.** No face recognition against any stored database. No biometric identification of anyone who has not opted in. No cloud storage of images or audio. No continuous recording.

The fork already defaults to storing no transcripts and redacting names in exports. Keep both.

**Re-recognition.** Users want "who is this person I have met before," and that is legitimate. But storing face embeddings of bystanders is biometric processing under GDPR Article 9 and Illinois BIPA, and consent belongs to the person recognized, not your user. If you build P1-4: on-device only, explicit self-enrollment, deletable, never a background scan.

**Two-party consent.** Recording laws in several US states and most of the EU require all-party consent. A rolling in-memory buffer with no persistence is a genuinely different legal posture from a recorder. Worth one slide.

**The asymmetry worth naming out loud:** a sighted person walks into a room and identifies everyone instantly, and nobody calls it surveillance. Cue restores parity, it does not create a new capability. That is honest, and it is your best answer in Q and A.

---

## 11. Two decisions to make at Hour 1

**AI provider.** The fork hardcodes `DEFAULT_PUBLIC_RELAY_URL` to `https://cyanbridge.vercel.app` in `AiProviderPrefs.kt`, which handles the vendor's Asaas/Paddle subscriptions and proxies all AI calls to OpenRouter. **You are shipping someone else's billing if you leave it.** Replace with direct Claude calls at Hour 1. It is a one-file change and it is not optional.

**Privacy posture.** Stated in Section 10. Put it in the app's onboarding, spoken, not just in the PRD.

---

## 12. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| `MainActivity.kt` is **10,094 lines** and holds BLE, Wi-Fi P2P, media download, AI routing, TTS, and dashboard actions | **High** | This is the biggest obstacle to any customization. Add Cue as new files and touch it as little as possible. Every hour budgeted for a change in there, double it. |
| Diarization collapses in a loud venue | **High** | Pre-recorded fallback behind a mode flag, rehearse both, demo in a quieter corner |
| Team builds the meeting summarizer because the fork makes it easy | **High** | It is in Non-goals for a reason. Watch for this at hour 20. |
| Thumbnail quality too coarse for P0-5 to read anything | High until measured | Spike A at Hour 0. If coarse, rescope P0-5 to people and gross objects and say so. |
| A Cue command clobbers an in-flight operation | High | Acquire a lease or permit from `GlassesSessionCoordinator` for every command. No exceptions. |
| Taking a photo interrupts the live mic stream | Medium, unmeasured | Spike C. If it does, define the deaf window and cover it with the working earcon. |
| HFP mic too narrowband for reliable diarization | Medium | Spike B, fall back to phone mic and say so honestly |
| Untracked Compose files break the build with `NonExistentClass cannot be converted to Annotation` | Medium, and it will waste an hour | Run `git status --short` and look for `??` under `ui/components/`, `ui/glasses/`, `ui/onboarding/`, `ui/plugins/` before blaming protocol code |
| Battery 270mAh, multimedia suspended below 10 percent | Medium | **Charge to 100 percent before demoing.** Runs under 5 minutes. Show battery in a dev overlay. |
| Glasses power off 3 minutes after BLE disconnect | Medium | Reconnect watchdog with a hard 3 minute budget, plus the lost-glasses earcon |
| Vendor assistant competes for the mic | Medium | Never use a wake word. Keep the user's hand off the rear button. |
| Single pair of glasses bottlenecks the whole team's QA | Medium | Schedule hardware time explicitly. Everything phone-side develops without it. |
| Notify `0x09` code `255` mistaken for a failure | Low | It is noise. The official app sees it constantly and still completes transfers. |
| Vendor `.aar` is proprietary | Low for hackathon, blocking for production | Do not redistribute it. Note the licensing question on the roadmap slide. |

---

## 13. Build plan, 36 hours

**Hours 0 to 3, spikes.** Nobody writes product code until these are answered.

- **Spike A: thumbnail quality and latency.** Trigger the existing AI photo flow, capture the thumbnail at max quality, time it, and feed it to Claude with a printed chart in frame. This decides whether P0-5 exists in its current form.
- **Spike B: mic path.** HFP capture into streaming STT, eyeball diarization on three speakers. Decide glasses mic versus phone mic.
- **Spike C: mode collision.** Does an AI photo interrupt the live mic? Does the pause button event actually arrive? Watch `DeviceNotify`, which prints every decoded frame and is the fastest way to learn the protocol on real hardware.

```bash
adb logcat -s DataDownload DeviceNotify WifiP2pManagerSingleton WifiP2pBroadcastReceiver BleIpBridge LDHMethods
```

Also at Hour 1: rip out the Vercel relay and point AI at Claude directly.

**Hours 3 to 10, the spine.** VAD and gap detector. Streaming STT with diarization into the context engine. Passive name mapping. Earcon mixer. At the end of this block Cue names speakers out loud. That is P0-1 through P0-3.

**Hours 10 to 16, the arbiter.** Output tiering and interruption budget. This is where it stops being a demo and starts feeling alive. Tune against a real three-person conversation.

**Hours 16 to 21, buttons and the photo path.** Wire pause and volume events, then P0-4, P0-5, P0-6.

**Hours 21 to 25, accessibility pass.** Section 9, screen off, no exceptions.

**Hours 25 to 31, rehearsal.** End to end at least six times with real people. Every failure found here is one a judge does not find.

**Hours 31 to 36, buffer and pitch.** Slides, fallback path, Q and A prep on privacy.

Ship nothing new after hour 31.

## 14. Demo script

Three minutes. Two volunteers, A and B. Do not hand the glasses to a judge, they will not know the earcon vocabulary and it will read as broken.

1. **Setup, 20s.** "I'm going to have a conversation. I won't look at anything, and I won't touch my phone." Put the glasses on, session starts on its own. Phone face down, screen off, **and it stays there.**
2. **Roll call, 25s.** A and B introduce themselves to each other, not to the device. No command, no tap. The roster builds itself out of an ordinary human ritual. Point out that nothing was triggered.
3. **Conversation, 60s.** A and B talk. Each speaker change gets a whisper with the name. Mirror the glasses audio through a laptop speaker so the room hears what the user hears. **This is where the demo lands.**
4. **Entrance, 20s.** A third person walks in and speaks: earcon, then name. A silently leaves: earcon. **Press pause.** Correct roster, spoken.
5. **Visual question, 30s.** B says "what do you think of this number here?" holding a printed chart. **Press the AI button.** The answer references both the image and what B just said.
6. **Close, 25s.** Zero-interruptions metric. Privacy posture, demonstrated by taking the glasses off and having the roster clear. One line of roadmap.

**What sells it:** the phone is face down and untouched for three straight minutes, and every interaction happens on the temple of a pair of sunglasses. That is the argument, and it is visual even though the product is not.

## 15. Metrics

| Metric | Target |
|---|---|
| Speaker attribution accuracy, 3 speakers | ≥ 85 percent of turns |
| Whisper interruptions (speaking over a human) | **0** in a 3 minute session |
| Turn start to name whisper, p50 | ≤ 800ms |
| Roll call to full roster | ≤ 30s, zero taps |
| Phone interactions during the demo | **0** |
| TalkBack-only task completion, screen off | 100 percent of primary flows |

The zero-interruptions metric is the one for the slide. It is counterintuitive, it shows you understood the actual problem, and it is measurable live.

## 16. Open questions

Inherited from `GLASSES_CAPABILITY_BRIEF.md` §7 and narrowed to what Cue actually depends on.

1. **Thumbnail quality at max setting.** Blocks P0-5 scope. Spike A. The only documented control is a coarse 0 to 6 setting and full-resolution options are undocumented.
2. **Thumbnail latency over BLE.** Sets the P0-5 budget in 7.8. Spike A.
3. **Does a photo interrupt the live mic?** Defines the deaf window in 7.5. Spike C.
4. **Do pause and volume events actually arrive?** Blocks P0-4 and P0-6, both of which have a volume-key fallback. Spike C.
5. **Can volume be set by the app, or only read?** Only reading is documented. Affects whether Cue can duck its own output.

Not blocking for Cue, but listed in the brief and worth knowing: glasses storage capacity, battery life under continuous use, media sync speed, recording length limits, and firmware update availability (the vendor server currently reports no update for this hardware, so firmware fixes cannot be assumed).

---

## Appendix: name

**Cue** carries both meanings at once: the social cue the user is missing, and the audio cue that delivers it. Alternatives if it collides: **Aside**, **Peripheral**, **Roomtone**.
