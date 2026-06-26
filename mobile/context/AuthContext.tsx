import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';

type Role = 'empleado' | 'administrador';

interface Usuario {
  name: string;
  role: Role;
  token: string;
}

interface AuthContextType {
  user: Usuario | null;
  isLoading: boolean;
  login: (token: string, userData: Omit<Usuario, 'token'>) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType>({} as AuthContextType);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Usuario | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Al arrancar la app, revisa si hay una sesión guardada
  useEffect(() => {
    AsyncStorage.getItem('user')
      .then((stored) => {
        if (stored) setUser(JSON.parse(stored));
      })
      .finally(() => setIsLoading(false));
  }, []);

  const login = async (token: string, userData: Omit<Usuario, 'token'>) => {
    const fullUser = { ...userData, token };
    await AsyncStorage.setItem('user', JSON.stringify(fullUser));
    setUser(fullUser);
  };

  const logout = async () => {
    await AsyncStorage.removeItem('user');
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);