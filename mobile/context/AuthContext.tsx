import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { AuthResponse, Rol } from '../services/auth';

/**
 * AuthContext: única fuente de verdad sobre QUIÉN está logueado en toda la app.
 *
 * Responsabilidades:
 *  - Guardar el usuario + token de forma persistente (AsyncStorage) para que
 *    la sesión sobreviva a cerrar y reabrir la app.
 *  - Rehidratar esa sesión al arrancar (`isLoading` evita parpadeos/redirects
 *    prematuros mientras leemos el storage).
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
  email: string;
  rol: Rol;
  token: string;
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

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Usuario | null>(null);
  // Arranca en true: todavía no sabemos si hay sesión guardada.
  const [isLoading, setIsLoading] = useState(true);

  // Al montar la app, intentamos rehidratar la sesión desde el storage.
  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY)
      .then((stored) => {
        if (stored) setUser(JSON.parse(stored) as Usuario);
      })
      .finally(() => setIsLoading(false));
  }, []);

  // El AuthResponse ya trae token + datos del usuario: lo guardamos tal cual.
  const login = async (data: AuthResponse) => {
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    setUser(data);
  };

  const logout = async () => {
    await AsyncStorage.removeItem(STORAGE_KEY);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

// Hook de conveniencia: cualquier pantalla hace `const { user } = useAuth()`.
export const useAuth = () => useContext(AuthContext);
