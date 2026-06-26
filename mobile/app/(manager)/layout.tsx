import { Stack } from 'expo-router';

export default function ManagerLayout() {
  return (
    <Stack>
      <Stack.Screen name="index" options={{ title: 'Dashboard' }} />
      <Stack.Screen name="tickets" options={{ title: 'Tickets del día' }} />
    </Stack>
  );
}