export type ISODate = string;

export enum TipoTelefono {
  PERSONAL = 'PERSONAL',
  EMPRESARIAL = 'EMPRESARIAL',
}

export enum TipoCombustible {
  DIESEL = 'DIESEL',
  NAFTA = 'NAFTA',
  GNC = 'GNC',
}

export enum Estado {
  DISPONIBLE = 'DISPONIBLE',
  EN_USO = 'EN_USO',
  EN_MANTENIMIENTO = 'EN_MANTENIMIENTO',
}

export enum TipoVehiculo {
  MAQUINA = 'MAQUINA',
  CAMIONETA = 'CAMIONETA',
}

export enum Servicios {
  COMBUSTIBLE = 'COMBUSTIBLE',
  REPUESTOS = 'REPUESTOS',
  OTROS = 'OTROS',
}

export interface Telefono {
  codArea: string;
  numero: string;
  tipo: TipoTelefono;
}

export interface Direccion {
  calle: string;
  numero: string;
  cp: string;
  barrio: string;
  departamento: string;
}

export interface Precio {
  precioUnitario: number;
  fechaDesde: ISODate;
  fechaHasta: ISODate;
}

export interface Persona {
  nombre: string;
  apellido: string;
  documento: string;
  telefono: Telefono;
  direccion: Direccion;
}

export interface Empleado extends Persona {
  vehiculos: Vehiculo[];
  fechaAlta: ISODate;
  fechaBaja: ISODate | null;
  jefe: Administrador;
  tickets: Ticket[];
}

export interface Administrador extends Persona {
  empleados: Empleado[];
  vehiculos: Vehiculo[];
}

export interface Vehiculo {
  patente: string;
  tipoCombustible: TipoCombustible;
  marca: string;
  modelo: string;
  capacidadTanque: number;
  nivelActual: number;
  estado: Estado;
  operario: Empleado;
  fechaUltimoMantenimiento: ISODate;
  kilometrajeActual: number;
  proximoMantenimiento: ISODate;
  tipoVehiculo: TipoVehiculo;
  consumoPromedio: number;
}

export interface Proveedor {
  nombre: string;
  cuit: string;
  tickets: Ticket[];
  tipoServicio: Servicios;
}

export interface Ticket {
  importeTotal: number;
  precioUnitario: Precio;
  litros: number;
  fechaCarga: ISODate;
  proveedor: Proveedor;
  responsableCarga: Empleado;
}
