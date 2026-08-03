import AsyncStorage from '@react-native-async-storage/async-storage';

/**
 * Puente entre el cliente HTTP y la capa de auth.
 *
 * `services/api.ts` no sabe (ni debe saber) nada de React ni de AuthContext:
 * no puede llamar a `useAuth()` desde una función suelta. Pero cuando el backend
 * responde 401, alguien tiene que cerrar la sesión. Este módulo es ese alguien:
 * un registro de callbacks que `api.ts` dispara y que `AuthProvider` escucha.
 *
 * También guarda el último username logueado, bajo una clave SEPARADA de la sesión,
 * porque debe sobrevivir al logout (la sesión no).
 */

const LAST_USERNAME_KEY = 'lastUsername';

type UnauthorizedHandler = () => void;

let handlers: UnauthorizedHandler[] = [];

// El AuthProvider se suscribe al montar. Devuelve la función para desuscribirse.
export function onUnauthorized(handler: UnauthorizedHandler): () => void {
  handlers.push(handler);
  return () => {
    handlers = handlers.filter((h) => h !== handler);
  };
}

// La dispara `api.ts` ante un 401 en una ruta autenticada.
export function emitUnauthorized(): void {
  handlers.forEach((handler) => handler());
}

// Último username que inició sesión en este dispositivo. Se conserva tanto si
// la sesión expiró como si el usuario cerró sesión a mano, para prellenar el login.
export function saveLastUsername(username: string): Promise<void> {
  return AsyncStorage.setItem(LAST_USERNAME_KEY, username);
}

export function getLastUsername(): Promise<string | null> {
  return AsyncStorage.getItem(LAST_USERNAME_KEY);
}
