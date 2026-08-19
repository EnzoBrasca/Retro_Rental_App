import { useState, ReactNode } from 'react';
import {
  View,
  Text,
  TextInput,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  KeyboardTypeOptions,
  Alert,
} from 'react-native';
import { useAuth } from '../../context/AuthContext';
import { registerRequest } from '../../services/auth';
import { documentoInvalido, passwordInvalida } from '../../constants/validation';
import { colors, fonts } from '../../constants/theme';
import { AuthShell, authStyles } from '../../components/auth/AuthShell';

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

    // Las mismas reglas que el alta del admin. Este camino es el que usa
    // cualquiera con la URL de la API y era el MENOS exigente de los dos:
    // aceptaba un documento "1" y una contraseña de un caracter, y el rechazo
    // llegaba recién del backend, después del round-trip.
    const errorDocumento = documentoInvalido(documento);
    if (errorDocumento) return setError(errorDocumento);
    const errorPassword = passwordInvalida(password);
    if (errorPassword) return setError(errorPassword);

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
    <AuthShell modo="register" subtitle="Creá tu cuenta para empezar a registrar cargas.">
      <Field label="Nombre">
        <Input value={nombre} onChangeText={setNombre} editable={!loading} placeholder="Juan" />
      </Field>
      <Field label="Apellido">
        <Input
          value={apellido}
          onChangeText={setApellido}
          editable={!loading}
          placeholder="Pérez"
        />
      </Field>
      <Field label="Documento">
        <Input
          value={documento}
          onChangeText={setDocumento}
          editable={!loading}
          keyboardType="number-pad"
          placeholder="30123456"
        />
      </Field>
      <Field label="Contraseña">
        <Input
          value={password}
          onChangeText={setPassword}
          editable={!loading}
          secureTextEntry
          placeholder="••••••••"
        />
      </Field>

      <Text style={styles.section}>Teléfono</Text>
      <Field label="Código de área">
        <Input
          value={codigoArea}
          onChangeText={setCodigoArea}
          editable={!loading}
          keyboardType="number-pad"
        />
      </Field>
      <Field label="Número">
        <Input
          value={telefonoNumero}
          onChangeText={setTelefonoNumero}
          editable={!loading}
          keyboardType="number-pad"
        />
      </Field>

      {error && <Text style={authStyles.error}>{error}</Text>}

      <Pressable
        style={[authStyles.cta, loading && authStyles.ctaDisabled]}
        onPress={handleRegister}
        disabled={loading}
      >
        {loading ? (
          <ActivityIndicator color={colors.bgDeep} />
        ) : (
          <Text style={authStyles.ctaText}>Crear cuenta</Text>
        )}
      </Pressable>
    </AuthShell>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <View style={{ marginBottom: 12 }}>
      <Text style={authStyles.fieldLabel}>{label}</Text>
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
  return <TextInput style={authStyles.input} placeholderTextColor={colors.textDim} {...props} />;
}

const styles = StyleSheet.create({
  // Solo lo propio del registro. Lo compartido con el login vive en authStyles.
  section: {
    fontSize: 11,
    letterSpacing: 1.2,
    textTransform: 'uppercase',
    color: colors.textFaint,
    marginTop: 6,
    marginBottom: 10,
    fontFamily: fonts.sans,
  },
});
