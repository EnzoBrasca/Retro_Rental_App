import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, fonts } from '../../constants/theme';
import { TUTORIAL_STEPS } from '../../data/mock';

interface Props {
  visible: boolean;
  step: number;
  onNext: () => void;
  onClose: () => void;
}

export function TutorialOverlay({ visible, step, onNext, onClose }: Props) {
  const data = TUTORIAL_STEPS[step];
  if (!data) return null;

  const isLast = step === TUTORIAL_STEPS.length - 1;

  return (
    <Modal visible={visible} transparent animationType="fade">
      <View style={styles.overlay}>
        <View style={styles.card}>
          <View style={styles.header}>
            <Text style={styles.stepLabel}>{data.label}</Text>
            <Pressable onPress={onClose} hitSlop={10}>
              <Text style={styles.skip}>Omitir ✕</Text>
            </Pressable>
          </View>

          <Text style={styles.title}>{data.title}</Text>
          <Text style={styles.text}>{data.text}</Text>

          <View style={styles.footer}>
            <View style={styles.dots}>
              {TUTORIAL_STEPS.map((_, i) => (
                <View key={i} style={[styles.dot, i === step && styles.dotActive]} />
              ))}
            </View>

            <Pressable style={styles.next} onPress={onNext}>
              <Text style={styles.nextText}>{isLast ? 'Empezar' : 'Siguiente'}</Text>
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.85)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  card: {
    backgroundColor: '#22262b',
    borderWidth: 1,
    borderColor: colors.primary,
    borderRadius: 15,
    padding: 24,
    width: '100%',
    maxWidth: 400,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.5,
    shadowRadius: 20,
    elevation: 10,
  },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
  stepLabel: {
    fontSize: 10,
    letterSpacing: 2,
    textTransform: 'uppercase',
    color: colors.primary,
    fontFamily: fonts.sansSemi,
  },
  skip: { fontSize: 11, color: colors.textFaint, fontFamily: fonts.sans },
  title: { fontFamily: fonts.display, fontSize: 22, color: colors.text, marginBottom: 8 },
  text: { fontSize: 14, color: colors.textMuted, lineHeight: 22, marginBottom: 24, fontFamily: fonts.sans },
  footer: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  dots: { flexDirection: 'row', gap: 6, alignItems: 'center' },
  dot: { width: 6, height: 6, borderRadius: 4, backgroundColor: '#4d525a' },
  dotActive: { width: 18, backgroundColor: colors.primary },
  next: {
    backgroundColor: colors.primary,
    borderRadius: 9,
    paddingHorizontal: 20,
    paddingVertical: 10,
  },
  nextText: {
    fontFamily: fonts.display,
    fontSize: 14,
    letterSpacing: 1,
    textTransform: 'uppercase',
    color: colors.bgDeep,
  },
});
