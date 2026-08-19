import { ReactNode, useRef, useState } from 'react';
import {
  Dimensions,
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

/** Margen mínimo entre el panel y el borde de la pantalla. */
const MARGEN = 12;
/** Separación entre el trigger y el panel. */
const GAP = 4;
/** Techo del panel: más alto que esto se vuelve incómodo aunque haya lugar. */
const MAX_ALTO = 260;
/** Piso: por debajo de esto el panel no sirve, mejor que desborde un poco. */
const MIN_ALTO = 120;

/**
 * Decide si el panel abre hacia abajo o hacia arriba, y cuánto alto puede tomar.
 *
 * Antes se posicionaba siempre debajo del trigger, sin comparar contra el alto
 * de la pantalla: con el trigger en la mitad inferior, el panel se dibujaba
 * fuera del viewport y las últimas opciones no se podían tocar. Con el filtro de
 * personas de Analítica en una pantalla chica, pasa.
 */
function PanelPosicionado({
  trigger,
  children,
}: {
  trigger: LayoutRectangle;
  children: ReactNode;
}) {
  const alto = Dimensions.get('window').height;
  const espacioAbajo = alto - (trigger.y + trigger.height) - GAP - MARGEN;
  const espacioArriba = trigger.y - GAP - MARGEN;
  // Se abre hacia abajo salvo que arriba entre claramente mejor. El alto se
  // acota al espacio REAL disponible, con el 260 original como techo.
  const haciaAbajo = espacioAbajo >= espacioArriba;
  const disponible = Math.max(haciaAbajo ? espacioAbajo : espacioArriba, MIN_ALTO);

  return (
    <View
      style={[
        styles.panel,
        {
          left: trigger.x,
          width: trigger.width,
          maxHeight: Math.min(MAX_ALTO, disponible),
          ...(haciaAbajo
            ? { top: trigger.y + trigger.height + GAP }
            : { bottom: alto - trigger.y + GAP }),
        },
      ]}
    >
      {children}
    </View>
  );
}

/**
 * Desplegable genérico (single o multi select) que sirve de filtro: un
 * trigger tipo "input" que muestra la selección actual, y un panel que se
 * abre mediante un Modal transparente, hacia abajo o hacia arriba según dónde
 * haya lugar. Se cierra al tocar fuera o, en modo single, al elegir una opción.
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
      <Pressable
        ref={triggerRef}
        style={styles.trigger}
        onPress={openDropdown}
        accessibilityRole="button"
        // El contenido visible del control es un texto truncado más un "▾". El
        // label dice qué filtro es y qué tiene puesto ahora mismo.
        accessibilityLabel={`Filtrar por ${props.label}, actualmente ${summary}`}
        accessibilityState={{ expanded: open }}
      >
        <Text style={styles.triggerText} numberOfLines={1}>
          {summary}
        </Text>
        <Text style={styles.caret}>▾</Text>
      </Pressable>

      <Modal visible={open} transparent animationType="fade" onRequestClose={closeDropdown}>
        <Pressable style={StyleSheet.absoluteFill} onPress={closeDropdown} />
        {triggerLayout && (
          <PanelPosicionado trigger={triggerLayout}>
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
          </PanelPosicionado>
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
  // Sin maxHeight propio: el alto lo acota PanelPosicionado contra el viewport.
  optionsList: { flexShrink: 1 },
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
