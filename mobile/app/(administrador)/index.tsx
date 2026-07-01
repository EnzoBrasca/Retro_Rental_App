import { View, Text, Pressable, StyleSheet } from 'react-native';
import { useAuth } from '../../context/AuthContext';

/**
 * Home del Administrador (placeholder).
 *
 * A esta pantalla solo llega un usuario con rol ADMINISTRADOR: el guardia del
 * _layout raíz redirige a cualquier otro rol fuera de este grupo.
 * El botón de logout limpia la sesión y dispara la vuelta al login.
 */
export default function AdministradorHome() {
  const { user, logout } = useAuth();

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Panel del Administrador</Text>
      <Text style={styles.subtitle}>
        Hola {user?.nombre} {user?.apellido}
      </Text>

      <Pressable style={styles.button} onPress={logout}>
        <Text style={styles.buttonText}>Cerrar sesión</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24 },
  title: { fontSize: 24, fontWeight: 'bold', marginBottom: 8 },
  subtitle: { fontSize: 16, color: '#666', marginBottom: 32 },
  button: {
    backgroundColor: '#d11',
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 24,
  },
  buttonText: { color: '#fff', fontWeight: '600' },
});
