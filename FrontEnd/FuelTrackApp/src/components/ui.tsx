import React from 'react';
import { StyleSheet, Text, View, ViewStyle } from 'react-native';
import Svg, { Circle, Path } from 'react-native-svg';
import { colors, fonts } from '../theme';

/** FuelTrack truck mark inside the yellow rounded square. */
export function Logo({ size = 42 }: { size?: number }) {
  const icon = size * 0.57;
  return (
    <View style={[styles.logoBox, { width: size, height: size, borderRadius: size * 0.21 }]}>
      <Svg width={icon} height={icon} viewBox="0 0 24 24" fill="none">
        <Path d="M4 20V6a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v14" stroke="#101215" strokeWidth={2} strokeLinecap="round" />
        <Path
          d="M15 10h2.5L20 12.5V18a1.5 1.5 0 0 1-3 0v-1H4"
          stroke="#101215"
          strokeWidth={2}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <Circle cx={18.5} cy={7} r={1.4} fill="#101215" />
      </Svg>
    </View>
  );
}

export function Wordmark({ size = 24 }: { size?: number }) {
  return (
    <Text style={{ fontFamily: fonts.displayBold, letterSpacing: 1.5, fontSize: size, color: colors.text }}>
      FUEL<Text style={{ color: colors.primary }}>TRACK</Text>
    </Text>
  );
}

export function Badge({
  label,
  bg,
  color,
  style,
}: {
  label: string;
  bg: string;
  color: string;
  style?: ViewStyle;
}) {
  return (
    <View style={[styles.badge, { backgroundColor: bg }, style]}>
      <Text style={{ fontSize: 10, fontFamily: fonts.sansSemi, color }}>{label}</Text>
    </View>
  );
}

export function FuelBar({ pct, color }: { pct: number; color: string }) {
  return (
    <View style={styles.track}>
      <View style={[styles.fill, { width: `${pct}%`, backgroundColor: color }]} />
    </View>
  );
}

const styles = StyleSheet.create({
  logoBox: {
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badge: {
    paddingHorizontal: 9,
    paddingVertical: 4,
    borderRadius: 20,
    alignSelf: 'flex-start',
  },
  track: { height: 9, backgroundColor: '#151719', borderRadius: 6, overflow: 'hidden' },
  fill: { height: '100%', borderRadius: 6 },
});
