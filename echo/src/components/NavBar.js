import React from "react";
import { Pressable, Text, View } from "react-native";
import C from "../theme/colors";
import st from "../theme/styles";
import { VIEWS, VIEW_LABEL, VIEW_TAB } from "../navigation/views";

export default function NavBar({ view, onSelect }) {
  return (
    <View style={st.nav} accessibilityRole="tablist">
      {VIEWS.map((v) => (
        <Pressable
          key={v}
          onPress={() => onSelect(v)}
          accessibilityRole="tab"
          accessibilityLabel={VIEW_LABEL[v]}
          accessibilityState={{ selected: view === v }}
          style={[st.navBtn, view === v && st.navBtnActive]}
        >
          <Text style={[st.navTxt, view === v && { color: C.text }]}>{VIEW_TAB[v]}</Text>
        </Pressable>
      ))}
    </View>
  );
}
