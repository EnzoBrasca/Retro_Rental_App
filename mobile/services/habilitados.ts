import { api } from './api';

/**
 * Capa de API del padrón de empleados habilitados a registrarse.
 * Espejo de HabilitadoResponse del backend.
 *
 * El registro en la app sigue siendo self-service: el empleado se da de alta
 * solo, con el mismo formulario de siempre. Este padrón define QUIÉNES pueden
 * hacerlo, para que el endpoint público no quede abierto a cualquiera.
 */
export interface Habilitado {
  id: number;
  documento: string;
  apellido: string;
  nombre: string | null;
  fechaAlta: string; // yyyy-mm-dd
  // Lo resuelve el backend: true cuando ya se consumió la habilitación.
  registrado: boolean;
  fechaUso: string | null;
  // Username de la cuenta creada con esta habilitación. null si sigue sin usarse.
  username: string | null;
}

// Cuerpo del alta. Espejo de CreateHabilitadoRequest.
export interface CreateHabilitadoPayload {
  documento: string;
  apellido: string;
  nombre?: string;
}

export function getHabilitados(): Promise<Habilitado[]> {
  return api.get<Habilitado[]>('/admin/habilitados');
}

export function createHabilitado(payload: CreateHabilitadoPayload): Promise<Habilitado> {
  return api.post<Habilitado>('/admin/habilitados', payload);
}

// Alta masiva: los documentos ya presentes se saltean. Devuelve el padrón completo.
export function createHabilitadosBulk(payload: CreateHabilitadoPayload[]): Promise<Habilitado[]> {
  return api.post<Habilitado[]>('/admin/habilitados/bulk', payload);
}

// Solo se puede quitar una habilitación que todavía no se usó. Devuelve 204 sin body.
export function deleteHabilitado(id: number): Promise<void> {
  return api.delete<void>(`/admin/habilitados/${id}`);
}
