import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import { AppState } from 'react-native';
import { AuthResponse } from '../services/auth';
import { onUnauthorized, saveLastUsername } from '../services/session';
import {
  Usuario,
  clearSession,
  purgeLegacySession,
  readSession,
  saveSession,
} from '../services/sessionStorage';

/**
 * AuthContext: única fuente de verdad sobre QUIÉN está logueado en toda la app.
 *
 * Responsabilidades:
 *  - Guardar la sesión de forma persistente para que sobreviva a cerrar y
 *    reabrir la app. El CÓMO vive en `services/sessionStorage.ts`: el token va
 *    a SecureStore (Keystore/Keychain) y el perfil a AsyncStorage. Este
 *    contexto no sabe de almacenes; solo pide guardar, leer y limpiar.
 *  - Rehidratar esa sesión al arrancar (`isLoading` evita parpadeos/redirects
 *    prematuros mientras leemos el storage).
 *  - Cerrar la sesión apenas el token vence, por tres vías complementarias:
 *      1) al rehidratar (arranque de la app),
 *      2) al volver la app a primer plano,
 *      3) ante un 401 del backend (vía `services/session.ts`).
 *  - Exponer `login` / `logout` para mutar la sesión desde las pantallas.
 *
 * Importante: el shape de `Usuario` es ESPEJO de `AuthResponse` del backend.
 * Guardamos el `rol` tal cual viene ('EMPLEADO' | 'ADMINISTRADOR') para que la
 * redirección por rol compare contra el mismo valor que emitió el servidor.
 */

// `Usuario` se define junto a su persistencia (sessionStorage) y se reexporta
// acá para no romper a las pantallas que ya lo importaban desde este módulo.
export type { Usuario };

interface AuthContextType {
  user: Usuario | null;
  isLoading: boolean;
  // Recibe la respuesta completa del backend y la persiste como sesión activa.
  login: (data: AuthResponse) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType>({} as AuthContextType);

function isExpired(user: Usuario): boolean {
  return !user.expiresAt || user.expiresAt <= Date.now();
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Usuario | null>(null);
  // Arranca en true: todavía no sabemos si hay sesión guardada.
  const [isLoading, setIsLoading] = useState(true);

  const logout = useCallback(async () => {
    // Solo borramos la sesión: `lastUsername` se conserva a propósito para
    // prellenar el login la próxima vez.
    await clearSession();
    setUser(null);
  }, []);

  // Al montar la app, rehidratamos la sesión — pero solo si el token sigue
  // vigente. Sin este chequeo la app mostraría una sesión zombie: el usuario
  // navegando con su rol mientras cada request muere con 401.
  useEffect(() => {
    // Se borra de entrada la sesión en formato viejo, donde el token vivía en
    // claro en AsyncStorage. No se migra a propósito: ese token ya estuvo
    // expuesto, así que se descarta y el usuario inicia sesión una vez más.
    purgeLegacySession()
      .then(readSession)
      .then(async (persisted) => {
        if (!persisted) return;
        if (isExpired(persisted)) {
          await clearSession();
          return;
        }
        setUser(persisted);
      })
      .finally(() => setIsLoading(false));
  }, []);

  // El backend puede rechazar el token aunque no haya vencido por reloj (secret
  // rotado, usuario eliminado). El 401 es la señal definitiva: cerramos sesión.
  useEffect(
    () =>
      onUnauthorized(() => {
        void logout();
      }),
    [logout],
  );

  // La sesión puede vencer con la app abierta en segundo plano. Al volver a
  // primer plano revisamos el reloj para no mostrar pantallas de un usuario
  // cuya sesión ya no existe.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state !== 'active') return;
      setUser((current) => {
        if (current && isExpired(current)) {
          void clearSession();
          return null;
        }
        return current;
      });
    });
    return () => subscription.remove();
  }, []);

  // El AuthResponse trae token + datos del usuario. sessionStorage se encarga
  // de separarlos: el token a SecureStore, el resto a AsyncStorage.
  const login = async (data: AuthResponse) => {
    await saveSession(data);
    await saveLastUsername(data.username);
    setUser(data);
  };

  return (
    <AuthContext.Provider value={{ user, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

// Hook de conveniencia: cualquier pantalla hace `const { user } = useAuth()`.
export const useAuth = () => useContext(AuthContext);
