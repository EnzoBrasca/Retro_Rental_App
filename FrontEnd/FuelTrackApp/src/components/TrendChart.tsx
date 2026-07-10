import React from 'react';
import { View } from 'react-native';
import Svg, { Circle, Defs, LinearGradient, Line, Path, Stop, Text as SvgText } from 'react-native-svg';
import { colors, fonts } from '../theme';

type Point = { l: string; v: number };

/** Area + line trend chart, ported from the prototype's buildTrend(). */
export function TrendChart({ data }: { data: Point[] }) {
  const W = 560;
  const H = 210;
  const pl = 44;
  const pr = 12;
  const pt = 14;
  const pb = 28;
  const max = Math.max(...data.map((d) => d.v)) * 1.15;
  const iw = W - pl - pr;
  const ih = H - pt - pb;
  const x = (i: number) => pl + (data.length === 1 ? iw / 2 : (iw * i) / (data.length - 1));
  const y = (v: number) => pt + ih - (v / max) * ih;

  const pts = data.map((d, i) => [x(i), y(d.v)] as const);
  const line = pts.map((p, i) => (i ? 'L' : 'M') + p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join(' ');
  const area = `${line} L${x(data.length - 1).toFixed(1)} ${pt + ih} L${pl} ${pt + ih} Z`;
  const gridLines = [0, 0.25, 0.5, 0.75, 1];

  return (
    <View style={{ width: '100%' }}>
      <Svg viewBox={`0 0 ${W} ${H}`} width="100%" height={undefined} style={{ aspectRatio: W / H }}>
        <Defs>
          <LinearGradient id="ffTrend" x1="0" y1="0" x2="0" y2="1">
            <Stop offset="0%" stopColor={colors.primary} stopOpacity={0.35} />
            <Stop offset="100%" stopColor={colors.primary} stopOpacity={0} />
          </LinearGradient>
        </Defs>

        {gridLines.map((f, i) => {
          const gy = pt + ih - f * ih;
          return (
            <React.Fragment key={i}>
              <Line x1={pl} x2={W - pr} y1={gy} y2={gy} stroke="#2A2F35" strokeWidth={1} />
              <SvgText x={pl - 8} y={gy + 4} fill={colors.textDim} fontSize={10} textAnchor="end" fontFamily={fonts.mono}>
                {'$' + Math.round(max * f) + 'k'}
              </SvgText>
            </React.Fragment>
          );
        })}

        <Path d={area} fill="url(#ffTrend)" />
        <Path d={line} fill="none" stroke={colors.primary} strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" />

        {pts.map((p, i) => (
          <Circle key={'c' + i} cx={p[0]} cy={p[1]} r={3.5} fill={colors.bg} stroke={colors.primary} strokeWidth={2} />
        ))}
        {data.map((d, i) => (
          <SvgText key={'l' + i} x={x(i)} y={H - 8} fill={colors.textFaint} fontSize={11} textAnchor="middle" fontFamily={fonts.sans}>
            {d.l}
          </SvgText>
        ))}
      </Svg>
    </View>
  );
}
