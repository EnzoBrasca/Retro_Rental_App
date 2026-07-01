import { Stack } from 'expo-router';

/**
 * Layout del grupo (administrador): navegación interna de la sección del admin.
 * Solo declaramos las pantallas que existen para no generar warnings de
 * Expo Router. A medida que sumes pantallas (ej. tickets), agregalas acá.
 */
export default function AdministradorLayout() {
  return (
    <Stack>
      <Stack.Screen name="index" options={{ title: 'Dashboard' }} />
    </Stack>
  );
}
