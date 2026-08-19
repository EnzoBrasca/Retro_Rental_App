import { Redirect } from 'expo-router';
import { useAuth } from '../context/AuthContext';
import { View, ActivityIndicator } from 'react-native';
import { rutaSegunSesion } from '../services/rutas';

/**
 * Pantalla de entrada (ruta '/').
 *
 * No muestra UI propia: decide a dónde mandar al usuario según su sesión.
 * El _layout raíz también guarda la navegación; tener la decisión acá además
 * hace que el primer frame ya apunte al destino correcto (sin parpadeo).
 *
 * La REGLA de a dónde va cada rol vive en `services/rutas.ts`, una sola vez,
 * aunque se evalúe también en el layout.
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

  return <Redirect href={rutaSegunSesion(user)} />;
}
