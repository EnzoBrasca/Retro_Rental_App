import { useEffect } from 'react';
import { Slot, useRouter, useSegments } from 'expo-router';
import { AuthProvider, useAuth } from '../context/AuthContext';

/**
 * Layout RAÍZ de la app (Expo Router).
 *
 * El nombre del archivo DEBE ser `_layout.tsx` (con guion bajo). Expo Router
 * usa esa convención para identificar layouts; sin el guion sería tratado como
 * una pantalla común y el AuthProvider nunca envolvería la app.
 *
 * Este layout cumple dos roles:
 *  1) Envolver TODA la navegación con <AuthProvider>, para que cualquier
 *     pantalla pueda leer la sesión con useAuth().
 *  2) Actuar como "guardia" de navegación: según haya o no sesión y según el
 *     rol, redirige al grupo de rutas correcto.
 */

function RootLayoutNav() {
  const { user, isLoading } = useAuth();
  // segments = piezas de la ruta actual. segments[0] es el grupo: '(auth)',
  // '(empleado)' o '(administrador)'.
  const segments = useSegments();
  const router = useRouter();

  useEffect(() => {
    // Mientras rehidratamos la sesión no decidimos nada (evita redirects falsos).
    if (isLoading) return;

    const inAuthGroup = segments[0] === '(auth)';

    if (!user && !inAuthGroup) {
      // No hay sesión y está fuera del login → mandarlo a autenticarse.
      router.replace('/(auth)/login');
    } else if (user?.rol === 'EMPLEADO' && segments[0] !== '(empleado)') {
      // Empleado logueado → su sección.
      router.replace('/(empleado)');
    } else if (user?.rol === 'ADMINISTRADOR' && segments[0] !== '(administrador)') {
      // Administrador logueado → su sección.
      router.replace('/(administrador)');
    }
  }, [user, segments, isLoading]);

  // <Slot /> renderiza la pantalla hija que corresponda a la ruta actual.
  return <Slot />;
}

export default function RootLayout() {
  return (
    <AuthProvider>
      <RootLayoutNav />
    </AuthProvider>
  );
}
