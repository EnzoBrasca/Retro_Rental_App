import React, { useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAuth } from '../context/AuthContext';
import { colors, fonts, radius } from '../theme';
import { Logo, Wordmark } from '../components/ui';

export function AuthScreen() {
  const { login } = useAuth();
  const [tab, setTab] = useState<'login' | 'register'>('login');
  const [name, setName] = useState('Nuevo Operario');
  const [email, setEmail] = useState('juan.perez@vialsur.com');
  const [password, setPassword] = useState('123456789');
  const [error, setError] = useState<string | null>(null);

  const isRegister = tab === 'register';
  const cta = isRegister ? 'Crear cuenta' : 'Ingresar';

  const submit = () => {
    const err = login(email, password);
    setError(err);
  };

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          <View style={styles.brandRow}>
            <Logo size={46} />
            <Wordmark size={24} />
          </View>
          <Text style={styles.subtitle}>Control de cargas de combustible para flotas y maquinaria vial.</Text>

          <View style={styles.tabs}>
            <TabButton label="Iniciar sesión" active={!isRegister} onPress={() => setTab('login')} />
            <TabButton label="Registrarse" active={isRegister} onPress={() => setTab('register')} />
          </View>

          {isRegister && (
            <Field label="Nombre completo">
              <TextInput style={styles.input} value={name} onChangeText={setName} placeholderTextColor={colors.textDim} />
            </Field>
          )}

          <Field label="Correo / Usuario">
            <TextInput
              style={styles.input}
              value={email}
              onChangeText={setEmail}
              autoCapitalize="none"
              keyboardType="email-address"
              placeholderTextColor={colors.textDim}
            />
          </Field>

          <Field label="Contraseña">
            <TextInput
              style={styles.input}
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              placeholderTextColor={colors.textDim}
            />
          </Field>

          {error && <Text style={styles.error}>{error}</Text>}

          <Pressable style={styles.cta} onPress={submit}>
            <Text style={styles.ctaText}>{cta}</Text>
          </Pressable>
          <Text style={styles.forgot}>¿Olvidaste tu contraseña?</Text>

          <View style={styles.hint}>
            <View style={styles.hintIcon}>
              <Text style={{ fontSize: 16 }}>🪖</Text>
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.hintText}>
                Ingresá con las credenciales que te asignó el administrador de tu obra.
              </Text>
              <Text style={styles.demo}>Operario: juan.perez@vialsur.com / 123456789</Text>
              <Text style={styles.demo}>Admin: admin@vialsur.com / admin123</Text>
            </View>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function TabButton({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  return (
    <Pressable style={[styles.tab, active && styles.tabActive]} onPress={onPress}>
      <Text style={[styles.tabText, active && styles.tabTextActive]}>{label}</Text>
    </Pressable>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <View style={{ marginBottom: 14 }}>
      <Text style={styles.fieldLabel}>{label}</Text>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  flex: { flex: 1 },
  content: { padding: 26, flexGrow: 1 },
  brandRow: { flexDirection: 'row', alignItems: 'center', gap: 11, marginBottom: 8 },
  subtitle: { fontSize: 13, color: colors.textFaint, marginBottom: 26, lineHeight: 20, fontFamily: fonts.sans },
  tabs: { flexDirection: 'row', backgroundColor: '#1F2226', borderRadius: radius.md, padding: 4, marginBottom: 22 },
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
  ctaText: {
    fontFamily: fonts.display,
    fontSize: 16,
    letterSpacing: 1.5,
    textTransform: 'uppercase',
    color: colors.bgDeep,
  },
  forgot: { textAlign: 'center', fontSize: 12.5, color: colors.textDim, marginTop: 16, fontFamily: fonts.sans },
  hint: {
    marginTop: 'auto',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: '#1F2226',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    padding: 13,
  },
  hintIcon: {
    width: 34,
    height: 34,
    borderRadius: 8,
    backgroundColor: '#242a30',
    alignItems: 'center',
    justifyContent: 'center',
  },
  hintText: { fontSize: 11.5, color: colors.textMuted, lineHeight: 17, fontFamily: fonts.sans },
  demo: { fontSize: 11, color: colors.primary, marginTop: 4, fontFamily: fonts.mono },
});
