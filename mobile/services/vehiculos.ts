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

// Una máquina vial no está patentada: se identifica por un número interno que
// asigna la empresa. Misma idea que `etiquetaUso`: el backend manda el valor y
// el cliente decide cómo rotularlo, porque la etiqueta es presentación.
export function etiquetaIdentificador(tipo: TipoVehiculo | null): string {
  return tipo === 'MAQUINA' ? 'Número interno' : 'Patente';
}

export function placeholderIdentificador(tipo: TipoVehiculo | null): string {
  return tipo === 'MAQUINA' ? 'M-01' : 'AB123CD';
}

// Solo una máquina exige modelo: su interno dice CUÁL es, no QUÉ es. Un camión
// ya queda identificado por su patente. Espeja TipoVehiculo.requiereModelo().
export function requiereModelo(tipo: TipoVehiculo | null): boolean {
  return tipo === 'MAQUINA';
}

/**
 * Cómo se nombra un vehículo delante de una persona.
 *
 * En una máquina manda el MODELO: "CAT 320D (M-01)". El número interno es una
 * numeración inventada por la empresa, así que como título no le dice nada a
 * nadie; va entre paréntesis, que es lo único que separa dos máquinas del mismo
 * modelo. En un camión o camioneta manda la patente, que es lo que está
 * pintado en el vehículo.
 *
 * El fallback importa: una máquina cargada antes de que existiera el campo
 * modelo no tiene ninguno, y sin esto el título quedaría en "(M-01)".
 */
export function tituloVehiculo(v: Vehiculo): string {
  if (v.tipoVehiculo === 'MAQUINA' && v.modelo) {
    return `${v.modelo} (${v.identificador})`;
  }
  return v.identificador;
}

// Texto sobre el que buscar un vehículo. Incluye el modelo porque es lo que el
// operario ve como título de la card: si buscara solo por identificador,
// tipear "CAT" no encontraría la máquina que tiene delante en pantalla.
export function textoBusquedaVehiculo(v: Vehiculo): string {
  return `${v.identificador} ${v.modelo ?? ''}`.toLowerCase();
}

// Etiqueta del campo donde el empleado anota la lectura al cargar combustible.
// Nombra el instrumento a propósito: en el campo se lee un horómetro o un
// odómetro, y decirlo así evita que alguien anote kilómetros en una máquina.
// Sufijo con el que se muestra un consumo ya calculado, a partir de la unidad
// que mandó el servidor. Es la forma preferida: no necesita el vehículo entero,
// solo el `unidadUso` que viaja en la respuesta.
export function sufijoConsumo(unidad: UnidadUso): string {
  return unidad === 'HORAS' ? 'L/h' : 'L/100km';
}

// Consumo ya formateado para la tarjeta del panel de estadísticas, que expresa
// los vehículos de ruta en km/L y no en L/100km como el resto de la app.
//
// Son la misma medida dada vuelta (km/L = 100 / L/100km), no dos datos
// distintos, pero se leen al revés: en L/100km más bajo es mejor y en km/L más
// alto es mejor. El ABM sigue en L/100km porque es la unidad en la que se carga
// y se persiste `consumoPromedio`, así que `sufijoConsumo` queda intacta.
//
// La inversión se hace acá y no en el backend a propósito: el APK ya instalado
// renderiza `consumoPeriodo` con su etiqueta vieja, y si el servidor mandara
// km/L esa versión mostraría el número nuevo rotulado "L/100km".
export function consumoParaStats(valor: number, unidad: UnidadUso): string {
  if (unidad === 'HORAS') {
    return `${valor} L/h`;
  }
  // El backend redondea a 2 decimales, así que un consumo absurdamente bajo
  // puede llegar como 0 y la inversión daría Infinity. Sin dato utilizable se
  // muestra el mismo guion que la tarjeta usa cuando no hay consumo.
  if (valor <= 0) {
    return '—';
  }
  return `${(100 / valor).toFixed(2)} km/L`;
}

// Igual que `sufijoConsumo` pero derivando la unidad del tipo, para cuando no
// hay respuesta del servidor de la cual leerla.
export function unidadConsumo(tipo: TipoVehiculo | null): string {
  return sufijoConsumo(unidadDeTipo(tipo));
}

export function etiquetaLectura(tipo: TipoVehiculo | null): string {
  return unidadDeTipo(tipo) === 'HORAS' ? 'Horas del horómetro' : 'Kilómetros del odómetro';
}

export function etiquetaConsumo(tipo: TipoVehiculo | null): string {
  return unidadDeTipo(tipo) === 'HORAS'
    ? 'Consumo promedio (L/h)'
    : 'Consumo promedio (L/100km)';
}
// MEZCLA es nafta con aceite: solo la usan herramientas como la motosierra, un
// vehículo nunca la tiene como tipoCombustible fijo.
export type TipoCombustible =
  | 'NAFTA_SUPER'
  | 'NAFTA_PREMIUM'
  | 'GASOIL_GRADO_2'
  | 'GASOIL_GRADO_3'
  | 'GNC'
  | 'MEZCLA';

export interface Vehiculo {
  id: number;
  // Identificador único y visible: patente en CAMION/CAMIONETA, número interno
  // en MAQUINA. Usar `etiquetaIdentificador(tipoVehiculo)` para rotularlo.
  identificador: string;
  // Modelo descriptivo (ej. "CAT 320D"). No es único: puede haber dos máquinas
  // del mismo modelo. null en vehículos cargados antes del cambio.
  modelo: string | null;
  tipoVehiculo: TipoVehiculo;
  tipoCombustible: TipoCombustible;
  estado: Estado;
  capacidadTanque: number;
  // Contador de uso acumulado. `unidadUso` dice en qué se mide: HORAS en una
  // máquina vial, KM en un camión o camioneta.
  usoAcumulado: number;
  unidadUso: UnidadUso;
  // Consumo sobre toda la historia de cargas. Lo carga el admin en el alta y a
  // partir de la segunda carga lo reemplaza el cálculo real.
  consumoPromedio: number;
  // Consumo de las últimas cargas. null hasta que haya dos con lectura.
  consumoReciente: number | null;
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
  identificador: string;
  // Obligatorio si tipoVehiculo es MAQUINA (lo valida el backend).
  modelo?: string | null;
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
