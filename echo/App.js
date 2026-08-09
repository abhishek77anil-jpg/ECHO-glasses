import React from "react";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { SafeAreaProvider } from "react-native-safe-area-context";

import EchoShell from "./src/EchoShell";
import { SettingsProvider } from "./src/state/settingsStore";
import { HistoryProvider } from "./src/state/historyStore";

// Root is providers only. Everything that has behaviour lives in
// src/EchoShell.js so this file never becomes the place features accrete.
export default function App() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <SettingsProvider>
          <HistoryProvider>
            <EchoShell />
          </HistoryProvider>
        </SettingsProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
