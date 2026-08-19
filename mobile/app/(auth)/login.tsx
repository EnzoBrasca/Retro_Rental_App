import { useEffect, useState } from 'react';
import {
  View,
  Text,
  TextInput,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { useAuth } from '../../context/AuthContext';
import { loginRequest } from '../../services/auth';
import { getLastUsername } from '../../services/session';
import { colors, fonts, radius } from '../../constants/theme';
import { Logo, Wordmark } from '../../components/ui';

/**
 * Pantalla de Login (look FuelTrack sobre la autenticación JWT real).
 *
 * Flujo:
 *  1) El usuario escribe username + password.
 *  2) `loginRequest` pega a POST /auth/login.
 *  3) Si responde OK, `auth.login(res)` persiste la sesión.
 *  4) NO navegamos a mano: al cambiar `user`, el guardia del _layout raíz
 *     redirige automáticamente al grupo de rol que corresponda.
 *
 * El campo de usuario arranca con el del último que inició sesión en este
 * dispositivo (venga de una sesión expirada o de un logout manual), así el
 * reingreso es evidente y no se confunde una cuenta con otra.
 */
export default function LoginScreen() {
  const { login } = useAuth();
  const router = useRouter();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Solo prellenamos si el usuario todavía no escribió nada: la lectura del
    // storage es asíncrona y no debe pisar lo que ya tipeó.
    getLastUsername().then((last) => {
      if (last) setUsername((current) => (current === '' ? last : current));
    });
  }, []);

  const handleLogin = async () => {
    setError(null);

    if (!username.trim() || !password) {
      setError('Completá usuario y contraseña.');
      return;
    }

    try {
      setLoading(true);
      const res = await loginRequest({ username: username.trim(), password });
      await login(res); // dispara la redirección por rol vía el _layout
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo iniciar sesión.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          <View style={styles.brandRow}>
            <Logo size={46} />
            <Wordmark size={24} />
          </View>
          <Text style={styles.subtitle}>
            Control de cargas de combustible para flotas y maquinaria vial.
          </Text>

          <View style={styles.tabs}>
            <View style={[styles.tab, styles.tabActive]}>
              <Text style={[styles.tabText, styles.tabTextActive]}>Iniciar sesión</Text>
            </View>
            <Pressable style={styles.tab} onPress={() => router.replace('/(auth)/register')}>
              <Text style={styles.tabText}>Registrarse</Text>
            </Pressable>
          </View>

          <View style={styles.field}>
            <Text style={styles.fieldLabel}>Usuario</Text>
            <TextInput
              style={styles.input}
              value={username}
              onChangeText={setUsername}
              autoCapitalize="none"
              placeholder="Usuario"
              placeholderTextColor={colors.textDim}
              editable={!loading}
            />
          </View>

          <View style={styles.field}>
            <Text style={styles.fieldLabel}>Contraseña</Text>
            <TextInput
              style={styles.input}
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              placeholder="••••••••"
              placeholderTextColor={colors.textDim}
              editable={!loading}
            />
          </View>

          {error && <Text style={styles.error}>{error}</Text>}

          <Pressable
            style={[styles.cta, loading && styles.ctaDisabled]}
            onPress={handleLogin}
            disabled={loading}
          >
            {loading ? (
              <ActivityIndicator color={colors.bgDeep} />
            ) : (
              <Text style={styles.ctaText}>Ingresar</Text>
            )}
          </Pressable>
          {/* Antes decía "¿Olvidaste tu contraseña?": un Text suelto, sin
              Pressable y sin destino, justo donde va ese link en todos los
              logins del mundo. El usuario lo tocaba y no pasaba nada. No hay
              flujo de recuperación ni en el backend ni acá, así que ahora dice
              lo único que sí se puede hacer. */}
          <Text style={styles.forgot}>
            Si no podés ingresar, pedile al administrador que restablezca tu contraseña.
          </Text>

          <View style={styles.hint}>
            <View style={styles.hintIcon}>
              <Text style={{ fontSize: 16 }}>🪖</Text>
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.hintText}>
                Ingresá con las credenciales que te asignó el administrador de tu obra.
              </Text>
            </View>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
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
  field: { marginBottom: 14 },
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
  forgot: {
    textAlign: 'center',
    fontSize: 12.5,
    color: colors.textDim,
    marginTop: 16,
    fontFamily: fonts.sans,
  },
  hint: {
    marginTop: 'auto',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: colors.surface,
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
});
