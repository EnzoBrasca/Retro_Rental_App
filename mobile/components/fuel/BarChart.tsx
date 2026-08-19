import { StyleSheet, Text, View } from 'react-native';
import { colors, fonts } from '../../constants/theme';

type Bar = { l: string; v: number; c?: string; amount: string; highlight?: boolean };

/**
 * Escala contra la que se dibuja cada barra. NUNCA devuelve 0.
 *
 * Con todas las barras en cero —un período con cargas registradas pero de gasto
 * cero, o importes que redondean a cero— el máximo daba 0, el ancho quedaba en
 * `0 / 0` = `NaN`, y React Native recibía `width: "NaN%"`: warning de estilo y
 * barra sin dibujar. El `Math.max(0, ...)` cubre además la lista vacía, donde
 * `Math.max()` sin argumentos devuelve -Infinity.
 */
export function escalaMaxima(data: Bar[]): number {
  return Math.max(0, ...data.map((d) => d.v)) || 1;
}

/** Barras horizontales rankeadas — usadas en los desgloses por vehículo/proveedor/operario. */
export function BarChart({ data }: { data: Bar[] }) {
  const max = escalaMaxima(data);
  return (
    <View style={{ gap: 13, paddingTop: 4 }}>
      {/* La clave es la etiqueta, no el índice: la lista se reordena por ranking
          al cambiar de período, y con el índice React reutiliza el nodo de una
          fila para los datos de otra. */}
      {data.map((d) => (
        <View key={d.l}>
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
