import React from "react";
import { ScrollView, Text, View } from "react-native";
import C from "../theme/colors";
import st from "../theme/styles";
import BigBtn from "../components/BigBtn";
import { formatTime } from "../utils/time";

const RULE = { arrive: C.ok, leave: C.warn, attn: C.focus };

export default function LiveScreen({ on, feed, onToggle }) {
  return (
    <ScrollView style={st.pad} contentContainerStyle={{ paddingBottom: 24 }}>
      <Text style={st.listTitle}>LIVE AWARENESS</Text>

      <BigBtn
        label={on ? "Stop live awareness" : "Start live awareness"}
        sub="Who's here · who's speaking · arrivals & exits"
        onPress={onToggle}
        primary={!on}
        a11y={
          on
            ? "Stop live awareness"
            : "Start live awareness. ECHO will quietly announce who is here, who is speaking, and when people arrive or leave."
        }
      />

      {on && feed.length === 0 && (
        <Text style={st.itemD}>Listening… the first update arrives in a moment.</Text>
      )}

      <View accessibilityLiveRegion="polite">
        {feed.map((ev) => (
          <View
            key={ev.time}
            accessible
            accessibilityLabel={`${ev.label}. ${ev.text}`}
            style={[st.item, { borderLeftWidth: 4, borderLeftColor: RULE[ev.kind] || C.focus }]}
          >
            <Text style={st.itemT}>{ev.label}</Text>
            <Text style={st.itemD}>{ev.text}</Text>
            <Text style={st.itemD}>{formatTime(ev.time)}</Text>
          </View>
        ))}
      </View>
    </ScrollView>
  );
}
