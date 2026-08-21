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
  // Proporción nafta:aceite de la mezcla que consume (50 = 50:1). Es una
  // propiedad de la MÁQUINA, no de la carga: una motosierra vieja pide 25:1 y
  // una moderna 50:1. Con ella se calcula el precio de la mezcla a partir del
  // de la nafta y el del aceite, en vez de pedirle al operario un precio de
  // mezcla que no existe en ningún surtidor.
  relacionMezcla: number;
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

// Cuerpo del alta y de la edición: son los mismos campos.
export interface CreateHerramientaPayload {
  nombre: string;
  capacidad: number;
  // Opcional en el contrato del backend: si no viaja, el alta usa 50:1 y la
  // edición CONSERVA la que la herramienta ya tenía (no la pisa). Así el APK
  // viejo, que no manda el campo, no rompe el ABM ni borra el dato.
  relacionMezcla?: number;
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
