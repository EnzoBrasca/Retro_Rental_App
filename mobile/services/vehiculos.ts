import { api } from './api';

/**
 * Capa de API de vehiculos. Espejo de VehiculoResponse del backend.
 * Los enums viajan como strings (mismos literales que el enum Java).
 */
export type Estado = 'DISPONIBLE' | 'EN_USO' | 'EN_MANTENIMIENTO';
export type TipoVehiculo = 'MAQUINA' | 'CAMIONETA' | 'CAMION';

// Unidad del contador de uso. La define el backend a partir del tipo de
// vehículo: las máquinas viales miden horas de horómetro, el resto kilómetros.
export type UnidadUso = 'KM' | 'HORAS';

// Misma regla que TipoVehiculo.unidadUso() en el backend. Se duplica acá SOLO
// para el formulario de alta: ahí el vehículo todavía no existe, así que no hay
// respuesta del servidor de la cual leer la unidad. Para un vehículo ya
// guardado se usa siempre el `unidadUso` que viene en la respuesta.
export function unidadDeTipo(tipo: TipoVehiculo | null): UnidadUso {
  return tipo === 'MAQUINA' ? 'HORAS' : 'KM';
}

// Etiquetas de UI derivadas de la unidad.
export function etiquetaUso(tipo: TipoVehiculo | null): string {
  return unidadDeTipo(tipo) === 'HORAS' ? 'Horas de uso' : 'Kilometraje';
}

// Etiqueta del campo donde el empleado anota la lectura al cargar combustible.
// Nombra el instrumento a propósito: en el campo se lee un horómetro o un
// odómetro, y decirlo así evita que alguien anote kilómetros en una máquina.
export function etiquetaLectura(tipo: TipoVehiculo | null): string {
  return unidadDeTipo(tipo) === 'HORAS' ? 'Horas del horómetro' : 'Kilómetros del odómetro';
}

export function etiquetaConsumo(tipo: TipoVehiculo | null): string {
  return unidadDeTipo(tipo) === 'HORAS'
    ? 'Consumo promedio (L/h)'
    : 'Consumo promedio (L/100km)';
}
export type TipoCombustible =
  | 'NAFTA_SUPER'
  | 'NAFTA_PREMIUM'
  | 'GASOIL_GRADO_2'
  | 'GASOIL_GRADO_3'
  | 'GNC';

export interface Vehiculo {
  id: number;
  patente: string;
  tipoVehiculo: TipoVehiculo;
  tipoCombustible: TipoCombustible;
  estado: Estado;
  capacidadTanque: number;
  // Contador de uso acumulado. `unidadUso` dice en qué se mide: HORAS en una
  // máquina vial, KM en un camión o camioneta.
  usoAcumulado: number;
  unidadUso: UnidadUso;
  consumoPromedio: number;
  fechaBaja: string | null;
  // Operario que usó el vehículo por última vez (se actualiza en cada carga).
  // null = sin uso registrado. El nombre/apellido vienen del backend para pintar
  // "En uso por X" en la card sin una segunda llamada.
  idOperario: number | null;
  operarioNombre: string | null;
  operarioApellido: string | null;
}

// El empleado autenticado TOMA un vehiculo libre (self-service).
export function tomarVehiculo(id: number): Promise<Vehiculo> {
  return api.post<Vehiculo>(`/me/vehiculos/${id}`, {});
}

// El empleado autenticado LIBERA un vehiculo asignado (queda disponible).
export function liberarVehiculo(id: number): Promise<void> {
  return api.delete<void>(`/me/vehiculos/${id}`);
}

// Vehiculos asignados al empleado autenticado (su flota).
export function getMisVehiculos(): Promise<Vehiculo[]> {
  return api.get<Vehiculo[]>('/me/vehiculos');
}

// Catálogo completo de la maquinaria del parque (lectura).
export function getVehiculos(): Promise<Vehiculo[]> {
  return api.get<Vehiculo[]>('/vehiculos');
}

// Listado para el ABM del administrador (incluye dados de baja).
export function getAdminVehiculos(): Promise<Vehiculo[]> {
  return api.get<Vehiculo[]>('/admin/vehiculos');
}

// Cuerpo del alta. Espejo de CreateVehiculoRequest (estado opcional → DISPONIBLE).
export interface CreateVehiculoPayload {
  patente: string;
  tipoVehiculo: TipoVehiculo;
  tipoCombustible: TipoCombustible;
  capacidadTanque: number;
  estado?: Estado;
  fechaUltimoMantenimiento: string; // yyyy-mm-dd
  usoAcumulado: number;
  consumoPromedio: number;
}

// Cuerpo de la edición. Espejo de UpdateVehiculoRequest (estado obligatorio).
export interface UpdateVehiculoPayload extends CreateVehiculoPayload {
  estado: Estado;
}

export function createVehiculo(payload: CreateVehiculoPayload): Promise<Vehiculo> {
  return api.post<Vehiculo>('/admin/vehiculos', payload);
}

export function updateVehiculo(id: number, payload: UpdateVehiculoPayload): Promise<Vehiculo> {
  return api.put<Vehiculo>(`/admin/vehiculos/${id}`, payload);
}

// Baja lógica (setea fechaBaja en el backend). Devuelve 204 sin body.
export function desactivarVehiculo(id: number): Promise<void> {
  return api.delete<void>(`/admin/vehiculos/${id}`);
}
