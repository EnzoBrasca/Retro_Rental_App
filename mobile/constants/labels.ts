import { FC } from 'react';
import { SvgProps } from 'react-native-svg';
import { Estado, TipoCombustible, TipoVehiculo } from '../services/vehiculos';

import IconPickup from '../assets/icons/005-camioneta.svg';
import IconExcavator from '../assets/icons/006-excavador.svg';
import IconTruck from '../assets/icons/007-camin-de-carga.svg';

/** Etiquetas legibles para los enums que devuelve el backend. */
export const combustibleLabel: Record<TipoCombustible, string> = {
  NAFTA_SUPER: 'Nafta Súper',
  NAFTA_PREMIUM: 'Nafta Premium',
  GASOIL_GRADO_2: 'Gasoil Grado 2',
  GASOIL_GRADO_3: 'Gasoil Grado 3',
  GNC: 'GNC',
  MEZCLA: 'Mezcla (nafta + aceite)',
};

export const tipoVehiculoLabel: Record<TipoVehiculo, string> = {
  MAQUINA: 'Máquina',
  CAMIONETA: 'Camioneta',
  CAMION: 'Camión',
};

export const estadoLabel: Record<Estado, string> = {
  DISPONIBLE: 'Disponible',
  EN_USO: 'En uso',
  EN_MANTENIMIENTO: 'En mantenimiento',
};

/** Ícono según el tipo de vehiculo (los reales no traen ícono propio). */
export function iconForTipoVehiculo(t: TipoVehiculo): FC<SvgProps> {
  if (t === 'CAMIONETA') return IconPickup;
  if (t === 'CAMION') return IconTruck;
  return IconExcavator; // MAQUINA
}

/**
 * Opción "HERRAMIENTA" del selector de tipo en el ABM de vehículos. Es
 * SOLO de UI: no existe en el enum TipoVehiculo del backend (una herramienta
 * es una entidad/tabla distinta) y nunca se manda como `tipoVehiculo` en un
 * request. Se usa para que el formulario decida qué campos mostrar y a qué
 * endpoint mandar el alta/edición.
 */
export const TIPO_HERRAMIENTA = 'HERRAMIENTA' as const;
export type TipoSeleccionVehiculo = TipoVehiculo | typeof TIPO_HERRAMIENTA;

export const tipoSeleccionLabel: Record<TipoSeleccionVehiculo, string> = {
  ...tipoVehiculoLabel,
  HERRAMIENTA: 'Herramienta',
};

/**
 * Ícono para una fila de la lista unificada de vehículos y herramientas. Una
 * herramienta no tiene ícono propio en assets/icons (no es un vehículo), así
 * que se representa con un emoji en vez de sumar un SVG nuevo solo para esto.
 */
export function iconForTipoSeleccion(t: TipoSeleccionVehiculo): FC<SvgProps> | null {
  if (t === TIPO_HERRAMIENTA) return null;
  return iconForTipoVehiculo(t);
}

/** Formatea un monto en pesos argentinos: 53070 -> "$53.070". */
export function formatMoney(n: number): string {
  return '$' + Math.round(n).toLocaleString('es-AR');
}

const MESES = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

/** Formatea un ISO date (yyyy-mm-dd) a "06 Jul 2026". */
export function formatDay(iso: string): string {
  // Las fechas date-only ("AAAA-MM-DD") las interpreta new Date() como medianoche
  // UTC; en zonas horarias UTC- (Argentina es UTC-3) eso se corre al día ANTERIOR
  // al leer los componentes locales. Parseamos las partes a mano para tratarlas
  // como fecha local y evitar el off-by-one. Los datetime completos siguen igual.
  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  const d = dateOnly
    ? new Date(Number(dateOnly[1]), Number(dateOnly[2]) - 1, Number(dateOnly[3]))
    : new Date(iso);
  if (isNaN(d.getTime())) return iso;
  const dd = String(d.getDate()).padStart(2, '0');
  return `${dd} ${MESES[d.getMonth()]} ${d.getFullYear()}`;
}

/** Formatea un ISO datetime a "05 Jul 2026 · 14:20". */
export function formatFecha(iso: string): string {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  const dd = String(d.getDate()).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${dd} ${MESES[d.getMonth()]} ${d.getFullYear()} · ${hh}:${mm}`;
}
