import React from "react";
import { Text, View } from "react-native";
import st from "../theme/styles";

export default function Header({ status }) {
  return (
    <View style={st.header}>
      <Text style={st.logo} accessibilityRole="header">
        E C H O
      </Text>
      <View
        style={st.badge}
        accessible
        accessibilityLiveRegion="polite"
        accessibilityLabel={`Status ${status.toLowerCase()}`}
      >
        <Text style={st.badgeTxt}>{status}</Text>
      </View>
    </View>
  );
}
