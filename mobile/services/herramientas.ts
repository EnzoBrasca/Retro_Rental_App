import { api } from './api';

/**
 * Capa de API de herramientas. Espejo de HerramientaResponse del backend.
 *
 * Una herramienta (motosierra, bidón, etc.) NO tiene identificador/patente, ni
 * modelo, ni tipo de vehículo, ni uso acumulado (no lleva horómetro/odómetro),
 * ni consumo promedio, ni combustible fijo: el combustible se elige carga por
 * carga (ver CreateTicketPayload.idHerramienta en services/tickets.ts). Solo
 * tiene nombre y capacidad.
 */
export interface Herramienta {
  id: number;
  nombre: string;
  capacidad: number;
  // Baja lógica, misma convención que Vehiculo.fechaBaja. Opcional porque el
  // contrato del backend no lo garantiza en todas las respuestas.
  fechaBaja?: string | null;
}

// Catálogo de herramientas activas (lectura para el empleado).
export function getHerramientas(): Promise<Herramienta[]> {
  return api.get<Herramienta[]>('/herramientas');
}

// Listado para el ABM del administrador (incluye dadas de baja).
export function getAdminHerramientas(): Promise<Herramienta[]> {
  return api.get<Herramienta[]>('/admin/herramientas');
}

// Cuerpo del alta y de la edición: son los mismos dos campos.
export interface CreateHerramientaPayload {
  nombre: string;
  capacidad: number;
}

export type UpdateHerramientaPayload = CreateHerramientaPayload;

export function createHerramienta(payload: CreateHerramientaPayload): Promise<Herramienta> {
  return api.post<Herramienta>('/admin/herramientas', payload);
}

export function updateHerramienta(
  id: number,
  payload: UpdateHerramientaPayload,
): Promise<Herramienta> {
  return api.put<Herramienta>(`/admin/herramientas/${id}`, payload);
}

// Baja lógica. Devuelve 204 sin body.
export function desactivarHerramienta(id: number): Promise<void> {
  return api.delete<void>(`/admin/herramientas/${id}`);
}
