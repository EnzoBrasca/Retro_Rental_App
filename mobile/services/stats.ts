import { api } from './api';
import type { UnidadUso } from './vehiculos';

/**
 * Estadísticas de consumo para el panel del administrador. Espejo de
 * StatsResponse. El backend agrega por proveedor (desglosePorProveedor) y por
 * empleado (desglosePorEmpleado).
 */
export type StatsRange = 'daily' | 'weekly' | 'monthly';

export interface ProveedorConsumo {
  proveedorId: number;
  nombre: string;
  litros: number;
  gasto: number;
}

export interface EmpleadoConsumo {
  empleadoId: number;
  nombreCompleto: string;
  litros: number;
  gasto: number;
}

export interface Stats {
  desde: string;
  hasta: string;
  totalLitros: number;
  gastoTotal: number;
  cantidadRegistros: number;
  vehiculosActivos: number;
  promedioLitrosPorVehiculo: number;
  // Consumo real del vehículo filtrado durante el período, en la unidad que
  // indica `unidadUso`. Ambos vienen en null cuando no hay un vehículo
  // seleccionado (promediar la flota mezclaría L/h con L/100km) o cuando no hay
  // dos lecturas con las que formar un intervalo. Null NO es cero: sin dato la
  // tarjeta no se muestra.
  consumoPeriodo: number | null;
  unidadUso: UnidadUso | null;
  desglosePorProveedor: ProveedorConsumo[];
  desglosePorEmpleado: EmpleadoConsumo[];
}

export interface StatsFilters {
  fecha?: string;
  vehiculoId?: number | null;
  empleadoIds?: number[];
}

export function getStats(range: StatsRange, opts?: StatsFilters): Promise<Stats> {
  const params = new URLSearchParams();
  if (opts?.fecha) params.set('fecha', opts.fecha);
  if (opts?.vehiculoId != null) params.set('vehiculoId', String(opts.vehiculoId));
  if (opts?.empleadoIds && opts.empleadoIds.length > 0) {
    params.set('empleadoIds', opts.empleadoIds.join(','));
  }
  const qs = params.toString();
  return api.get<Stats>(`/admin/stats/${range}${qs ? `?${qs}` : ''}`);
}
