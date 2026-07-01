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
 *   POST /auth/login    -> { email, password }
 *   POST /auth/register -> { nombre, apellido, documento, email, password, rol }
 *   Respuesta (ambos)   -> AuthResponse
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
  email: string;
  rol: Rol;
}

// Cuerpo que espera POST /auth/login.
export interface LoginPayload {
  email: string;
  password: string;
}

// Cuerpo que espera POST /auth/register.
export interface RegisterPayload {
  nombre: string;
  apellido: string;
  documento: string;
  email: string;
  password: string;
  rol: Rol;
}

export function loginRequest(payload: LoginPayload): Promise<AuthResponse> {
  return api.post<AuthResponse>('/auth/login', payload);
}

export function registerRequest(payload: RegisterPayload): Promise<AuthResponse> {
  return api.post<AuthResponse>('/auth/register', payload);
}
