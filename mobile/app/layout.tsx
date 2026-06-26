import { useEffect } from 'react';
import { Slot, useRouter, useSegments } from 'expo-router';
import { AuthProvider, useAuth } from '../context/AuthContext';

function RootLayoutNav() {
  const { user, isLoading } = useAuth();
  const segments = useSegments();
  const router = useRouter();

  useEffect(() => {
    if (isLoading) return;

    const inAuthGroup = segments[0] === '(auth)';

    if (!user && !inAuthGroup) {
      // No hay sesión → ir al login
      router.replace('/(auth)/login');
    } else if (user?.role === 'empleado' && segments[0] !== '(empleado)') {
      // Empleado logueado → ir a su sección
      router.replace('/(empleado)');
    } else if (user?.role === 'administrador' && segments[0] !== '(administrador)') {
      // Jefe logueado → ir a su sección
      router.replace('/(administrador)');
    }
  }, [user, segments, isLoading]);

  return <Slot />;
}

export default function RootLayout() {
  return (
    <AuthProvider>
      <RootLayoutNav />
    </AuthProvider>
  );
}