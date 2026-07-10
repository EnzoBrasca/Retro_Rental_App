import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, fonts } from '../../constants/theme';

/**
 * Selector de una opción entre varias, renderizado como chips que se envuelven.
 * Reutilizable para enums (tipo de vehiculo, estado) y catálogos (proveedor,
 * precio, vehiculo). La clave puede ser string o number.
 */
export function OptionChips<T extends string | number>({
  options,
  value,
  onChange,
}: {
  options: { key: T; label: string }[];
  value: T | null;
  onChange: (key: T) => void;
}) {
  return (
    <View style={styles.wrap}>
      {options.map((o) => {
        const selected = o.key === value;
        return (
          <Pressable
            key={String(o.key)}
            style={[styles.chip, selected && styles.chipActive]}
            onPress={() => onChange(o.key)}
          >
            <Text style={[styles.chipText, selected && styles.chipTextActive]}>{o.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: {
    backgroundColor: colors.surfaceInput,
    borderWidth: 1,
    borderColor: colors.borderInput,
    paddingHorizontal: 13,
    paddingVertical: 9,
    borderRadius: 10,
  },
  chipActive: { backgroundColor: colors.amberBg, borderColor: colors.primary },
  chipText: { color: colors.textMuted, fontSize: 13, fontFamily: fonts.sansMed },
  chipTextActive: { color: colors.primary, fontFamily: fonts.sansSemi },
});
