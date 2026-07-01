import { Stack } from 'expo-router';

/**
 * Layout del grupo (empleado): navegación interna de la sección del empleado.
 * Solo declaramos las pantallas que existen para no generar warnings de
 * Expo Router. A medida que sumes pantallas (ej. history), agregalas acá.
 */
export default function EmpleadoLayout() {
  return (
    <Stack>
      <Stack.Screen name="index" options={{ title: 'Registrar Carga' }} />
    </Stack>
  );
}
