import { api } from './api';

/**
 * Estadísticas de consumo para el panel del administrador. Espejo de
 * StatsResponse. El backend agrega SOLO por vehiculo (desglosePorVehiculo);
 * los desgloses por proveedor/operario no existen todavía en el backend.
 */
export type StatsRange = 'daily' | 'weekly' | 'monthly';

export interface VehiculoConsumo {
  vehiculoId: number;
  patente: string;
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
  desglosePorVehiculo: VehiculoConsumo[];
}

export function getStats(range: StatsRange): Promise<Stats> {
  return api.get<Stats>(`/admin/stats/${range}`);
}
