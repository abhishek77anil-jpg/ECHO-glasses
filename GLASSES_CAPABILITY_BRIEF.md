# Smart Glasses — Capability Brief for PRD Authoring

**Purpose:** what the hardware and SDK can and cannot do, so the PRD specifies
things that are buildable. Written for a product author, not an engineer.

**Device:** HeyCyan-class glasses (unit on hand: `CY 01_994B`)
**Platform:** Android only
**Source:** vendor SDK `glasses_sdk_20250723_v01.aar` + the official vendor
developer guide + an existing Android app (CyanBridge) that already implements
most of the transfer stack.

Confidence labels used below:

- **Confirmed** — implemented and working in the existing app.
- **Documented** — the vendor SDK supports it; not yet wired up here.
- **Experimental** — code exists, not validated on hardware.
- **Not available** — do not specify this.

---

## 1. What the glasses physically are

A camera + microphone + speaker + onboard storage, worn as glasses, talking to
an Android phone over Bluetooth.

**There is no display.** These glasses cannot show text, images, notifications,
or any visual output. Every response to the user is **spoken aloud** through the
glasses' speaker. Any PRD feature phrased as "show the user X on the glasses"
has to be re-specified as "speak X to the user."

**On-glasses input is limited to a few hardware buttons and the microphone.**
The buttons are fixed by firmware; we cannot add new ones or remap them freely.
Known button events are: AI/photo button, microphone button, and a pause button.

---

## 2. Two separate audio paths — this distinction matters a lot

This is the single most misunderstood part of the platform, and it changes what
is possible in real time.

**Path A — live microphone (Confirmed).** The glasses pair as a normal Bluetooth
headset. The phone hears the glasses' microphone live, exactly like any BT
headset. Real-time voice input, live transcription, and voice assistants all
work through this path.

**Path B — on-glasses recording (Confirmed).** The glasses record to their own
internal storage. Those files are **not live**. They are pulled to the phone
later in a batch transfer that takes over the phone's Wi-Fi (see §4).

So: "live voice conversation with an AI" is feasible. "Real-time processing of
an on-glasses recording while it is being recorded" is not.

---

## 3. What works today

**Confirmed — already built and working:**

| Capability | Notes |
| --- | --- |
| Scan, pair, connect, auto-reconnect | |
| Battery level + charging state | Reported automatically |
| Take a photo | |
| Start / stop video recording | |
| Start / stop audio recording | To glasses storage |
| AI photo → ask a question about what you see | Press button, photo is captured, thumbnail sent to phone, question answered aloud |
| Count of unsynced media on glasses | Photos / videos / recordings, separately |
| Sync all media to the phone | Photos and videos to the gallery, audio to the music library |
| Read firmware and hardware versions | |
| Read current volume levels | Music, call, and system, separately |
| Live voice conversation with an AI | Via the headset mic path |
| Meeting capture, transcription, summarization, notes | Runs on the phone |
| On-device (offline) AI models, plus cloud AI | Both supported |

**Experimental — exists but unvalidated, do not build a launch feature on these:**
live camera preview, firmware updates (OTA), Wi-Fi debug mode.

---

## 4. The constraints the PRD must design around

### 4.1 The glasses do exactly one thing at a time

The device is always in exactly one mode: photo, video recording, audio
recording, media transfer, firmware update, or AI conversation. A command sent
while it is busy is **rejected**, and the SDK reports which mode it is stuck in.

Consequences for the PRD:

- You cannot record video and sync photos at the same time.
- You cannot take a photo mid-transfer.
- Any flow that assumes two glasses activities overlap will not work.
- Every feature needs defined behaviour for "device busy" — what does the user
  hear, and does the action queue or fail?

**This should be an explicit section in the PRD.** It is the number-one source
of features that look fine on paper and cannot be built.

### 4.2 Media transfer hijacks the phone's Wi-Fi

Pulling photos, videos, and recordings off the glasses works by having the phone
join a direct Wi-Fi network hosted by the glasses. While that is happening:

- The phone is **off the normal internet**. Cloud AI calls, uploads, and syncing
  will fail mid-transfer.
- Transfer is a batch operation with a real duration, proportional to how much
  media is waiting.

The PRD needs to say when sync happens (manual? on charge? on Wi-Fi?) and what
the user sees while it runs. Do not specify anything that needs the internet
during a sync.

### 4.3 Recorded audio is in a non-standard format

The glasses' audio recordings are not a normal playable file. The app repairs
them into a standard format on the way in. This mostly works. Budget for a
minority of recordings that fail to convert, and decide what the user sees when
one does.

### 4.4 Nothing can be tested without the physical glasses

There is no simulator. Every glasses feature requires the hardware and a real
Android phone. Plan QA time accordingly, and be aware that a single pair of
glasses is a testing bottleneck for the whole team.

### 4.5 Battery and heat

Continuous camera or recording use drains the glasses and warms them. If the PRD
proposes always-on capture, periodic capture, or long recording sessions, that
needs a battery budget and a stated maximum session length. We do not yet have
measured figures — see §7.

---

## 5. Cheap wins — already available, nobody has used them

The glasses already send these signals to the phone. The vendor documents them,
and the existing app receives them and does nothing. Wiring any of these up is
small work, so they are unusually good value in a v1 scope.

| Signal | What you could do with it |
| --- | --- |
| **Glasses storage almost full** | Warn the user, or auto-trigger a sync before recording fails |
| **User changed volume on the glasses** | Use the volume buttons as an extra input, or adapt spoken output |
| **Pause button pressed** | Interrupt or pause spoken playback |
| **User unbound the app from the glasses** | Clean shutdown instead of a silent stall |
| **Translation paused** | Relevant only if the PRD includes translation |

---

## 6. Do not specify these — not available

| Request | Why not |
| --- | --- |
| Show text, images, or notifications on the glasses | No display exists |
| Heads-up navigation, subtitles, teleprompter | No display exists |
| Live video streaming from the glasses to the phone | Only an unvalidated experiment; not a supported path |
| Watching a recording while it is still being recorded | Recordings are files, pulled afterwards |
| Capturing while syncing, or any two glasses actions at once | One mode at a time |
| Cloud AI during a media sync | The phone is off the internet during transfer |
| Custom hardware button mappings | Buttons are fixed in firmware |
| Health sensors — heart rate, steps, eye tracking | Hardware does not have them |
| Location from the glasses | No GPS on the device; the phone's location is available |

---

## 7. Open questions we need answered before finalising scope

Engineering cannot size several features until these are measured on the actual
unit. Most need only a short hardware session.

1. **Photo quality and resolution.** The only documented quality control is a
   coarse 0–6 setting for the quick thumbnail. Full-resolution photo options are
   undocumented. If the PRD depends on image quality — for AI vision accuracy,
   for example — this must be measured first.
2. **Glasses storage capacity**, and therefore how much recording fits before a
   sync is mandatory.
3. **Battery life** under continuous recording, and under periodic photo capture.
4. **Media sync speed** — how long a realistic day's media actually takes.
5. **Whether volume can be set** by the app, or only read. Only reading is
   documented.
6. **Recording length limits** — maximum single video and audio duration.
7. **Firmware update availability.** The vendor's update server currently
   reports no update for this hardware, so firmware fixes cannot be assumed.

---

## 8. Effort guidance for scoping

Rough cost bands, to help balance the PRD. Not estimates.

**Low — the plumbing already exists:** any feature built on photo capture, video
or audio recording, media sync, battery, or the existing AI question flow. Also
the five cheap wins in §5.

**Medium — new work on existing foundations:** new AI behaviours, new spoken
interaction flows, scheduling and automation around capture and sync, changes to
notes and summaries.

**High — carries real risk:** anything needing undocumented device commands,
firmware updates, live preview, or a new glasses capability we have not proven.
Also anything requiring simultaneous glasses activities, which is a hard
platform limit rather than an effort question.

One structural note: the existing app has a single 10,000-line file at its core
that handles most glasses behaviour. Features that need changes there cost more
than their description suggests. Worth knowing when a "small" request comes back
with a surprising estimate.

---

## 9. Two things to decide early

**AI provider and cost model.** The current app routes AI through a hosted relay
with its own subscription plans built in. If the product has different pricing,
a different AI provider, or must run offline, that decision changes the
architecture and should be in the PRD rather than discovered later.

**Privacy posture.** These are camera and microphone glasses worn in public. The
existing app defaults to storing no transcripts and redacting names in exports.
The PRD should state the intended posture explicitly — what is recorded, what is
stored, what leaves the device, and what the wearer and the people around them
are told.

---

## 10. Practical asks for the PRD

To keep the document buildable:

- Phrase every glasses output as something **spoken**, never shown.
- For each feature, state what happens when **the device is busy**.
- For each feature, state whether it needs the **internet**, and what happens
  during a sync when there is none.
- Say when **media sync** is expected to happen and what the user sees.
- Flag any feature that depends on an answer from §7, so we can measure it first.
