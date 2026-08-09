import React from "react";
import { Pressable, ScrollView, Text } from "react-native";
import st from "../theme/styles";
import BigBtn from "../components/BigBtn";
import Stepper from "../components/Stepper";
import { HAPTIC_PATTERNS } from "../hooks/useHaptics";
import { HAPTIC_SCALE_LABEL } from "../state/settingsStore";

export default function SettingsScreen({
  settings,
  onStep,
  onTestHaptic,
  onReset,
  screenReaderOn,
  screenReaderName,
}) {
  return (
    <ScrollView style={st.pad} contentContainerStyle={{ paddingBottom: 24 }}>
      <Text style={st.listTitle}>AUDIO</Text>

      {screenReaderOn && (
        <Text style={st.note}>
          {screenReaderName} is running, so it speaks for ECHO using your own voice
          settings. Speed and volume below apply when {screenReaderName} is off.
        </Text>
      )}

      <Stepper
        label="Voice speed"
        value={`${settings.rate.toFixed(1)}×`}
        onStep={(d) => onStep("rate", d)}
      />
      <Stepper
        label="Voice volume"
        value={`${Math.round(settings.volume * 100)}%`}
        onStep={(d) => onStep("volume", d)}
      />

      <Text style={[st.listTitle, { marginTop: 16 }]}>HAPTICS</Text>
      <Stepper
        label="Feedback intensity"
        value={HAPTIC_SCALE_LABEL[settings.hapticScale]}
        onStep={(d) => onStep("haptics", d)}
      />

      <Text style={[st.listTitle, { marginTop: 16 }]}>FEEL THE PATTERNS</Text>
      <Text style={[st.itemD, { marginBottom: 12 }]}>
        Each buzz means something specific. Tap one to hear its name and feel it.
      </Text>
      {HAPTIC_PATTERNS.map((p) => (
        <Pressable
          key={p.key}
          onPress={() => onTestHaptic(p.key)}
          accessibilityRole="button"
          accessibilityLabel={`Test ${p.title} pattern. ${p.desc}.`}
          style={st.item}
        >
          <Text style={st.itemT}>{p.title}</Text>
          <Text style={st.itemD}>{p.desc}</Text>
        </Pressable>
      ))}

      <BigBtn
        label="Reset to defaults"
        onPress={onReset}
        a11y="Reset voice speed, volume and haptic intensity to their default values"
      />
    </ScrollView>
  );
}
