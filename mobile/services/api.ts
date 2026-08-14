import { emitUnauthorized } from './session';
import { readToken } from './sessionStorage';

// El backend (Spring Boot) corre en el puerto 8080 por defecto.
// En el celular físico no podés usar localhost: necesitás la IP de tu máquina
// (por ej. http://192.168.0.10:8080) vía la env var EXPO_PUBLIC_API_URL.
const BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080';

// El token vive en SecureStore (Keystore/Keychain), no en AsyncStorage: ver
// services/sessionStorage.ts para el porqué.
function getToken(): Promise<string | null> {
  return readToken();
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = await getToken();
  // Con FormData NO seteamos Content-Type: fetch pone el multipart boundary solo.
  const isForm = options.body instanceof FormData;

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      ...(isForm ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    // 401 en una ruta autenticada = el token venció o dejó de ser válido → hay
    // que cerrar la sesión. Ojo: /auth/login también responde 401 cuando las
    // credenciales son incorrectas, y ESO no es una sesión expirada; por eso
    // las rutas de auth quedan fuera.
    if (response.status === 401 && !path.startsWith('/auth/')) {
      emitUnauthorized();
    }
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message ?? `Error ${response.status}`);
  }

  // 204 No Content (ej. baja de vehiculo) no trae body para parsear.
  if (response.status === 204) return undefined as T;
  return response.json();
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  delete: <T>(path: string) =>
    request<T>(path, { method: 'DELETE' }),
  // Envío multipart (ej. crear ticket con foto).
  postForm: <T>(path: string, form: FormData) =>
    request<T>(path, { method: 'POST', body: form }),
};