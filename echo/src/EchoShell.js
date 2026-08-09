import React, { useCallback, useEffect, useReducer, useRef, useState } from "react";
import { StatusBar, View } from "react-native";
import { GestureDetector } from "react-native-gesture-handler";
import { SafeAreaView } from "react-native-safe-area-context";

import C from "./theme/colors";
import st from "./theme/styles";

import Header from "./components/Header";
import NavBar from "./components/NavBar";

import HomeScreen from "./screens/HomeScreen";
import ResultScreen from "./screens/ResultScreen";
import LiveScreen from "./screens/LiveScreen";
import HistoryScreen from "./screens/HistoryScreen";
import SettingsScreen from "./screens/SettingsScreen";
import HelpScreen from "./screens/HelpScreen";

import useSpeech, { PRIORITY } from "./hooks/useSpeech";
import useHaptics, { describeHaptic } from "./hooks/useHaptics";
import useGestures from "./hooks/useGestures";

import { clamp, LIMITS, useSettings } from "./state/settingsStore";
import { useHistory } from "./state/historyStore";
import { echoReducer, initialEchoState, STATUS_BADGE } from "./state/echoReducer";

import {
  analyzePerson,
  CancelledError,
  speakFailure,
  speakResult,
} from "./services/analysisService";
import { createLiveSession } from "./services/liveService";

import { GREETING, HELP_SPEECH, nextView, prevView, VIEW_LABEL } from "./navigation/views";

const LIVE_FEED_MAX = 8;

export default function EchoShell() {
  const { settings, settingsRef, update, reset } = useSettings();
  const { items: history, add: addHistory, clear: clearHistory } = useHistory();

  const { say, stop: stopSpeech, screenReaderOn, screenReaderName } = useSpeech(settingsRef);
  const { play, stop: stopHaptics } = useHaptics(settingsRef);

  const [view, setView] = useState("home");
  const [echo, dispatch] = useReducer(echoReducer, initialEchoState);
  const [liveOn, setLiveOn] = useState(false);
  const [liveFeed, setLiveFeed] = useState([]);

  // Gesture callbacks are built once, so anything they read must come from a
  // ref rather than a closed-over render value.
  const viewRef = useRef(view);
  viewRef.current = view;
  const echoRef = useRef(echo);
  echoRef.current = echo;

  const lastSpeechRef = useRef(null); // what "repeat last result" replays
  const abortRef = useRef(null); // in-flight capture
  const genRef = useRef(0); // capture generation, see echoReducer
  const liveRef = useRef(null); // live session handle

  const announce = useCallback(
    (text, haptic, priority = PRIORITY.NORMAL) => {
      say(text, { priority });
      if (haptic) play(haptic);
    },
    [say, play]
  );

  /* ---------- navigation ---------- */

  const goView = useCallback(
    (name, speak = true) => {
      setView(name);
      if (speak) {
        announce(`${VIEW_LABEL[name]} Swipe right for ${nextView(name)}.`, "nav");
      }
    },
    [announce]
  );

  const swipe = useCallback(
    (dir) => {
      const cur = viewRef.current;
      goView(dir === "next" ? nextView(cur) : prevView(cur));
    },
    [goView]
  );

  /* ---------- capture ---------- */

  const startCapture = useCallback(async () => {
    // Guard on the abort handle, not on echo.status: taps can arrive faster
    // than React re-renders, and echoRef would still read "idle" for the
    // instant between dispatch and the next render.
    if (abortRef.current) return;

    const controller = new AbortController();
    abortRef.current = controller;
    const gen = (genRef.current += 1);

    dispatch({ type: "CAPTURE_START", gen });
    setView("home");
    announce("Analyzing.", "confirm", PRIORITY.HIGH);

    try {
      const res = await analyzePerson({ signal: controller.signal });

      if (res.ok) {
        const pct = Math.round(res.confidence * 100);
        const speech = speakResult(res.expression, pct);
        lastSpeechRef.current = speech;
        dispatch({ type: "CAPTURE_SUCCESS", gen, result: { expression: res.expression, pct } });
        addHistory({ expression: res.expression, pct, time: Date.now() });
        announce(speech, "result", PRIORITY.HIGH);
      } else {
        const msg = speakFailure(res.error);
        lastSpeechRef.current = msg;
        dispatch({ type: "CAPTURE_FAILURE", gen, error: msg });
        announce(msg, "error", PRIORITY.HIGH);
      }
    } catch (e) {
      if (e instanceof CancelledError) return; // cancelCapture already spoke
      const msg = "Something went wrong. Please try again.";
      lastSpeechRef.current = msg;
      dispatch({ type: "CAPTURE_FAILURE", gen, error: msg });
      announce(msg, "error", PRIORITY.HIGH);
    } finally {
      if (abortRef.current === controller) abortRef.current = null;
    }
  }, [announce, addHistory]);

  const cancelCapture = useCallback(() => {
    if (abortRef.current) abortRef.current.abort();
    abortRef.current = null;
    // Bumping the generation is what stops a result that is already in flight
    // from landing on a screen the user has cancelled out of.
    const gen = (genRef.current += 1);
    dispatch({ type: "CAPTURE_CANCEL", gen });
    announce("Analysis cancelled.", "nav", PRIORITY.HIGH);
  }, [announce]);

  const repeatLast = useCallback(() => {
    if (lastSpeechRef.current) {
      announce(lastSpeechRef.current, "nav", PRIORITY.HIGH);
    } else {
      announce(
        "No result yet. Double tap the center of the screen to analyze.",
        "nav",
        PRIORITY.HIGH
      );
    }
  }, [announce]);

  /* ---------- live awareness ---------- */

  const pushLiveEvent = useCallback(
    (ev) => {
      setLiveFeed((f) => [ev, ...f].slice(0, LIVE_FEED_MAX));
      // LOW priority: an ambient update must never cut off a result the user
      // explicitly asked for.
      say(ev.text, { priority: PRIORITY.LOW });
      play(ev.kind === "leave" ? "nav" : "confirm");
    },
    [say, play]
  );
  const pushLiveRef = useRef(pushLiveEvent);
  pushLiveRef.current = pushLiveEvent;

  const toggleLive = useCallback(() => {
    if (liveRef.current) {
      liveRef.current.stop();
      liveRef.current = null;
      stopHaptics();
      setLiveOn(false);
      announce("Live awareness off.", "nav", PRIORITY.HIGH);
      return;
    }

    const session = createLiveSession({ onEvent: (ev) => pushLiveRef.current(ev) });
    liveRef.current = session;
    setLiveOn(true);
    announce(
      "Live awareness on. I will quietly tell you who is here and what changes. Only you can hear this.",
      "confirm",
      PRIORITY.HIGH
    );
    session.start();
  }, [announce, stopHaptics]);

  /* ---------- settings ---------- */

  const step = useCallback(
    (key, dir) => {
      const s = settingsRef.current;

      if (key === "rate") {
        const rate = clamp(
          +(s.rate + dir * LIMITS.rate.stepBy).toFixed(1),
          LIMITS.rate.min,
          LIMITS.rate.max
        );
        update({ rate });
        // Spoken at the new rate, so the user hears the change itself.
        announce(`Voice speed ${rate.toFixed(1)}`, "tick");
        return;
      }

      if (key === "volume") {
        const volume = clamp(
          +(s.volume + dir * LIMITS.volume.stepBy).toFixed(1),
          LIMITS.volume.min,
          LIMITS.volume.max
        );
        update({ volume });
        announce(`Volume ${Math.round(volume * 100)} percent`, "tick");
        return;
      }

      if (key === "haptics") {
        const steps = LIMITS.hapticSteps;
        const i = clamp(steps.indexOf(s.hapticScale) + dir, 0, steps.length - 1);
        update({ hapticScale: steps[i] });
        stopHaptics();
        announce(`Haptics ${["Off", "Gentle", "Normal", "Strong"][i]}`, "confirm");
      }
    },
    [announce, update, stopHaptics]
  );

  const resetSettings = useCallback(() => {
    reset();
    stopHaptics();
    announce("Settings reset to defaults.", "confirm", PRIORITY.HIGH);
  }, [reset, stopHaptics, announce]);

  // Speak the pattern's name first, then let the user feel it cleanly — a buzz
  // underneath the words is much harder to learn.
  const testHaptic = useCallback(
    (key) => {
      say(describeHaptic(key), { priority: PRIORITY.HIGH });
      setTimeout(() => play(key), 900);
    },
    [say, play]
  );

  const onClearHistory = useCallback(() => {
    clearHistory();
    announce("History cleared.", "confirm", PRIORITY.HIGH);
  }, [clearHistory, announce]);

  /* ---------- global gesture handlers ---------- */

  const primaryAction = useCallback(() => {
    const v = viewRef.current;
    if (v === "home") {
      if (abortRef.current) cancelCapture();
      else startCapture();
    } else if (v === "live") {
      toggleLive();
    } else {
      announce(VIEW_LABEL[v], "nav");
    }
  }, [startCapture, cancelCapture, toggleLive, announce]);

  const whereAmI = useCallback(() => {
    const v = viewRef.current;
    const extra =
      v === "home" && echoRef.current.status === "result"
        ? " Result ready. Two finger double tap to repeat it."
        : "";
    announce(VIEW_LABEL[v] + extra, "tick");
  }, [announce]);

  const openHelp = useCallback(() => {
    setView("help");
    announce(HELP_SPEECH, "confirm", PRIORITY.HIGH);
  }, [announce]);

  const handlersRef = useRef({});
  handlersRef.current = {
    onPrimary: primaryAction,
    onWhereAmI: whereAmI,
    onRepeat: repeatLast,
    onHelp: openHelp,
    onSwipe: swipe,
  };
  const gestures = useGestures(handlersRef);

  /* ---------- lifecycle ---------- */

  useEffect(() => {
    const t = setTimeout(() => announce(GREETING, "nav", PRIORITY.HIGH), 600);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(
    () => () => {
      if (liveRef.current) liveRef.current.stop();
      if (abortRef.current) abortRef.current.abort();
      stopHaptics();
      stopSpeech();
    },
    [stopHaptics, stopSpeech]
  );

  /* ---------- render ---------- */

  const showResult = view === "home" && echo.status === "result" && echo.result;

  return (
    <SafeAreaView style={st.root}>
      <StatusBar barStyle="light-content" backgroundColor={C.bg} />

      <Header status={STATUS_BADGE[echo.status]} />

      <GestureDetector gesture={gestures}>
        <View style={{ flex: 1 }} collapsable={false}>
          {view === "home" &&
            (showResult ? (
              <ResultScreen result={echo.result} onRepeat={repeatLast} onAgain={startCapture} />
            ) : (
              <HomeScreen
                status={echo.status}
                error={echo.error}
                onCapture={startCapture}
                onCancel={cancelCapture}
              />
            ))}

          {view === "live" && <LiveScreen on={liveOn} feed={liveFeed} onToggle={toggleLive} />}

          {view === "history" && <HistoryScreen items={history} onClear={onClearHistory} />}

          {view === "settings" && (
            <SettingsScreen
              settings={settings}
              onStep={step}
              onTestHaptic={testHaptic}
              onReset={resetSettings}
              screenReaderOn={screenReaderOn}
              screenReaderName={screenReaderName}
            />
          )}

          {view === "help" && (
            <HelpScreen
              onSpeak={() => announce(HELP_SPEECH, "nav", PRIORITY.HIGH)}
              screenReaderOn={screenReaderOn}
              screenReaderName={screenReaderName}
            />
          )}
        </View>
      </GestureDetector>

      <NavBar view={view} onSelect={goView} />
    </SafeAreaView>
  );
}
