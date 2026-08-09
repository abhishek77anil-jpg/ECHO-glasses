import React from "react";
import { Pressable, Text, View } from "react-native";
import C from "../theme/colors";
import st from "../theme/styles";

export default function HomeScreen({ status, error, onCapture, onCancel }) {
  const analyzing = status === "analyzing";

  return (
    <View style={st.center}>
      <Pressable
        onPress={analyzing ? onCancel : onCapture}
        accessibilityRole="button"
        accessibilityLabel={
          analyzing
            ? "Cancel analysis"
            : "Capture and analyze the person in front of you"
        }
        accessibilityState={{ busy: analyzing }}
        style={[st.capture, analyzing && { borderColor: C.focus }]}
      >
        <Text style={st.captureTxt}>{analyzing ? "ANALYZING" : "CAPTURE"}</Text>
        <Text style={st.captureSub}>
          {analyzing ? "Double tap to cancel" : "Double tap to analyze"}
        </Text>
      </Pressable>

      <Text style={st.status} accessibilityLiveRegion="polite">
        {analyzing ? (
          <Text style={{ color: C.text, fontWeight: "700" }}>Analyzing…{"\n"}</Text>
        ) : status === "error" ? (
          <>
            <Text style={st.errText}>{error}{"\n"}</Text>
            <Text>Double tap the center button to try again.</Text>
          </>
        ) : (
          <>
            <Text style={{ color: C.text, fontWeight: "700" }}>Ready{"\n"}</Text>
            <Text>Double tap the center button to begin. Long press for help.</Text>
          </>
        )}
      </Text>
    </View>
  );
}
