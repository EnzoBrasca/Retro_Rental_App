import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { CREDENTIALS, Role } from '../data/mock';

type User = { email: string; name: string; role: Role };

type AuthState = {
  user: User | null;
  /** Returns null on success, or an error message on failure. */
  login: (email: string, password: string) => string | null;
  logout: () => void;
  updateName: (name: string) => void;
};

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);

  const login = useCallback((email: string, password: string) => {
    const match = CREDENTIALS.find(
      (c) => c.email.toLowerCase() === email.trim().toLowerCase() && c.password === password,
    );
    if (!match) return 'Credenciales incorrectas. Revisá el correo y la contraseña.';
    setUser({ email: match.email, name: match.name, role: match.role });
    return null;
  }, []);

  const logout = useCallback(() => setUser(null), []);

  const updateName = useCallback((name: string) => {
    setUser((prev) => (prev ? { ...prev, name } : null));
  }, []);

  const value = useMemo(() => ({ user, login, logout, updateName }), [user, login, logout, updateName]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
