import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { loadJSON, saveJSON } from "../services/storage";

const KEY = "echo.settings.v1";

export const DEFAULT_SETTINGS = {
  rate: 0.9, // slightly slower than normal speech
  volume: 1.0,
  hapticScale: 1, // 0 off | 0.5 gentle | 1 normal | 1.5 strong
};

export const LIMITS = {
  rate: { min: 0.5, max: 1.4, stepBy: 0.1 },
  volume: { min: 0.2, max: 1.0, stepBy: 0.2 },
  hapticSteps: [0, 0.5, 1, 1.5],
};

export const HAPTIC_SCALE_LABEL = { 0: "Off", 0.5: "Gentle", 1: "Normal", 1.5: "Strong" };

const SettingsContext = createContext(null);

export function SettingsProvider({ children }) {
  const [settings, setSettings] = useState(DEFAULT_SETTINGS);

  // Speech and haptics are fired from gesture callbacks that were created on
  // an earlier render, so they read settings through this ref rather than
  // through the closed-over state value. The ref is the source of truth for
  // "what is set right now"; `settings` exists so the UI re-renders.
  const ref = useRef(DEFAULT_SETTINGS);

  useEffect(() => {
    let alive = true;
    loadJSON(KEY, null).then((saved) => {
      if (!alive || !saved) return;
      const merged = { ...DEFAULT_SETTINGS, ...saved };
      ref.current = merged;
      setSettings(merged);
    });
    return () => {
      alive = false;
    };
  }, []);

  const update = useCallback((patch) => {
    const next = { ...ref.current, ...patch };
    ref.current = next;
    setSettings(next);
    saveJSON(KEY, next);
    return next;
  }, []);

  const reset = useCallback(() => {
    ref.current = DEFAULT_SETTINGS;
    setSettings(DEFAULT_SETTINGS);
    saveJSON(KEY, DEFAULT_SETTINGS);
    return DEFAULT_SETTINGS;
  }, []);

  return (
    <SettingsContext.Provider value={{ settings, settingsRef: ref, update, reset }}>
      {children}
    </SettingsContext.Provider>
  );
}

export function useSettings() {
  const ctx = useContext(SettingsContext);
  if (!ctx) throw new Error("useSettings must be used inside <SettingsProvider>");
  return ctx;
}

export const clamp = (v, min, max) => Math.min(max, Math.max(min, v));
