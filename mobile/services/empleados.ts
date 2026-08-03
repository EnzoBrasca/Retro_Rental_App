import { api } from './api';
import { TelefonoPayload } from './auth';

/**
 * Capa de API de gestión de personal (ABM de empleados). Espejo de
 * EmpleadoResponse del backend.
 */
export interface Empleado {
  id: number;
  nombre: string;
  apellido: string;
  documento: string;
  username: string;
  telefono: TelefonoPayload | null;
  fechaAlta: string; // yyyy-mm-dd
  fechaBaja: string | null;
}

// Cuerpo del alta. Espejo de CreateEmpleadoRequest. El username lo genera el
// backend a partir de nombre + apellido: no se envía en el payload.
export interface CreateEmpleadoPayload {
  nombre: string;
  apellido: string;
  documento: string;
  password: string;
  telefono: TelefonoPayload;
}

// Cuerpo de la edición. Espejo de UpdateEmpleadoRequest (sin password ni documento).
export interface UpdateEmpleadoPayload {
  nombre: string;
  apellido: string;
  telefono: TelefonoPayload;
}

// Listado para el ABM del administrador (incluye dados de baja).
export function getAdminEmpleados(): Promise<Empleado[]> {
  return api.get<Empleado[]>('/admin/empleados');
}

export function createEmpleado(payload: CreateEmpleadoPayload): Promise<Empleado> {
  return api.post<Empleado>('/admin/empleados', payload);
}

export function updateEmpleado(id: number, payload: UpdateEmpleadoPayload): Promise<Empleado> {
  return api.put<Empleado>(`/admin/empleados/${id}`, payload);
}

// Baja lógica (setea fechaBaja en el backend). Devuelve 204 sin body.
export function desactivarEmpleado(id: number): Promise<void> {
  return api.delete<void>(`/admin/empleados/${id}`);
}
