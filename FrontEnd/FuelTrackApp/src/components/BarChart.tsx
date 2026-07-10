import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { colors, fonts } from '../theme';

type Bar = { l: string; v: number; c?: string; amount: string; highlight?: boolean };

/** Horizontal ranked bars — used for provider and per-operator breakdowns. */
export function BarChart({ data }: { data: Bar[] }) {
  const max = Math.max(...data.map((d) => d.v));
  return (
    <View style={{ gap: 13, paddingTop: 4 }}>
      {data.map((d, i) => (
        <View key={i}>
          <View style={styles.row}>
            <Text style={[styles.label, d.highlight && { color: colors.primary }]}>{d.l}</Text>
            <Text style={styles.amount}>{d.amount}</Text>
          </View>
          <View style={styles.track}>
            <View
              style={[
                styles.fill,
                {
                  width: `${(d.v / max) * 100}%`,
                  backgroundColor: d.c ?? (d.highlight ? colors.primary : '#3a3f45'),
                },
              ]}
            />
          </View>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 5 },
  label: { fontSize: 12, color: '#C9CDD2', fontFamily: fonts.sans },
  amount: { fontSize: 12, fontFamily: fonts.mono, color: colors.text },
  track: { height: 9, backgroundColor: '#151719', borderRadius: 5, overflow: 'hidden' },
  fill: { height: '100%', borderRadius: 5 },
});
