import { ReactNode } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { colors, fonts, radius } from '../../constants/theme';
import { Logo, Wordmark } from '../ui';

/**
 * Cascarón compartido de Login y Registro: marca, subtítulo y el conmutador
 * entre las dos pantallas.
 *
 * POR QUÉ EXISTE. Las dos pantallas tenían 15 claves de estilo IDÉNTICAS byte a
 * byte, más el mismo bloque de marca y el mismo conmutador escritos dos veces.
 * Un cambio de diseño obligaba a tocar los dos archivos, y el que se olvidara
 * quedaba distinto sin que nada avisara: la deriva ya había empezado —login
 * tenía un color de fondo en el bloque de ayuda que register no.
 */
export function AuthShell({
  modo,
  subtitle,
  children,
}: {
  modo: 'login' | 'register';
  subtitle: string;
  children: ReactNode;
}) {
  const router = useRouter();
  const enLogin = modo === 'login';

  return (
    <SafeAreaView style={authStyles.safe} edges={['top', 'bottom']}>
      <KeyboardAvoidingView
        style={authStyles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView contentContainerStyle={authStyles.content} keyboardShouldPersistTaps="handled">
          <View style={authStyles.brandRow}>
            <Logo size={46} />
            <Wordmark size={24} />
          </View>
          <Text style={authStyles.subtitle}>{subtitle}</Text>

          <View style={authStyles.tabs}>
            <Tab
              label="Iniciar sesión"
              activa={enLogin}
              onPress={() => router.replace('/(auth)/login')}
            />
            <Tab
              label="Registrarse"
              activa={!enLogin}
              onPress={() => router.replace('/(auth)/register')}
            />
          </View>

          {children}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

/** La pestaña activa no es tocable: ya estás ahí. */
function Tab({ label, activa, onPress }: { label: string; activa: boolean; onPress: () => void }) {
  if (activa) {
    return (
      <View style={[authStyles.tab, authStyles.tabActive]}>
        <Text style={[authStyles.tabText, authStyles.tabTextActive]}>{label}</Text>
      </View>
    );
  }
  return (
    <Pressable
      style={authStyles.tab}
      onPress={onPress}
      accessibilityRole="tab"
      accessibilityState={{ selected: false }}
      accessibilityLabel={label}
    >
      <Text style={authStyles.tabText}>{label}</Text>
    </Pressable>
  );
}

/**
 * Estilos que comparten las dos pantallas de auth. Viven acá para que exista una
 * sola definición de cada uno; lo propio de cada pantalla queda en su archivo.
 */
export const authStyles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  flex: { flex: 1 },
  content: { padding: 26, flexGrow: 1 },
  brandRow: { flexDirection: 'row', alignItems: 'center', gap: 11, marginBottom: 8 },
  subtitle: {
    fontSize: 13,
    color: colors.textFaint,
    marginBottom: 26,
    lineHeight: 20,
    fontFamily: fonts.sans,
  },
  tabs: {
    flexDirection: 'row',
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    padding: 4,
    marginBottom: 22,
  },
  tab: { flex: 1, paddingVertical: 9, borderRadius: 8, alignItems: 'center' },
  tabActive: { backgroundColor: colors.primary },
  tabText: { fontSize: 13, fontFamily: fonts.sansSemi, color: colors.textMuted },
  tabTextActive: { color: colors.bgDeep },
  fieldLabel: {
    fontSize: 11,
    letterSpacing: 1.2,
    textTransform: 'uppercase',
    color: colors.textFaint,
    marginBottom: 7,
    fontFamily: fonts.sans,
  },
  input: {
    height: 48,
    backgroundColor: colors.surfaceInput,
    borderWidth: 1,
    borderColor: colors.borderInput,
    borderRadius: 10,
    paddingHorizontal: 14,
    color: colors.text,
    fontSize: 15,
    fontFamily: fonts.sans,
  },
  error: { color: colors.danger, fontSize: 13, marginBottom: 12, fontFamily: fonts.sans },
  cta: {
    height: 52,
    backgroundColor: colors.primary,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 6,
  },
  ctaDisabled: { opacity: 0.6 },
  ctaText: {
    fontFamily: fonts.display,
    fontSize: 16,
    letterSpacing: 1.5,
    textTransform: 'uppercase',
    color: colors.bgDeep,
  },
});
