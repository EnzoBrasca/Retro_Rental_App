import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import { AppState } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { AuthResponse, Rol } from '../services/auth';
import { onUnauthorized, saveLastUsername } from '../services/session';

/**
 * AuthContext: única fuente de verdad sobre QUIÉN está logueado en toda la app.
 *
 * Responsabilidades:
 *  - Guardar el usuario + token de forma persistente (AsyncStorage) para que
 *    la sesión sobreviva a cerrar y reabrir la app.
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

// Clave única bajo la que persistimos la sesión. `services/api.ts` lee esta
// misma clave para adjuntar el token en el header Authorization.
const STORAGE_KEY = 'user';

export interface Usuario {
  nombre: string;
  apellido: string;
  username: string;
  rol: Rol;
  token: string;
  // Vencimiento del token en epoch millis, tal como lo firmó el backend.
  // Puede faltar en sesiones viejas persistidas antes de este campo → esas se
  // tratan como vencidas, así el usuario vuelve a loguearse una única vez.
  expiresAt?: number;
  // Teléfono formateado que llega en el AuthResponse. Puede faltar en sesiones
  // viejas persistidas antes de este campo → tratar como opcional.
  telefono?: string | null;
}

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
    await AsyncStorage.removeItem(STORAGE_KEY);
    setUser(null);
  }, []);

  // Al montar la app, rehidratamos la sesión — pero solo si el token sigue
  // vigente. Sin este chequeo la app mostraría una sesión zombie: el usuario
  // navegando con su rol mientras cada request muere con 401.
  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY)
      .then(async (stored) => {
        if (!stored) return;
        const persisted = JSON.parse(stored) as Usuario;
        if (isExpired(persisted)) {
          await AsyncStorage.removeItem(STORAGE_KEY);
          return;
        }
        setUser(persisted);
      })
      .finally(() => setIsLoading(false));
  }, []);

  // El backend puede rechazar el token aunque no haya vencido por reloj (secret
  // rotado, usuario eliminado). El 401 es la señal definitiva: cerramos sesión.
  useEffect(() => onUnauthorized(() => { void logout(); }), [logout]);

  // La sesión puede vencer con la app abierta en segundo plano. Al volver a
  // primer plano revisamos el reloj para no mostrar pantallas de un usuario
  // cuya sesión ya no existe.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state !== 'active') return;
      setUser((current) => {
        if (current && isExpired(current)) {
          void AsyncStorage.removeItem(STORAGE_KEY);
          return null;
        }
        return current;
      });
    });
    return () => subscription.remove();
  }, []);

  // El AuthResponse ya trae token + datos del usuario: lo guardamos tal cual.
  const login = async (data: AuthResponse) => {
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(data));
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
