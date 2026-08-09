import React from "react";
import { Pressable, Text } from "react-native";
import C from "../theme/colors";
import st from "../theme/styles";

export default function BigBtn({ label, sub, onPress, primary, danger, a11y }) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={a11y || label}
      style={[st.bigBtn, primary && st.bigBtnPrimary, danger && st.bigBtnDanger]}
    >
      <Text
        style={[st.bigBtnTxt, primary && { color: C.bg }, danger && st.bigBtnTxtDanger]}
      >
        {label}
      </Text>
      {sub ? <Text style={[st.itemD, primary && { color: "#333" }]}>{sub}</Text> : null}
    </Pressable>
  );
}
