import { Stack } from 'expo-router';

/**
 * Layout del grupo (administrador): navegación interna de la sección del admin.
 * Sin header nativo: el dashboard ya trae su propio encabezado
 * ("PANEL DE CONTROL / ADMINISTRADOR") como parte del diseño oscuro.
 */
export default function AdministradorLayout() {
  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Screen name="index" />
      <Stack.Screen name="perfil" />
    </Stack>
  );
}
