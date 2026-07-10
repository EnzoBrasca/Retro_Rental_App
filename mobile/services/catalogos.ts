import { api } from './api';
import { TipoCombustible } from './vehiculos';

/**
 * Catálogos de referencia para el formulario de carga de tickets: proveedores
 * (estaciones) y precios vigentes. También se usan en el Historial para
 * resolver el nombre del proveedor y el monto de cada ticket.
 */
export type Servicio = 'COMBUSTIBLE' | 'REPUESTOS' | 'OTROS';

export interface Proveedor {
  id: number;
  nombre: string;
  cuit: string;
  servicio: Servicio;
}

export interface Precio {
  id: number;
  tipoCombustible: TipoCombustible | null;
  precioUnitario: number;
  servicio: Servicio;
  // Proveedor dueño de este precio (null en precios legacy sin proveedor). El
  // precio del ticket se elige por (proveedor + combustible del vehículo).
  idProveedor: number | null;
  fechaDesde: string;
  fechaHasta: string | null;
}

export function getProveedores(): Promise<Proveedor[]> {
  return api.get<Proveedor[]>('/proveedores');
}

export function getPrecios(): Promise<Precio[]> {
  return api.get<Precio[]>('/precios');
}
