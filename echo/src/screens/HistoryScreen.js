import React from "react";
import { ScrollView, Text, View } from "react-native";
import st from "../theme/styles";
import BigBtn from "../components/BigBtn";
import { formatTime } from "../utils/time";

export default function HistoryScreen({ items, onClear }) {
  return (
    <ScrollView style={st.pad} contentContainerStyle={{ paddingBottom: 24 }}>
      <Text style={st.listTitle}>HISTORY</Text>

      {items.length === 0 && <Text style={st.empty}>History is currently empty.</Text>}

      {items.map((h, i) => (
        <View
          key={`${h.time}-${i}`}
          accessible
          accessibilityLabel={`${h.expression}, confidence ${h.pct} percent, at ${formatTime(h.time)}`}
          style={st.item}
        >
          <Text style={st.itemT}>{h.expression}</Text>
          <Text style={st.itemD}>
            Confidence {h.pct}% · {formatTime(h.time)}
          </Text>
        </View>
      ))}

      {items.length > 0 && (
        <BigBtn
          label="Clear history"
          onPress={onClear}
          danger
          a11y={`Clear history. This deletes all ${items.length} saved results.`}
        />
      )}
    </ScrollView>
  );
}
