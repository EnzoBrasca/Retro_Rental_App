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
  empleadoUsername: string;
  // Precio por litro efectivamente aplicado a esta carga. El monto total no
  // viaja: es litros × este precio.
  precioUnitario: number;
  ticketFotoKey: string | null;
  ticketFotoUrl: string | null;
  tableroFotoKey: string | null;
  tableroFotoUrl: string | null;
  // Anulación. null = ticket vigente. Solo llegan con valor al panel del admin
  // cuando pide ver los anulados: el resto de los endpoints los filtra.
  fechaAnulacion: string | null;
  anuladoPorUsername: string | null;
}

// Historial del empleado autenticado (sus tickets vigentes, más recientes primero).
export function getMisTickets(): Promise<Ticket[]> {
  return api.get<Ticket[]>('/tickets/me');
}

// ---------------------------------------------------------------- admin

/** Filtros del listado del admin. Todos opcionales, se combinan con AND. */
export interface AdminTicketFilters {
  empleadoId?: number | null;
  proveedorId?: number | null;
  vehiculoId?: number | null;
  // ISO 8601 sin zona (yyyy-MM-ddTHH:mm:ss), como los espera el backend.
  desde?: string | null;
  hasta?: string | null;
  // Monto TOTAL de la carga (litros × precio), no el precio por litro.
  montoMin?: number | null;
  montoMax?: number | null;
  incluirAnulados?: boolean;
  page?: number;
  size?: number;
}

// Espejo de PagedModel del backend.
export interface Paged<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

/** Listado paginado de tickets para el panel del admin. */
export function getAdminTickets(filters: AdminTicketFilters = {}): Promise<Paged<Ticket>> {
  const qs = new URLSearchParams();
  // Solo viajan los filtros con valor: el backend omite el predicado cuando el
  // parámetro no llega, y mandar vacíos rompería el parseo de fechas y montos.
  const put = (k: string, v: unknown) => {
    if (v !== null && v !== undefined && v !== '') qs.append(k, String(v));
  };
  put('empleadoId', filters.empleadoId);
  put('proveedorId', filters.proveedorId);
  put('vehiculoId', filters.vehiculoId);
  put('desde', filters.desde);
  put('hasta', filters.hasta);
  put('montoMin', filters.montoMin);
  put('montoMax', filters.montoMax);
  if (filters.incluirAnulados) qs.append('incluirAnulados', 'true');
  put('page', filters.page ?? 0);
  put('size', filters.size ?? 20);
  return api.get<Paged<Ticket>>(`/admin/tickets?${qs.toString()}`);
}

/**
 * Anula un ticket. Es un DELETE, pero baja lógica: la fila queda (un ticket es
 * un registro contable) y el backend revierte en el vehículo la lectura del
 * contador y el consumo que esta carga había dejado. Devuelve 204 sin body.
 */
export function anularTicket(id: number): Promise<void> {
  return api.delete<void>(`/admin/tickets/${id}`);
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
  // Lectura del odómetro/horómetro al momento de la carga. Obligatoria: sin
  // ella no se puede calcular el consumo real entre cargas.
  usoAcumulado: number;
  // Precio por litro realmente pagado. Solo se manda si el empleado corrigió el
  // del catálogo; si va vacío, el backend usa el vigente (idPrecio).
  precioUnitario?: number;
  fechaCarga?: string; // ISO opcional; el backend usa "ahora" si falta
}

/**
 * Crea un ticket (multipart). El empleado sale del JWT en el backend. La foto
 * de tablero es opcional y por ahora no se envía (versión futura). `fotoUri` es
 * el uri local de la imagen (cámara o galería) y es OPCIONAL: si no viene, el
 * ticket se crea sin comprobante (el backend acepta ticketFoto null).
 */
export function createTicket(payload: CreateTicketPayload, fotoUri?: string | null): Promise<Ticket> {
  const form = new FormData();
  form.append('litros', String(payload.litros));
  form.append('idPrecio', String(payload.idPrecio));
  form.append('idProveedor', String(payload.idProveedor));
  form.append('idVehiculo', String(payload.idVehiculo));
  form.append('usoAcumulado', String(payload.usoAcumulado));
  if (payload.precioUnitario != null) {
    form.append('precioUnitario', String(payload.precioUnitario));
  }
  if (payload.fechaCarga) form.append('fechaCarga', payload.fechaCarga);
  // La foto solo se adjunta si el empleado la sacó. En React Native un archivo
  // se adjunta como { uri, name, type }.
  if (fotoUri) {
    form.append('ticketFoto', {
      uri: fotoUri,
      name: 'ticket.jpg',
      type: 'image/jpeg',
    } as unknown as Blob);
  }
  return api.postForm<Ticket>('/tickets', form);
}
