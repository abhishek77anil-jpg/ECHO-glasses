import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { loadJSON, removeKey, saveJSON } from "../services/storage";

const KEY = "echo.history.v1";
const MAX = 50;

// Entries are { expression, pct, time } where `time` is epoch milliseconds.
// Storing a number rather than a Date is what makes the list survive the
// JSON round trip — a serialised Date comes back as a string and every
// .toLocaleTimeString() call downstream throws.
const HistoryContext = createContext(null);

export function HistoryProvider({ children }) {
  const [items, setItems] = useState([]);
  const ref = useRef([]);
  const hydrated = useRef(false);

  useEffect(() => {
    let alive = true;
    loadJSON(KEY, []).then((saved) => {
      if (!alive) return;
      const clean = Array.isArray(saved)
        ? saved.filter((h) => h && typeof h.expression === "string" && typeof h.time === "number")
        : [];
      // A capture can finish before hydration on a cold start; keep whatever
      // is already in memory ahead of the restored rows.
      const pending = ref.current;
      const merged = [...pending, ...clean].slice(0, MAX);
      ref.current = merged;
      hydrated.current = true;
      setItems(merged);
      // Those pending rows were added before we knew what was on disk, so
      // add() skipped writing them. Flush them now or they vanish on restart.
      if (pending.length > 0) saveJSON(KEY, merged);
    });
    return () => {
      alive = false;
    };
  }, []);

  const add = useCallback((entry) => {
    const next = [entry, ...ref.current].slice(0, MAX);
    ref.current = next;
    setItems(next);
    if (hydrated.current) saveJSON(KEY, next);
    return next;
  }, []);

  const clear = useCallback(() => {
    ref.current = [];
    setItems([]);
    removeKey(KEY);
  }, []);

  return (
    <HistoryContext.Provider value={{ items, add, clear }}>{children}</HistoryContext.Provider>
  );
}

export function useHistory() {
  const ctx = useContext(HistoryContext);
  if (!ctx) throw new Error("useHistory must be used inside <HistoryProvider>");
  return ctx;
}
