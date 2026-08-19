import { useEffect, useState } from 'react';
import { View, Text, TextInput, Pressable, StyleSheet, ActivityIndicator } from 'react-native';
import { useAuth } from '../../context/AuthContext';
import { loginRequest } from '../../services/auth';
import { getLastUsername } from '../../services/session';
import { colors, fonts } from '../../constants/theme';
import { AuthShell, authStyles } from '../../components/auth/AuthShell';

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
    <AuthShell
      modo="login"
      subtitle="Control de cargas de combustible para flotas y maquinaria vial."
    >
      <View style={styles.field}>
        <Text style={authStyles.fieldLabel}>Usuario</Text>
        <TextInput
          style={authStyles.input}
          value={username}
          onChangeText={setUsername}
          autoCapitalize="none"
          placeholder="Usuario"
          placeholderTextColor={colors.textDim}
          editable={!loading}
          accessibilityLabel="Usuario"
        />
      </View>

      <View style={styles.field}>
        <Text style={authStyles.fieldLabel}>Contraseña</Text>
        <TextInput
          style={authStyles.input}
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          placeholder="••••••••"
          placeholderTextColor={colors.textDim}
          editable={!loading}
          accessibilityLabel="Contraseña"
        />
      </View>

      {error && <Text style={authStyles.error}>{error}</Text>}

      <Pressable
        style={[authStyles.cta, loading && authStyles.ctaDisabled]}
        onPress={handleLogin}
        disabled={loading}
        accessibilityRole="button"
        accessibilityLabel="Ingresar"
        accessibilityState={{ disabled: loading, busy: loading }}
      >
        {loading ? (
          <ActivityIndicator color={colors.bgDeep} />
        ) : (
          <Text style={authStyles.ctaText}>Ingresar</Text>
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
        <View style={styles.hintIcon} accessibilityElementsHidden importantForAccessibility="no">
          <Text style={{ fontSize: 16 }}>🪖</Text>
        </View>
        <View style={{ flex: 1 }}>
          <Text style={styles.hintText}>
            Ingresá con las credenciales que te asignó el administrador de tu obra.
          </Text>
        </View>
      </View>
    </AuthShell>
  );
}

const styles = StyleSheet.create({
  // Solo lo propio del login. Lo compartido con el registro vive en authStyles.
  field: { marginBottom: 14 },
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
    backgroundColor: colors.hintBg,
    alignItems: 'center',
    justifyContent: 'center',
  },
  hintText: { fontSize: 11.5, color: colors.textMuted, lineHeight: 17, fontFamily: fonts.sans },
});
