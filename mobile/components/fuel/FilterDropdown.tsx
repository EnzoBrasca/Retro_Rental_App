import { useRef, useState } from 'react';
import {
  LayoutRectangle,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { colors, fonts, radius } from '../../constants/theme';

export type FilterDropdownOption<T extends string | number> = {
  key: T;
  label: string;
};

type SingleSelectProps<T extends string | number> = {
  mode: 'single';
  label: string;
  options: FilterDropdownOption<T>[];
  selected: T;
  onSelect: (key: T) => void;
};

type MultiSelectProps<T extends string | number> = {
  mode: 'multi';
  label: string;
  options: FilterDropdownOption<T>[];
  selected: T[];
  onToggle: (key: T) => void;
  onClear: () => void;
  allLabel?: string;
};

type FilterDropdownProps<T extends string | number> = SingleSelectProps<T> | MultiSelectProps<T>;

/**
 * Desplegable genérico (single o multi select) que sirve de filtro: un
 * trigger tipo "input" que muestra la selección actual, y un panel que se
 * abre hacia abajo mediante un Modal transparente. Se cierra al tocar fuera
 * o, en modo single, al elegir una opción.
 */
export function FilterDropdown<T extends string | number>(props: FilterDropdownProps<T>) {
  const [open, setOpen] = useState(false);
  const [triggerLayout, setTriggerLayout] = useState<LayoutRectangle | null>(null);
  const triggerRef = useRef<View>(null);

  const openDropdown = () => {
    triggerRef.current?.measureInWindow((x, y, width, height) => {
      setTriggerLayout({ x, y, width, height });
      setOpen(true);
    });
  };

  const closeDropdown = () => setOpen(false);

  const allLabel = props.mode === 'multi' ? (props.allLabel ?? 'Todos') : undefined;

  const summary = (() => {
    if (props.mode === 'single') {
      return props.options.find((o) => o.key === props.selected)?.label ?? '';
    }
    const count = props.selected.length;
    if (count === 0) return allLabel;
    if (count === 1) {
      return props.options.find((o) => o.key === props.selected[0])?.label ?? '1 seleccionado';
    }
    return `${count} seleccionados`;
  })();

  return (
    <View>
      <Text style={styles.label}>{props.label}</Text>
      <Pressable ref={triggerRef} style={styles.trigger} onPress={openDropdown}>
        <Text style={styles.triggerText} numberOfLines={1}>
          {summary}
        </Text>
        <Text style={styles.caret}>▾</Text>
      </Pressable>

      <Modal visible={open} transparent animationType="fade" onRequestClose={closeDropdown}>
        <Pressable style={StyleSheet.absoluteFill} onPress={closeDropdown} />
        {triggerLayout && (
          <View
            style={[
              styles.panel,
              {
                top: triggerLayout.y + triggerLayout.height + 4,
                left: triggerLayout.x,
                width: triggerLayout.width,
              },
            ]}
          >
            <ScrollView style={styles.optionsList} keyboardShouldPersistTaps="handled">
              {props.mode === 'multi' && (
                <Pressable style={styles.option} onPress={props.onClear}>
                  <Text
                    style={[
                      styles.optionText,
                      props.selected.length === 0 && styles.optionTextActive,
                    ]}
                  >
                    {allLabel}
                  </Text>
                </Pressable>
              )}
              {props.options.map((o) => {
                const isActive =
                  props.mode === 'single'
                    ? o.key === props.selected
                    : props.selected.includes(o.key);
                return (
                  <Pressable
                    key={String(o.key)}
                    style={styles.option}
                    onPress={() => {
                      if (props.mode === 'single') {
                        props.onSelect(o.key);
                        closeDropdown();
                      } else {
                        props.onToggle(o.key);
                      }
                    }}
                  >
                    <Text style={[styles.optionText, isActive && styles.optionTextActive]}>
                      {o.label}
                    </Text>
                  </Pressable>
                );
              })}
            </ScrollView>
            {props.mode === 'multi' && (
              <Pressable style={styles.doneBtn} onPress={closeDropdown}>
                <Text style={styles.doneBtnText}>Listo</Text>
              </Pressable>
            )}
          </View>
        )}
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  label: { fontSize: 11, color: colors.textFaint, marginBottom: 6, fontFamily: fonts.sans },
  trigger: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: colors.surfaceInput,
    borderWidth: 1,
    borderColor: colors.borderInput,
    borderRadius: 10,
    paddingHorizontal: 13,
    paddingVertical: 9,
  },
  triggerText: { flexShrink: 1, color: colors.text, fontSize: 13, fontFamily: fonts.sansMed },
  caret: { color: colors.textFaint, fontSize: 12, marginLeft: 6 },
  panel: {
    position: 'absolute',
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.borderInput,
    borderRadius: radius.md,
    overflow: 'hidden',
  },
  optionsList: { maxHeight: 260 },
  option: {
    paddingHorizontal: 13,
    paddingVertical: 11,
    borderBottomWidth: 1,
    borderBottomColor: colors.divider,
  },
  optionText: { color: colors.textMuted, fontSize: 13, fontFamily: fonts.sansMed },
  optionTextActive: { color: colors.primary, fontFamily: fonts.sansSemi },
  doneBtn: { paddingVertical: 11, alignItems: 'center', backgroundColor: colors.surfaceAlt },
  doneBtnText: { color: colors.primary, fontSize: 13, fontFamily: fonts.sansSemi },
});
