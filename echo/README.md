# ECHO

Ambient awareness for people who can't see the room. ECHO reads the person in
front of you and says what it finds — out loud, privately, in your ear.

This folder is the **reference implementation** of the ECHO experience: a React
Native / Expo app that is complete end-to-end, with the camera and the model
mocked behind two clearly marked seams. It is also the design source of truth
for the eventual Kotlin + Compose port into `android/CyanBridge`.

```bash
npm install
npx expo start          # scan the QR with Expo Go
```

Runs in Expo Go on SDK 54 — no dev build, no native modules, no babel config.

## The design

Dark, huge, and spoken. Nothing depends on sight:

- **Every action has a gesture** that works anywhere on the screen, so a user
  never has to find a control.
- **Every gesture has a voice reply.** Silence is a bug.
- **Every event has a haptic** from a fixed five-pattern vocabulary, so the
  phone can answer without speaking over a conversation.
- Touch targets are ≥56dp on their short edge. The capture target is 280dp.

### Gestures

| Gesture | Action |
| --- | --- |
| Single tap | "Where am I" — speaks the current section |
| Double tap | Primary action (analyze / cancel / toggle live) |
| Swipe left / right | Next / previous section |
| Long press | Open and speak help |
| Two-finger double tap | Repeat the last result |

Composed in [useGestures.js](src/hooks/useGestures.js) as
`Race(twoFingerDouble, longPress, flingLeft, flingRight, Exclusive(doubleTap, singleTap))`.
`Exclusive` is what stops a double tap from also firing a single tap.

### Haptic vocabulary

| Pattern | Meaning |
| --- | --- |
| `nav` — one short buzz | selection / navigation |
| `confirm` — two short buzzes | action confirmed |
| `error` — one long buzz | error or warning |
| `result` — three pulses | analysis complete |
| `tick` — light tap | setting changed |

Android gets real millisecond patterns via `Vibration.vibrate([...])`; iOS
sequences Taptic impacts, because it collapses any custom pattern array into
one generic buzz. Settings has a tester so a user can learn all four on demand.

## Structure

```
App.js                      providers only
src/EchoShell.js            orchestration: state, flows, gesture wiring
src/navigation/views.js     the swipe ring and every spoken label
src/state/
  echoReducer.js            capture state machine (idle→analyzing→result/error)
  settingsStore.js          voice + haptic settings, persisted
  historyStore.js           last 50 results, persisted
src/services/
  analysisService.js        ← HARDWARE SEAM (capture + model)
  liveService.js            ← HARDWARE SEAM (continuous scene stream)
  storage.js                AsyncStorage with an in-memory fallback
src/hooks/                  useSpeech, useHaptics, useGestures
src/screens/                Home, Result, Live, History, Settings, Help
src/components/             Header, NavBar, BigBtn, Stepper
src/theme/                  colors, styles
```

## Two decisions worth knowing before you change anything

**A running screen reader owns the voice channel.** `useSpeech` detects
TalkBack / VoiceOver and, when one is active, routes text through
`announceForAccessibility` *only*. Calling `Speech.speak()` as well — which is
the obvious thing to do — plays every sentence twice, in two voices, a beat
apart. Do not add it back.

**Ambient speech must not interrupt an answer.** Live-awareness events fire on
a timer; a result fires because the user asked. `say()` takes a priority
(`LOW` / `NORMAL` / `HIGH`) and drops a lower-priority line rather than cutting
off a higher-priority one mid-sentence.

Related: every capture carries a generation number. Cancelling or starting a
new capture bumps it, so a slow run that lands after the user has moved on is
discarded instead of overwriting a newer result.

## Going real

Both seams are single files with a documented contract; nothing else in the app
changes.

`analyzePerson({ signal })` in [analysisService.js](src/services/analysisService.js)
resolves `{ ok: true, expression, confidence }`, resolves
`{ ok: false, error }`, or rejects `CancelledError`. Replace its body with:
glasses camera over BLE control → Wi-Fi Direct transfer → JPEG → expression
model. **Honour `signal`** — the UI lets the user cancel, and a capture that
ignores cancellation will hold the glasses session lease open.

`createLiveSession({ onEvent })` in [liveService.js](src/services/liveService.js)
is a start/stop handle that pushes `{ label, kind, text, time }`. Replace the
scripted timer with the continuous scene stream.

Note that the real path needs an Expo **dev build** and a native module over
the vendor AAR — the glasses cannot be reached from Expo Go. The rest of the
app is unaffected by that change.

For the protocol itself see `../android/docs/VENDOR_SDK_REFERENCE_EN.md` and
`../android/AGENTS.md`; the session-lease rules in `../CLAUDE.md` apply to any
new command path.
