import React from "react";
import { Text, View } from "react-native";
import C from "../theme/colors";
import st from "../theme/styles";
import BigBtn from "../components/BigBtn";

export default function ResultScreen({ result, onRepeat, onAgain }) {
  return (
    <View style={[st.pad, { justifyContent: "center", flex: 1 }]}>
      <View style={st.card}>
        <Text style={st.kicker}>EXPRESSION</Text>
        <Text style={st.big}>{result.expression}</Text>

        <Text style={[st.kicker, { marginTop: 18 }]}>CONFIDENCE</Text>
        <View
          style={st.confbar}
          accessible
          accessibilityRole="progressbar"
          accessibilityLabel={`Confidence ${result.pct} percent`}
          accessibilityValue={{ min: 0, max: 100, now: result.pct }}
        >
          <View style={[st.conffill, { width: `${result.pct}%` }]} />
        </View>
        <Text style={{ color: C.text2, marginTop: 6 }}>{result.pct}%</Text>
      </View>

      <BigBtn label="Repeat result" onPress={onRepeat} a11y="Repeat the last result" />
      <BigBtn label="Analyze again" onPress={onAgain} a11y="Analyze again" primary />
    </View>
  );
}
