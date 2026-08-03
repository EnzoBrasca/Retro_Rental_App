import { api } from './api';

/**
 * Capa de API de autenticación.
 *
 * Esta capa NO sabe nada de React ni de pantallas: solo traduce llamadas
 * tipadas a las rutas HTTP del backend (`/auth/login`, `/auth/register`).
 * Reutiliza el cliente genérico de `services/api.ts`, que ya se encarga de
 * la BASE_URL, los headers JSON y el manejo de errores.
 *
 * Contrato espejo del backend (Spring Boot):
 *   POST /auth/login    -> { username, password }
 *   POST /auth/register -> { nombre, apellido, documento, password, rol, telefono }
 *   Respuesta (ambos)   -> AuthResponse
 *
 * El `username` de login/registro lo genera el backend a partir de
 * nombre + apellido: nunca lo elige ni lo envía el usuario.
 */

// El backend serializa el enum Rol por su .name(), o sea EN MAYÚSCULAS.
// El front DEBE usar exactamente estos mismos valores para que las
// comparaciones de rol (redirección) funcionen.
export type Rol = 'EMPLEADO' | 'ADMINISTRADOR';

// Lo que devuelve el backend tras un login o registro exitoso.
export interface AuthResponse {
  token: string;
  nombre: string;
  apellido: string;
  username: string;
  rol: Rol;
  // Teléfono ya formateado por el backend. null si no hay uno cargado.
  telefono: string | null;
  // Vencimiento del token en epoch millis. Permite detectar la sesión expirada
  // sin esperar a que un request falle con 401.
  expiresAt: number;
}

// Cuerpo que espera POST /auth/login.
export interface LoginPayload {
  username: string;
  password: string;
}

// Teléfono del usuario. Espejo de TelefonoRequest en el backend.
export interface TelefonoPayload {
  codigoArea: string;
  numero: string;
}

// Cuerpo que espera POST /auth/register.
// Sin `rol`: el registro público siempre crea un EMPLEADO y el backend ignora
// cualquier rol que le manden. Las altas de administrador se hacen fuera de la app.
export interface RegisterPayload {
  nombre: string;
  apellido: string;
  documento: string;
  password: string;
  telefono: TelefonoPayload;
}

export function loginRequest(payload: LoginPayload): Promise<AuthResponse> {
  return api.post<AuthResponse>('/auth/login', payload);
}

export function registerRequest(payload: RegisterPayload): Promise<AuthResponse> {
  return api.post<AuthResponse>('/auth/register', payload);
}
