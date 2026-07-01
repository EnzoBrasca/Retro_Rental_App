import { Redirect } from 'expo-router';
import { useAuth } from '../context/AuthContext';
import { View, ActivityIndicator } from 'react-native';

/**
 * Pantalla de entrada (ruta '/').
 *
 * No muestra UI propia: decide a dónde mandar al usuario según su sesión.
 * El _layout raíz también guarda la navegación; tener la decisión acá además
 * hace que el primer frame ya apunte al destino correcto (sin parpadeo).
 */
export default function Index() {
  const { user, isLoading } = useAuth();

  // Todavía leyendo el storage: spinner, no decidimos aún.
  if (isLoading) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  if (!user) return <Redirect href="/(auth)/login" />;
  if (user.rol === 'EMPLEADO') return <Redirect href="/(empleado)" />;
  return <Redirect href="/(administrador)" />;
}
