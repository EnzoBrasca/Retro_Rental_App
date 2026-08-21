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
  // No es un combustible cargable: es el insumo de la mezcla. Aparece en el
  // catálogo de precios, nunca como opción de carga.
  ACEITE: 'Aceite 2 tiempos',
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

/**
 * Convierte a número un valor tipeado por una persona en un teclado numérico.
 *
 * POR QUÉ EXISTE. Cada formulario hacía `parseFloat(texto.replace(',', '.'))`.
 * `String.replace` con un string reemplaza SOLO la primera ocurrencia y no toca
 * los puntos de miles, así que con la convención argentina —punto para miles,
 * coma para decimales— salían números válidos y equivocados:
 *
 *   "12.500,75"  ->  12,5        (un precio por litro con error de mil veces)
 *   "1.234,5"    ->  1,234       (una carga de camión como 1,2 litros)
 *
 * Ninguno falla con un error: se guardan y siguen.
 *
 * Devuelve NaN si el texto no es un número, incluida la basura pegada al final
 * ("152340km"), que `parseFloat` se comía sin chistar. Todos los llamadores ya
 * validan con `<= 0`, `>= 0` o `Number.isFinite`, así que NaN los frena solo.
 */
export function parseNumero(texto: string): number {
  const limpio = texto.trim();
  // Solo dígitos, separadores y un signo adelante. Cualquier otra cosa es
  // basura y se rechaza entera, en vez de recortarla.
  if (!/^-?[\d.,]+$/.test(limpio)) return NaN;

  const puntos = (limpio.match(/\./g) ?? []).length;
  const comas = (limpio.match(/,/g) ?? []).length;

  let decimal: '.' | ',' | null = null;
  if (puntos > 0 && comas > 0) {
    // Con los dos presentes manda el ÚLTIMO: cubre la convención argentina
    // ("1.234,5") y también la inglesa ("1,234.5").
    decimal = limpio.lastIndexOf(',') > limpio.lastIndexOf('.') ? ',' : '.';
  } else if (comas === 1) {
    decimal = ',';
  } else if (puntos === 1) {
    // UN PUNTO SOLO ES AMBIGUO: "1.234" son 1234 litros, pero "58.5" son 58,5.
    // Se resuelve por la forma del grupo: exactamente 3 dígitos después, 1 a 3
    // antes y sin cero adelante es un separador de miles ("0.500" no lo es,
    // nadie escribe cero como millar). Cualquier otra forma es un decimal.
    decimal = /^-?[1-9]\d{0,2}\.\d{3}$/.test(limpio) ? null : '.';
  } else if (comas > 1 || puntos > 1) {
    // Varios separadores del mismo tipo solo tienen sentido como miles.
    decimal = null;
  }

  const miles = decimal === ',' ? '.' : decimal === '.' ? ',' : puntos > 0 ? '.' : ',';

  let entera = limpio;
  let decimales = '';
  if (decimal !== null) {
    const corte = limpio.lastIndexOf(decimal);
    entera = limpio.slice(0, corte);
    decimales = limpio.slice(corte + 1);
    if (!/^\d+$/.test(decimales)) return NaN;
  }

  // La parte entera tiene que ser o un número pelado, o grupos de miles BIEN
  // formados. Sin este chequeo "12,5,3" pasaría como 1253: sacar los
  // separadores sin mirar la forma convierte cualquier cosa en un número.
  const sep = miles === '.' ? '\\.' : ',';
  if (!new RegExp(`^-?(\\d+|\\d{1,3}(${sep}\\d{3})+)$`).test(entera)) return NaN;

  const n = Number(entera.split(miles).join('') + (decimales ? `.${decimales}` : ''));
  return Number.isFinite(n) ? n : NaN;
}

/**
 * Igual que `parseNumero` pero para los campos que el backend guarda como
 * entero: la lectura del odómetro/horómetro y la capacidad del tanque.
 *
 * `parseInt("1.523", 10)` devolvía 1 — una lectura de 1.523 km entraba como
 * 1 km y le arruinaba al vehículo el cálculo de consumo real.
 *
 * Un decimal se REDONDEA, no se trunca. No se puede persistir (usoAcumulado es
 * Integer en el backend), y redondear pierde menos de una unidad; truncar es lo
 * que venía haciendo `parseInt`.
 */
export function parseEntero(texto: string): number {
  const n = parseNumero(texto);
  return Number.isFinite(n) ? Math.round(n) : NaN;
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
