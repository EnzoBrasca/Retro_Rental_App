import { api } from './api';
import { TipoCombustible } from './vehiculos';

/**
 * Capa de API de tickets. Espejo de TicketResponse del backend.
 *
 * Ojo: el ticket referencia precio/proveedor/vehiculo por ID (no trae nombres
 * ni montos). El Historial resuelve esos IDs contra los catálogos para mostrar
 * proveedor y costo. `tableroFoto*` puede venir null (foto opcional).
 */
export interface Ticket {
  id: number;
  litros: number;
  fechaCarga: string;
  idPrecio: number;
  idProveedor: number;
  idVehiculo: number;
  empleadoEmail: string;
  ticketFotoKey: string | null;
  ticketFotoUrl: string | null;
  tableroFotoKey: string | null;
  tableroFotoUrl: string | null;
}

// Historial del empleado autenticado (sus tickets, más recientes primero).
export function getMisTickets(): Promise<Ticket[]> {
  return api.get<Ticket[]>('/tickets/me');
}

/**
 * Resultado del análisis OCR de la foto del ticket. Espejo de
 * TicketAnalysisResponse del backend. CUALQUIER campo puede venir null: el OCR
 * deja null lo que no pudo leer y el empleado lo completa a mano.
 *
 * - litros / fechaCarga: datos crudos del ticket, usables tal cual.
 * - estacion / precioPorLitro / importeTotal: lo que leyó el OCR (para mostrar
 *   cuando no hubo match contra el catálogo).
 * - tipoCombustible: producto clasificado por el OCR. Se compara con el
 *   combustible del vehículo para avisar discrepancias. null si ilegible.
 * - idProveedor / idPrecio: entradas del catálogo que el backend resolvió a
 *   partir del ticket. null si no hubo coincidencia.
 */
export interface TicketAnalysis {
  litros: number | null;
  fechaCarga: string | null;
  importeTotal: number | null;
  precioPorLitro: number | null;
  estacion: string | null;
  tipoCombustible: TipoCombustible | null;
  idProveedor: number | null;
  proveedorNombre: string | null;
  idPrecio: number | null;
  precioUnitario: number | null;
}

/**
 * Analiza la foto del ticket con el OCR (Mistral) SIN persistir nada. Devuelve
 * los campos pre-cargados para el formulario de creación. `fotoUri` es el uri
 * local de la imagen (cámara o galería).
 */
export function analyzeTicket(fotoUri: string): Promise<TicketAnalysis> {
  const form = new FormData();
  form.append('ticketFoto', {
    uri: fotoUri,
    name: 'ticket.jpg',
    type: 'image/jpeg',
  } as unknown as Blob);
  return api.postForm<TicketAnalysis>('/tickets/analyze', form);
}

// Datos de la carga (sin la foto, que va aparte como archivo multipart).
export interface CreateTicketPayload {
  litros: number;
  idPrecio: number;
  idProveedor: number;
  idVehiculo: number;
  fechaCarga?: string; // ISO opcional; el backend usa "ahora" si falta
}

/**
 * Crea un ticket con su foto (multipart). El empleado sale del JWT en el
 * backend. La foto de tablero es opcional y por ahora no se envía (versión
 * futura). `fotoUri` es el uri local de la imagen (cámara o galería).
 */
export function createTicket(payload: CreateTicketPayload, fotoUri: string): Promise<Ticket> {
  const form = new FormData();
  form.append('litros', String(payload.litros));
  form.append('idPrecio', String(payload.idPrecio));
  form.append('idProveedor', String(payload.idProveedor));
  form.append('idVehiculo', String(payload.idVehiculo));
  if (payload.fechaCarga) form.append('fechaCarga', payload.fechaCarga);
  // En React Native un archivo se adjunta como { uri, name, type }.
  form.append('ticketFoto', {
    uri: fotoUri,
    name: 'ticket.jpg',
    type: 'image/jpeg',
  } as unknown as Blob);
  return api.postForm<Ticket>('/tickets', form);
}
