import { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  ScrollView,
} from 'react-native';
import { Link } from 'expo-router';
import { useAuth } from '../../context/AuthContext';
import { registerRequest, Rol } from '../../services/auth';

/**
 * Pantalla de Registro.
 *
 * Refleja exactamente el RegisterRequest del backend:
 *   { nombre, apellido, documento, email, password, rol }
 *
 * El rol se elige con un selector de dos opciones (EMPLEADO / ADMINISTRADOR)
 * en lugar de un Picker, para no sumar dependencias nativas nuevas.
 *
 * Tras un registro exitoso hacemos auto-login: el backend ya devuelve un token
 * en el AuthResponse, así que persistimos la sesión y el _layout redirige.
 */
export default function RegisterScreen() {
  const { login } = useAuth();

  const [nombre, setNombre] = useState('');
  const [apellido, setApellido] = useState('');
  const [documento, setDocumento] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [rol, setRol] = useState<Rol>('EMPLEADO');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleRegister = async () => {
    setError(null);

    if (!nombre.trim() || !apellido.trim() || !documento.trim() || !email.trim() || !password) {
      setError('Completá todos los campos.');
      return;
    }

    try {
      setLoading(true);
      const res = await registerRequest({
        nombre: nombre.trim(),
        apellido: apellido.trim(),
        documento: documento.trim(),
        email: email.trim(),
        password,
        rol,
      });
      await login(res); // auto-login + redirección por rol
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo crear la cuenta.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
    >
      <Text style={styles.title}>Crear cuenta</Text>

      <TextInput
        style={styles.input}
        placeholder="Nombre"
        value={nombre}
        onChangeText={setNombre}
        editable={!loading}
      />
      <TextInput
        style={styles.input}
        placeholder="Apellido"
        value={apellido}
        onChangeText={setApellido}
        editable={!loading}
      />
      <TextInput
        style={styles.input}
        placeholder="Documento"
        keyboardType="number-pad"
        value={documento}
        onChangeText={setDocumento}
        editable={!loading}
      />
      <TextInput
        style={styles.input}
        placeholder="Email"
        autoCapitalize="none"
        keyboardType="email-address"
        value={email}
        onChangeText={setEmail}
        editable={!loading}
      />
      <TextInput
        style={styles.input}
        placeholder="Contraseña"
        secureTextEntry
        value={password}
        onChangeText={setPassword}
        editable={!loading}
      />

      <Text style={styles.label}>Rol</Text>
      <View style={styles.roleRow}>
        {(['EMPLEADO', 'ADMINISTRADOR'] as Rol[]).map((opcion) => {
          const selected = rol === opcion;
          return (
            <Pressable
              key={opcion}
              style={[styles.roleOption, selected && styles.roleOptionSelected]}
              onPress={() => setRol(opcion)}
              disabled={loading}
            >
              <Text style={[styles.roleText, selected && styles.roleTextSelected]}>
                {opcion === 'EMPLEADO' ? 'Empleado' : 'Administrador'}
              </Text>
            </Pressable>
          );
        })}
      </View>

      {error && <Text style={styles.error}>{error}</Text>}

      <Pressable
        style={[styles.button, loading && styles.buttonDisabled]}
        onPress={handleRegister}
        disabled={loading}
      >
        {loading ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.buttonText}>Registrarme</Text>
        )}
      </Pressable>

      <View style={styles.footer}>
        <Text style={styles.footerText}>¿Ya tenés cuenta? </Text>
        <Link href="/(auth)/login" style={styles.link}>
          Iniciá sesión
        </Link>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  content: {
    padding: 24,
    paddingTop: 64,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    marginBottom: 24,
  },
  input: {
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 8,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 16,
    marginBottom: 12,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: '#444',
    marginTop: 4,
    marginBottom: 8,
  },
  roleRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 16,
  },
  roleOption: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 8,
    paddingVertical: 12,
    alignItems: 'center',
  },
  roleOptionSelected: {
    borderColor: '#1e90ff',
    backgroundColor: '#e8f3ff',
  },
  roleText: {
    fontSize: 15,
    color: '#444',
  },
  roleTextSelected: {
    color: '#1e90ff',
    fontWeight: '600',
  },
  button: {
    backgroundColor: '#1e90ff',
    borderRadius: 8,
    paddingVertical: 14,
    alignItems: 'center',
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  error: {
    color: '#d11',
    marginBottom: 12,
    textAlign: 'center',
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginTop: 24,
  },
  footerText: {
    color: '#666',
  },
  link: {
    color: '#1e90ff',
    fontWeight: '600',
  },
});
