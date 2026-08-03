import { useEffect } from 'react';
import { View, ActivityIndicator } from 'react-native';
import { Slot, useRouter, useSegments } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { useFonts, Oswald_600SemiBold, Oswald_700Bold } from '@expo-google-fonts/oswald';
import {
  IBMPlexSans_400Regular,
  IBMPlexSans_500Medium,
  IBMPlexSans_600SemiBold,
} from '@expo-google-fonts/ibm-plex-sans';
import { IBMPlexMono_500Medium } from '@expo-google-fonts/ibm-plex-mono';
import { AuthProvider, useAuth } from '../context/AuthContext';
import { colors } from '../constants/theme';

/**
 * Layout RAÍZ de la app (Expo Router).
 *
 * El nombre del archivo DEBE ser `_layout.tsx` (con guion bajo). Expo Router
 * usa esa convención para identificar layouts; sin el guion sería tratado como
 * una pantalla común y el AuthProvider nunca envolvería la app.
 *
 * Este layout cumple tres roles:
 *  1) Cargar las tipografías (Oswald / IBM Plex) que usa todo el diseño oscuro.
 *  2) Envolver TODA la navegación con <AuthProvider>, para que cualquier
 *     pantalla pueda leer la sesión con useAuth().
 *  3) Actuar como "guardia" de navegación: según haya o no sesión y según el
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
    } else if (
      user?.rol === 'ADMINISTRADOR' &&
      segments[0] !== '(administrador)' &&
      segments[0] !== '(empleado)'
    ) {
      // Administrador logueado → su sección (pero puede entrar a "modo operario"
      // en (empleado) sin ser expulsado).
      router.replace('/(administrador)');
    }
  }, [user, segments, isLoading]);

  // <Slot /> renderiza la pantalla hija que corresponda a la ruta actual.
  return <Slot />;
}

export default function RootLayout() {
  const [fontsLoaded] = useFonts({
    Oswald_600SemiBold,
    Oswald_700Bold,
    IBMPlexSans_400Regular,
    IBMPlexSans_500Medium,
    IBMPlexSans_600SemiBold,
    IBMPlexMono_500Medium,
  });

  // Hasta que las fuentes estén listas mostramos un spinner sobre el fondo
  // oscuro: evita el "flash" de texto con la tipografía del sistema.
  if (!fontsLoaded) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }

  return (
    <SafeAreaProvider>
      <AuthProvider>
        <StatusBar style="light" />
        <RootLayoutNav />
      </AuthProvider>
    </SafeAreaProvider>
  );
}
