import React from "react";
import { ScrollView, Text, View } from "react-native";
import st from "../theme/styles";
import BigBtn from "../components/BigBtn";

const ROWS = [
  ["Single tap", "Hear the current option"],
  ["Double tap", "Activate"],
  ["Swipe left / right", "Previous / next section"],
  ["Long press", "Speak this help"],
  ["Two-finger double tap", "Repeat last result"],
];

export default function HelpScreen({ onSpeak, screenReaderOn, screenReaderName }) {
  return (
    <ScrollView style={st.pad} contentContainerStyle={{ paddingBottom: 24 }}>
      <Text style={st.listTitle}>GESTURE GUIDE</Text>

      {screenReaderOn && (
        <Text style={st.note}>
          {screenReaderName} is on. Use its own gestures to move around — ECHO
          speaks through {screenReaderName} so nothing is said twice.
        </Text>
      )}

      {ROWS.map(([t, d]) => (
        <View key={t} accessible accessibilityLabel={`${t}. ${d}`} style={st.item}>
          <Text style={st.itemT}>{t}</Text>
          <Text style={st.itemD}>{d}</Text>
        </View>
      ))}

      <BigBtn label="Speak this guide" onPress={onSpeak} a11y="Speak the gesture guide aloud" />
    </ScrollView>
  );
}
