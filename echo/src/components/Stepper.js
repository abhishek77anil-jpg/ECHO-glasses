import React from "react";
import { Pressable, Text, View } from "react-native";
import st from "../theme/styles";

export default function Stepper({ label, value, onStep }) {
  return (
    <View style={st.setting}>
      <Text style={st.itemT}>{label}</Text>
      <View style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
        <Pressable
          onPress={() => onStep(-1)}
          accessibilityRole="button"
          accessibilityLabel={`Decrease ${label}`}
          accessibilityValue={{ text: String(value) }}
          style={st.stepBtn}
        >
          <Text style={st.stepTxt}>−</Text>
        </Pressable>
        <Text style={[st.itemT, { minWidth: 70, textAlign: "center" }]}>{value}</Text>
        <Pressable
          onPress={() => onStep(1)}
          accessibilityRole="button"
          accessibilityLabel={`Increase ${label}`}
          accessibilityValue={{ text: String(value) }}
          style={st.stepBtn}
        >
          <Text style={st.stepTxt}>+</Text>
        </Pressable>
      </View>
    </View>
  );
}
