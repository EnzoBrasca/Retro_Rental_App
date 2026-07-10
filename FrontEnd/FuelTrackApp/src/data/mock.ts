/**
 * Static mock data — no database, exactly as the prototype used.
 * This is the seam where a real API will plug in later.
 */
import { colors } from '../theme';

import IconPickup from '../assets/icons/005-camioneta.svg';
import IconExcavator from '../assets/icons/006-excavador.svg';
import IconTruck from '../assets/icons/007-camin-de-carga.svg';

export type Role = 'employee' | 'admin';

export type Credential = {
  email: string;
  password: string;
  role: Role;
  name: string;
};

/** Hardcoded accounts for the mockup role validation. */
export const CREDENTIALS: Credential[] = [
  { email: 'juan.perez@vialsur.com', password: '123456789', role: 'employee', name: 'Juan Pérez' },
  { email: 'admin@vialsur.com', password: 'admin123', role: 'admin', name: 'Admin' },
];

export type Machine = {
  id: string;
  icon: any;
  name: string;
  operator: string;
  lastRefuel: string;
  consumption: string;
};

export let MACHINERY: Machine[] = [
  { id: 'm0', icon: IconPickup, name: 'Toyota Hilux SRV', operator: 'Juan Pérez', lastRefuel: '05 Jul 14:20', consumption: '11.2 L/100km' },
  { id: 'm1', icon: IconExcavator, name: 'Retroexcavadora CAT 320', operator: 'Carlos Díaz', lastRefuel: 'Hoy 08:10', consumption: '15.4 L/100km' },
  { id: 'm2', icon: IconExcavator, name: 'Motoniveladora JD 670G', operator: 'Ana López', lastRefuel: 'Ayer 17:40', consumption: '18.1 L/100km' },
  { id: 'm3', icon: IconTruck, name: 'Camión Volquete Iveco Trakker', operator: 'Marta Ruiz', lastRefuel: '04 Jul 11:25', consumption: '22.5 L/100km' },
  { id: 'm4', icon: IconExcavator, name: 'Cargadora Frontal CAT 950', operator: 'Sin asignar', lastRefuel: '02 Jul 09:00', consumption: '16.8 L/100km' },
];

export const updateMachinery = (newMachinery: Machine[]) => {
  MACHINERY = newMachinery;
};

export type Load = {
  id: string;
  icon: any;
  vehicle: string;
  date: string;
  provider: string;
  liters: string;
  cost: string;
  status: 'Verificado' | 'Pendiente';
};

export const HISTORY: Load[] = [
  { id: 'h1', icon: IconPickup, vehicle: 'Toyota Hilux SRV', date: '05 Jul 2026 · 14:20', provider: 'YPF', liters: '58 L', cost: '$53.070', status: 'Verificado' },
  { id: 'h2', icon: IconExcavator, vehicle: 'Retroexcavadora CAT 320', date: '05 Jul 2026 · 08:10', provider: 'Shell', liters: '120 L', cost: '$109.800', status: 'Pendiente' },
  { id: 'h3', icon: IconTruck, vehicle: 'Camión Iveco Trakker', date: '04 Jul 2026 · 11:25', provider: 'Axion', liters: '210 L', cost: '$189.000', status: 'Verificado' },
  { id: 'h4', icon: IconExcavator, vehicle: 'Motoniveladora JD 670G', date: '03 Jul 2026 · 17:40', provider: 'Puma', liters: '95 L', cost: '$86.450', status: 'Verificado' },
  { id: 'h5', icon: IconPickup, vehicle: 'Toyota Hilux SRV', date: '02 Jul 2026 · 09:15', provider: 'YPF', liters: '52 L', cost: '$47.580', status: 'Verificado' },
  { id: 'h6', icon: IconExcavator, vehicle: 'Cargadora CAT 950', date: '01 Jul 2026 · 16:05', provider: 'Petrobras', liters: '140 L', cost: '$127.400', status: 'Pendiente' },
];

export const PROFILE_FIELDS = [
  { label: 'Nombre completo', value: 'Juan Pérez' },
  { label: 'Correo', value: 'juan.perez@vialsur.com' },
  { label: 'Teléfono', value: '+54 9 342 555-1234' },
];

export const TUTORIAL_STEPS = [
  { label: 'Paso 1 de 4', title: 'Tu flota, de un vistazo', text: 'Acá ves tu vehículo asignado, su nivel de combustible y toda la maquinaria a tu cargo.' },
  { label: 'Paso 2 de 4', title: 'Escaneá el ticket', text: 'Tocá el botón central para fotografiar el ticket de carga. La app extrae los datos automáticamente.' },
  { label: 'Paso 3 de 4', title: 'Revisá el historial', text: 'Consultá todas las cargas registradas de tu flota, con su estado y monto.' },
  { label: 'Paso 4 de 4', title: 'Gestioná tu cuenta', text: 'Desde tu perfil editás tus datos, cambiás el idioma y cerrás sesión.' },
];

/** Simulated OCR output shown after a ticket photo is captured. */
export const SCAN_DEFAULTS = {
  proveedor: 'YPF',
  litros: '58,00',
  precioL: '915,00',
  patente: 'AB 123 CO',
  fecha: '05/07/2026',
  combustible: 'Diésel',
};

// ---------- Admin analytics ----------
type Range = '1d' | '7d' | '30d';

export const rangeLabel = (r: Range) =>
  r === '1d' ? '05 Jul 2026' : r === '7d' ? '29 Jun – 05 Jul 2026' : '06 Jun – 05 Jul 2026';

export const fmt = (n: number) => '$' + Math.round(n).toLocaleString('es-AR');

export const kpis = (r: Range) => ({
  total: fmt(r === '1d' ? 82400 : r === '7d' ? 613300 : 2480000),
  liters: r === '1d' ? '90 L' : r === '7d' ? '675 L' : '2.720 L',
  loads: r === '1d' ? 2 : r === '7d' ? 12 : 48,
  avg: fmt(r === '1d' ? 41200 : r === '7d' ? 51108 : 51667),
});

const rangeMult = (r: Range) => (r === '1d' ? 0.15 : r === '7d' ? 1 : 4);

export const vehicleData = (r: Range) => {
  const mult = rangeMult(r);
  return [
    { l: 'Hilux SRV', v: 195, c: '#F5C518' },
    { l: 'CAT 320', v: 148, c: '#ffd94d' },
    { l: 'JD 670G', v: 110, c: '#c99a00' },
    { l: 'CAT 950', v: 80, c: '#8a7220' },
    { l: 'Trakker', v: 45, c: '#5f5220' },
  ].map((d) => ({ ...d, amount: fmt(d.v * 1000 * mult) }));
};

export const providerData = (r: Range) => {
  const mult = rangeMult(r);
  return [
    { l: 'YPF', v: 185, c: '#F5C518' },
    { l: 'Shell', v: 142, c: '#ffd94d' },
    { l: 'Axion', v: 110, c: '#c99a00' },
    { l: 'Puma', v: 74, c: '#8a7220' },
    { l: 'Petrobras', v: 52, c: '#5f5220' },
  ].map((d) => ({ ...d, amount: fmt(d.v * 1000 * mult) }));
};

export const userData = (r: Range) => {
  const mult = rangeMult(r);
  return [
    { l: 'Juan Pérez', v: 168 },
    { l: 'Ana López', v: 132 },
    { l: 'Carlos Díaz', v: 108 },
    { l: 'Marta Ruiz', v: 76 },
    { l: 'Sin asignar', v: 41 },
  ].map((d, i) => ({ ...d, amount: fmt(d.v * 1000 * mult), highlight: i === 0 }));
};

export type { Range };
