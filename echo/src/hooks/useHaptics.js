import { useCallback, useRef } from "react";
import { Platform, Vibration } from "react-native";
import * as Haptics from "expo-haptics";

/*
  ECHO haptic language — consistent and learnable.

    nav     one short buzz      selection / navigation
    confirm two short buzzes    action confirmed
    error   one long buzz       error / warning
    result  three pulses        analysis complete
    tick    light tap           setting changed

  Two engines, chosen per platform:

  ANDROID → react-native's Vibration.vibrate([...]) with real millisecond
    patterns. Android honours arbitrary on/off arrays, so the four patterns
    are physically distinguishable and intensity scaling actually works by
    lengthening the pulses.

  iOS → expo-haptics (Taptic Engine). iOS ignores custom Vibration patterns
    (it fires one generic buzz for any array), so we sequence Taptic impacts
    with timers instead. Sharper and more precise than Android, but the
    pattern must be built from discrete impacts.

  Both paths are wrapped so a device with no vibration motor fails silently
  instead of crashing.
*/

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// The user-facing catalogue. Settings renders this and the tester speaks it,
// so a new pattern is described in exactly one place.
export const HAPTIC_PATTERNS = [
  { key: "nav", title: "Navigation", desc: "One short buzz" },
  { key: "confirm", title: "Confirmed", desc: "Two short buzzes" },
  { key: "error", title: "Error", desc: "One long buzz" },
  { key: "result", title: "Result ready", desc: "Three pulses" },
];

export const describeHaptic = (key) => {
  const p = HAPTIC_PATTERNS.find((x) => x.key === key);
  return p ? `${p.title}. ${p.desc}.` : "";
};

// Android millisecond patterns: [waitBeforeStart, vibrate, pause, vibrate, ...]
const ANDROID_PATTERNS = {
  nav: [0, 45],
  confirm: [0, 55, 80, 55],
  error: [0, 400],
  result: [0, 80, 90, 80, 90, 80],
  tick: [0, 18],
};

export default function useHaptics(settingsRef) {
  const busy = useRef(false);

  const play = useCallback(async (name = "nav") => {
    const scale = settingsRef.current.hapticScale; // 0.5 gentle | 1 normal | 1.5 strong
    if (scale === 0) return; // haptics turned off

    // Don't let overlapping patterns smear into one long buzz. Errors always
    // win — a failure the user cannot feel is a failure they will not notice.
    if (busy.current && name !== "error") return;
    busy.current = true;

    try {
      if (Platform.OS === "android") {
        const base = ANDROID_PATTERNS[name] || ANDROID_PATTERNS.nav;
        // Scale only the "on" durations (odd indices), keep gaps intact so
        // the rhythm stays recognisable at every intensity.
        const pattern = base.map((v, i) => (i % 2 === 1 ? Math.max(12, Math.round(v * scale)) : v));
        Vibration.vibrate(pattern);
        await sleep(pattern.reduce((a, b) => a + b, 0));
      } else {
        // iOS — sequence Taptic Engine impacts.
        const S = Haptics.ImpactFeedbackStyle;
        const strength = scale < 1 ? S.Light : scale > 1 ? S.Heavy : S.Medium;

        switch (name) {
          case "confirm":
            await Haptics.impactAsync(strength);
            await sleep(95);
            await Haptics.impactAsync(strength);
            break;
          case "error":
            await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
            await sleep(70);
            await Haptics.impactAsync(S.Heavy);
            break;
          case "result":
            for (let i = 0; i < 3; i++) {
              await Haptics.impactAsync(strength);
              await sleep(115);
            }
            break;
          case "tick":
            await Haptics.selectionAsync();
            break;
          case "nav":
          default:
            await Haptics.impactAsync(scale < 1 ? S.Light : S.Medium);
        }
      }
    } catch (e) {
      // No motor (emulator / some tablets) — silent no-op, never crash.
    } finally {
      busy.current = false;
    }
  }, []);

  // Cancel any running Android pattern (used when live mode stops).
  const stop = useCallback(() => {
    if (Platform.OS === "android") {
      try {
        Vibration.cancel();
      } catch (e) {
        // nothing running
      }
    }
    busy.current = false;
  }, []);

  return { play, stop };
}
