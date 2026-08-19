import { useState, ReactNode } from 'react';
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
  KeyboardTypeOptions,
  Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { useAuth } from '../../context/AuthContext';
import { registerRequest } from '../../services/auth';
import { colors, fonts, radius } from '../../constants/theme';
import { Logo, Wordmark } from '../../components/ui';

/**
 * Pantalla de Registro (look FuelTrack sobre la autenticación JWT real).
 *
 * Refleja exactamente el RegisterRequest del backend:
 *   { nombre, apellido, documento, password, telefono }
 *
 * No hay selector de rol: el registro siempre crea un EMPLEADO. Las altas de
 * administrador se hacen fuera de la app (ver docs/DEPLOYMENT.md).
 *
 * El `username` con el que el usuario después inicia sesión lo genera el
 * backend a partir de nombre + apellido: no se pide acá. Tras un registro
 * exitoso mostramos ese username en un Alert (es la única vez que se ve)
 * y recién ahí hacemos auto-login: el backend ya devuelve un token en el
 * AuthResponse, así que persistimos la sesión y el _layout redirige.
 */
export default function RegisterScreen() {
  const { login } = useAuth();
  const router = useRouter();

  const [nombre, setNombre] = useState('');
  const [apellido, setApellido] = useState('');
  const [documento, setDocumento] = useState('');
  const [password, setPassword] = useState('');

  // Teléfono
  const [codigoArea, setCodigoArea] = useState('');
  const [telefonoNumero, setTelefonoNumero] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleRegister = async () => {
    setError(null);

    if (
      !nombre.trim() ||
      !apellido.trim() ||
      !documento.trim() ||
      !password ||
      !codigoArea.trim() ||
      !telefonoNumero.trim()
    ) {
      setError('Completá todos los campos.');
      return;
    }

    try {
      setLoading(true);
      const res = await registerRequest({
        nombre: nombre.trim(),
        apellido: apellido.trim(),
        documento: documento.trim(),
        password,
        telefono: {
          codigoArea: codigoArea.trim(),
          numero: telefonoNumero.trim(),
        },
      });
      // A PARTIR DE ACÁ LA CUENTA YA EXISTE en el backend. Ningún fallo
      // posterior puede reportarse como "no se pudo crear la cuenta": eso
      // mandaría al usuario a reintentar con un documento que ya está tomado,
      // que es exactamente el pozo del que trata este arreglo.
      //
      // El auto-login va ANTES del Alert, no adentro de su onPress: en Android
      // el diálogo es cancelable (botón atrás o tocar afuera) y ahí onPress
      // NUNCA corre. Atada a ese callback, la sesión se perdía con la cuenta ya
      // creada y el token descartado — y como el username lo genera el servidor
      // y este Alert era la única vez que se mostraba, el usuario quedaba sin
      // poder entrar ni volver a registrarse.
      //
      // Logueado, el username además queda visible en Perfil, así que deja de
      // ser un dato de una sola oportunidad.
      try {
        await login(res); // auto-login + redirección por rol
      } catch {
        // Si no se pudo persistir la sesión, el Alert de abajo sigue siendo la
        // vía para que se lleve su username y entre a mano.
      }
      Alert.alert(
        '¡Registro exitoso!',
        `Tu usuario es: ${res.username}\nGuardalo para iniciar sesión.`,
        [{ text: 'Continuar' }],
        { cancelable: false },
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo crear la cuenta.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          <View style={styles.brandRow}>
            <Logo size={46} />
            <Wordmark size={24} />
          </View>
          <Text style={styles.subtitle}>Creá tu cuenta para empezar a registrar cargas.</Text>

          <View style={styles.tabs}>
            <Pressable style={styles.tab} onPress={() => router.replace('/(auth)/login')}>
              <Text style={styles.tabText}>Iniciar sesión</Text>
            </Pressable>
            <View style={[styles.tab, styles.tabActive]}>
              <Text style={[styles.tabText, styles.tabTextActive]}>Registrarse</Text>
            </View>
          </View>

          <Field label="Nombre">
            <Input value={nombre} onChangeText={setNombre} editable={!loading} placeholder="Juan" />
          </Field>
          <Field label="Apellido">
            <Input value={apellido} onChangeText={setApellido} editable={!loading} placeholder="Pérez" />
          </Field>
          <Field label="Documento">
            <Input value={documento} onChangeText={setDocumento} editable={!loading} keyboardType="number-pad" placeholder="30123456" />
          </Field>
          <Field label="Contraseña">
            <Input value={password} onChangeText={setPassword} editable={!loading} secureTextEntry placeholder="••••••••" />
          </Field>

          <Text style={styles.section}>Teléfono</Text>
          <Field label="Código de área">
            <Input value={codigoArea} onChangeText={setCodigoArea} editable={!loading} keyboardType="number-pad" />
          </Field>
          <Field label="Número">
            <Input value={telefonoNumero} onChangeText={setTelefonoNumero} editable={!loading} keyboardType="number-pad" />
          </Field>

          {error && <Text style={styles.error}>{error}</Text>}

          <Pressable
            style={[styles.cta, loading && styles.ctaDisabled]}
            onPress={handleRegister}
            disabled={loading}
          >
            {loading ? (
              <ActivityIndicator color={colors.bgDeep} />
            ) : (
              <Text style={styles.ctaText}>Crear cuenta</Text>
            )}
          </Pressable>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <View style={{ marginBottom: 12 }}>
      <Text style={styles.fieldLabel}>{label}</Text>
      {children}
    </View>
  );
}

function Input(props: {
  value: string;
  onChangeText: (t: string) => void;
  editable?: boolean;
  placeholder?: string;
  secureTextEntry?: boolean;
  autoCapitalize?: 'none' | 'sentences' | 'words' | 'characters';
  keyboardType?: KeyboardTypeOptions;
}) {
  return (
    <TextInput
      style={styles.input}
      placeholderTextColor={colors.textDim}
      {...props}
    />
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  flex: { flex: 1 },
  content: { padding: 26 },
  brandRow: { flexDirection: 'row', alignItems: 'center', gap: 11, marginBottom: 8 },
  subtitle: { fontSize: 13, color: colors.textFaint, marginBottom: 26, lineHeight: 20, fontFamily: fonts.sans },
  tabs: { flexDirection: 'row', backgroundColor: colors.surface, borderRadius: radius.md, padding: 4, marginBottom: 22 },
  tab: { flex: 1, paddingVertical: 9, borderRadius: 8, alignItems: 'center' },
  tabActive: { backgroundColor: colors.primary },
  tabText: { fontSize: 13, fontFamily: fonts.sansSemi, color: colors.textMuted },
  tabTextActive: { color: colors.bgDeep },
  section: {
    fontFamily: fonts.display,
    fontSize: 13,
    letterSpacing: 1.4,
    textTransform: 'uppercase',
    color: colors.primary,
    marginTop: 10,
    marginBottom: 12,
  },
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
