import { useCallback, useEffect, useRef, useState } from "react";
import { AccessibilityInfo, Platform } from "react-native";
import * as Speech from "expo-speech";

/*
  Central speech service.

  Two rules, both learned the hard way in screen-reader testing:

  1. NEVER speak twice. The original build called Speech.speak() *and*
     announceForAccessibility() every time. With TalkBack or VoiceOver
     running that plays every sentence twice, in two different voices, a
     beat apart — unusable. So: if a screen reader is active it owns the
     voice channel and we only post announcements to it, at the user's own
     rate and volume. If it is not, we drive expo-speech ourselves.

  2. Ambient chatter must never talk over an answer the user asked for.
     Live-awareness events fire on a timer; a result fires because the user
     double-tapped. Priorities below keep the timer from stomping the answer.
*/

export const PRIORITY = {
  LOW: 0, // live awareness feed, ambient
  NORMAL: 1, // navigation, settings ticks
  HIGH: 2, // results, errors, help, anything the user directly asked for
};

export default function useSpeech(settingsRef) {
  const [screenReaderOn, setScreenReaderOn] = useState(false);
  const screenReaderRef = useRef(false);

  const speakingRef = useRef(false);
  const priorityRef = useRef(-1);
  const tokenRef = useRef(0);

  useEffect(() => {
    let alive = true;

    const apply = (on) => {
      if (!alive) return;
      screenReaderRef.current = on;
      setScreenReaderOn(on);
      // Handing the channel over: silence anything we were saying ourselves.
      if (on) Speech.stop();
    };

    AccessibilityInfo.isScreenReaderEnabled().then(apply).catch(() => {});
    const sub = AccessibilityInfo.addEventListener("screenReaderChanged", apply);

    return () => {
      alive = false;
      if (sub && sub.remove) sub.remove();
      Speech.stop();
    };
  }, []);

  const say = useCallback((text, { priority = PRIORITY.NORMAL } = {}) => {
    if (!text) return;

    if (screenReaderRef.current) {
      AccessibilityInfo.announceForAccessibility(text);
      return;
    }

    // Something more important is mid-sentence — drop this rather than cut it off.
    if (speakingRef.current && priority < priorityRef.current) return;

    const token = (tokenRef.current += 1);
    const settle = () => {
      if (tokenRef.current !== token) return; // a newer utterance owns the channel
      speakingRef.current = false;
      priorityRef.current = -1;
    };

    Speech.stop(); // fires onStopped for the previous utterance; `token` ignores it
    speakingRef.current = true;
    priorityRef.current = priority;

    try {
      Speech.speak(text, {
        language: "en-US",
        rate: settingsRef.current.rate,
        // expo-speech honours `volume` on Android only; on iOS the system
        // media volume governs. Harmless to pass either way.
        volume: settingsRef.current.volume,
        onDone: settle,
        onStopped: settle,
        onError: settle,
      });
    } catch (e) {
      settle(); // no TTS engine installed — stay silent rather than crash
    }
  }, []);

  const stop = useCallback(() => {
    tokenRef.current += 1;
    speakingRef.current = false;
    priorityRef.current = -1;
    try {
      Speech.stop();
    } catch (e) {
      // nothing playing
    }
  }, []);

  return {
    say,
    stop,
    screenReaderOn,
    screenReaderName: Platform.OS === "ios" ? "VoiceOver" : "TalkBack",
  };
}
