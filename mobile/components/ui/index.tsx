import { Image, StyleSheet, Text, View, ViewStyle } from 'react-native';
import { colors, fonts } from '../../constants/theme';

/**
 * Símbolo de RetroRental: el elefante de la marca.
 *
 * Reutiliza el mismo asset que el ícono de la aplicación, así la marca que ve
 * el usuario en el launcher y la que ve al abrir la app son la misma imagen y
 * no dos cosas que se pueden desincronizar.
 */
export function Logo({ size = 42 }: { size?: number }) {
  return (
    <Image
      source={require('../../assets/icon.png')}
      style={{ width: size, height: size, borderRadius: size * 0.21 }}
      resizeMode="cover"
    />
  );
}

/**
 * Logotipo. En amarillo de marca, como el logo real del cliente: la versión
 * anterior decía FUELTRACK, que era el nombre de la plantilla de la que partió
 * el proyecto.
 */
export function Wordmark({ size = 24 }: { size?: number }) {
  return (
    <Text
      style={{
        fontFamily: fonts.displayBold,
        letterSpacing: 1.5,
        fontSize: size,
        color: colors.primary,
      }}
    >
      RETRO RENTAL
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
  badge: {
    paddingHorizontal: 9,
    paddingVertical: 4,
    borderRadius: 20,
    alignSelf: 'flex-start',
  },
  track: { height: 9, backgroundColor: '#151719', borderRadius: 6, overflow: 'hidden' },
  fill: { height: '100%', borderRadius: 6 },
});
