import { Stack } from 'expo-router';

export default function EmployeeLayout() {
  return (
    <Stack>
      <Stack.Screen name="index" options={{ title: 'Registrar Carga' }} />
      <Stack.Screen name="history" options={{ title: 'Mis Registros' }} />
    </Stack>
  );
}