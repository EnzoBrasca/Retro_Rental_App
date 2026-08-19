import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { colors, fonts } from '../../constants/theme';
import { FilterDropdown } from '../fuel/FilterDropdown';
import { LoadDetailModal, type Row } from '../fuel/LoadDetailModal';
import { Loading, ErrorState, EmptyState } from '../fuel/ScreenState';
import { formatFecha, formatMoney, parseNumero } from '../../constants/labels';
import {
  getAdminTickets,
  anularTicket,
  type AdminTicketFilters,
  type Ticket,
} from '../../services/tickets';
import { getAdminVehiculos, tituloVehiculo, type Vehiculo } from '../../services/vehiculos';
import { getAdminHerramientas, type Herramienta } from '../../services/herramientas';
import { getAdminEmpleados, type Empleado } from '../../services/empleados';
import { getProveedores, type Proveedor } from '../../services/catalogos';

const PAGE_SIZE = 20;

// Retardo antes de que un monto tipeado dispare la consulta. 350 ms alcanza
// para cubrir el tecleo continuo sin que se sienta trabado al terminar.
const DEBOUNCE_MONTO_MS = 350;

// Sentinel de "todos" para los OptionChips, que son single-select y no admiten
// null como valor seleccionable. Mismo criterio que el filtro de la analítica.
const TODOS = -1;

type Catalogos = {
  vehiculos: Vehiculo[];
  herramientas: Herramienta[];
  empleados: Empleado[];
  proveedores: Proveedor[];
};

/**
 * Listado de todos los tickets cargados, con filtros y anulación.
 *
 * NO permite alta ni edición a propósito: un ticket lo carga el operario en el
 * momento de la carga, con la foto del comprobante. Dejar que el admin los
 * escriba a mano convertiría la rendición en algo que se puede fabricar.
 *
 * Vive en su propio archivo y no dentro de la pantalla del panel porque esa ya
 * pasaba las 900 líneas con dos ABMs adentro.
 */
export function TicketsABM() {
  const [catalogos, setCatalogos] = useState<Catalogos | null>(null);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<Row | null>(null);

  // Filtros aplicados. El monto se guarda como texto porque viene de inputs:
  // convertirlo en cada tecla haría que "1" y "1." se comporten distinto.
  const [empleadoId, setEmpleadoId] = useState<number>(TODOS);
  const [vehiculoId, setVehiculoId] = useState<number>(TODOS);
  const [montoMin, setMontoMin] = useState('');
  const [montoMax, setMontoMax] = useState('');
  const [incluirAnulados, setIncluirAnulados] = useState(false);

  // Los montos entran a los filtros CON RETARDO. Escribir "150000" en el input
  // son seis pulsaciones, y cada una disparaba su propia consulta paginada
  // contra la tabla que más crece del sistema: 1, 15, 150, 1500, 15000, 150000.
  // Los desplegables de operario y vehículo no necesitan esto: son selecciones
  // discretas, no tecleo.
  const [montoMinAplicado, setMontoMinAplicado] = useState('');
  const [montoMaxAplicado, setMontoMaxAplicado] = useState('');
  useEffect(() => {
    const id = setTimeout(() => {
      setMontoMinAplicado(montoMin);
      setMontoMaxAplicado(montoMax);
    }, DEBOUNCE_MONTO_MS);
    return () => clearTimeout(id);
  }, [montoMin, montoMax]);

  const filtros = useMemo<AdminTicketFilters>(
    () => ({
      empleadoId: empleadoId === TODOS ? null : empleadoId,
      vehiculoId: vehiculoId === TODOS ? null : vehiculoId,
      montoMin: parseMonto(montoMinAplicado),
      montoMax: parseMonto(montoMaxAplicado),
      incluirAnulados,
      size: PAGE_SIZE,
    }),
    [empleadoId, vehiculoId, montoMinAplicado, montoMaxAplicado, incluirAnulados],
  );

  // Los catálogos se piden una sola vez: alimentan los chips de filtro y la
  // resolución de IDs a nombres. Sin ellos la lista mostraría "Vehículo #3".
  useEffect(() => {
    Promise.all([
      getAdminVehiculos(),
      getAdminHerramientas(),
      getAdminEmpleados(),
      getProveedores(),
    ])
      .then(([vehiculos, herramientas, empleados, proveedores]) =>
        setCatalogos({ vehiculos, herramientas, empleados, proveedores }),
      )
      .catch((e) =>
        setError(e instanceof Error ? e.message : 'No se pudieron cargar los catálogos.'),
      );
  }, []);

  // Misma guarda que useFetch: cada carga se numera y solo la más reciente
  // escribe. Sin esto la respuesta de un filtro viejo puede llegar después de la
  // del nuevo y dejar la lista mostrando tickets que no corresponden al filtro
  // que dice la pantalla. En una rendición de gastos, eso es peor que un error.
  const generacion = useRef(0);

  const cargar = useCallback(
    async (destino: number) => {
      const propia = ++generacion.current;
      const vigente = () => generacion.current === propia;
      setLoading(true);
      setError(null);
      try {
        const res = await getAdminTickets({ ...filtros, page: destino });
        if (!vigente()) return;
        setTickets(res.content);
        setPage(res.page.number);
        setTotalPages(res.page.totalPages);
        setTotal(res.page.totalElements);
      } catch (e) {
        if (vigente()) {
          setError(e instanceof Error ? e.message : 'No se pudieron cargar los tickets.');
        }
      } finally {
        if (vigente()) setLoading(false);
      }
    },
    [filtros],
  );

  // Al cambiar cualquier filtro se vuelve a la página 0: quedarse en la 3 de un
  // resultado que ahora tiene una sola página deja la pantalla vacía sin motivo.
  useEffect(() => {
    cargar(0);
  }, [cargar]);

  const filas = useMemo(
    () => (catalogos ? tickets.map((t) => toRow(t, catalogos)) : []),
    [tickets, catalogos],
  );

  const anular = (fila: Row) => {
    Alert.alert(
      'Anular ticket',
      `¿Anular la carga de ${fila.litros} L por ${formatMoney(fila.costo)} del ${fila.fecha}?\n\n` +
        'El ticket deja de contar en la analítica y en el consumo del vehículo. ' +
        'No se borra: queda registrado quién lo anuló.',
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Anular',
          style: 'destructive',
          onPress: async () => {
            try {
              await anularTicket(fila.id);
              // Se recarga en vez de sacar la fila en memoria: anular cambia el
              // total y puede reordenar la página.
              await cargar(page);
            } catch (e) {
              Alert.alert('Error', e instanceof Error ? e.message : 'No se pudo anular.');
            }
          },
        },
      ],
    );
  };

  if (error && !catalogos) return <ErrorState message={error} />;
  if (!catalogos) return <Loading />;

  const empleadoOpts = [
    { key: TODOS, label: 'Todos' },
    ...catalogos.empleados.map((e) => ({ key: e.id, label: `${e.nombre} ${e.apellido}` })),
  ];
  const vehiculoOpts = [
    { key: TODOS, label: 'Todos' },
    ...catalogos.vehiculos.map((v) => ({ key: v.id, label: tituloVehiculo(v) })),
  ];
  return (
    <View style={{ marginTop: 4 }}>
      <Text style={styles.caption}>Todas las cargas registradas en la aplicación</Text>

      {/* Mismo panel de filtros que la analítica: dos desplegables por fila en
          vez de listas de chips. Con siete vehículos y varios operarios, los
          chips ocupaban media pantalla antes de mostrar un solo ticket. */}
      <View style={styles.panel}>
        <Text style={styles.panelLabel}>FILTROS</Text>

        <View style={styles.filterRow}>
          <View style={styles.filterRowItem}>
            <FilterDropdown
              mode="single"
              label="Operario"
              options={empleadoOpts}
              selected={empleadoId}
              onSelect={setEmpleadoId}
            />
          </View>
          <View style={styles.filterRowItem}>
            <FilterDropdown
              mode="single"
              label="Vehículo"
              options={vehiculoOpts}
              selected={vehiculoId}
              onSelect={setVehiculoId}
            />
          </View>
        </View>

        <Text style={[styles.fieldHint, { marginTop: 12 }]}>Monto entre</Text>
        <View style={styles.filterRow}>
          <TextInput
            style={[styles.input, styles.filterRowItem]}
            value={montoMin}
            onChangeText={setMontoMin}
            keyboardType="numeric"
            placeholder="Mínimo"
            placeholderTextColor={colors.textFaint}
          />
          <TextInput
            style={[styles.input, styles.filterRowItem]}
            value={montoMax}
            onChangeText={setMontoMax}
            keyboardType="numeric"
            placeholder="Máximo"
            placeholderTextColor={colors.textFaint}
          />
        </View>

        <Pressable style={styles.toggle} onPress={() => setIncluirAnulados((v) => !v)}>
          <View style={[styles.checkbox, incluirAnulados && styles.checkboxOn]}>
            {incluirAnulados && <Text style={styles.checkboxTick}>✓</Text>}
          </View>
          <Text style={styles.toggleText}>Mostrar tickets anulados</Text>
        </Pressable>
      </View>

      <Text style={styles.count}>
        {total} {total === 1 ? 'ticket' : 'tickets'}
        {totalPages > 1 ? ` · página ${page + 1} de ${totalPages}` : ''}
      </Text>

      {loading ? (
        <Loading />
      ) : error ? (
        <ErrorState message={error} />
      ) : filas.length === 0 ? (
        <EmptyState message="No hay tickets con esos filtros." />
      ) : (
        filas.map((fila) => {
          const anulado = fila.fechaAnulacion != null;
          return (
            <View key={fila.id} style={[styles.card, anulado && styles.cardAnulado]}>
              <Pressable style={styles.cardMain} onPress={() => setSelected(fila)}>
                <View style={{ flex: 1, paddingRight: 10 }}>
                  <Text style={styles.cardName} numberOfLines={1}>
                    {formatMoney(fila.costo)} · {fila.litros} L
                  </Text>
                  <Text style={styles.cardSub} numberOfLines={1}>
                    {fila.identificador} · {fila.proveedor}
                  </Text>
                  <Text style={styles.cardSub}>
                    {fila.fecha} · {fila.operario}
                  </Text>
                  {anulado && (
                    <Text style={styles.anuladoTag}>
                      ANULADO{fila.anuladoPor ? ` por ${fila.anuladoPor}` : ''}
                    </Text>
                  )}
                </View>
                <Text style={styles.chevron}>›</Text>
              </Pressable>
              {/* Un ticket anulado no se vuelve a anular: el backend responde
                  409 y el botón no tendría a dónde llevar. */}
              {!anulado && (
                <Pressable
                  style={styles.iconBtn}
                  onPress={() => anular(fila)}
                  accessibilityRole="button"
                  accessibilityLabel={`Anular la carga de ${fila.litros} litros de ${fila.identificador}`}
                >
                  <Text style={styles.iconBtnText}>✕</Text>
                </Pressable>
              )}
            </View>
          );
        })
      )}

      {totalPages > 1 && (
        <View style={styles.pager}>
          <Pressable
            style={[styles.pagerBtn, page === 0 && styles.pagerBtnOff]}
            disabled={page === 0}
            onPress={() => cargar(page - 1)}
          >
            <Text style={styles.pagerText}>‹ Anterior</Text>
          </Pressable>
          <Pressable
            style={[styles.pagerBtn, page >= totalPages - 1 && styles.pagerBtnOff]}
            disabled={page >= totalPages - 1}
            onPress={() => cargar(page + 1)}
          >
            <Text style={styles.pagerText}>Siguiente ›</Text>
          </Pressable>
        </View>
      )}

      <LoadDetailModal row={selected} onClose={() => setSelected(null)} />
    </View>
  );
}

// "" y basura no son 0: son "sin filtro". Devolver 0 filtraría por monto cero.
function parseMonto(texto: string): number | null {
  const n = parseNumero(texto);
  return Number.isFinite(n) ? n : null;
}

/**
 * Resuelve un ticket contra los catálogos. El backend manda IDs, no nombres:
 * mostrarlos crudos dejaría "Vehículo #3 · Proveedor #1" en pantalla.
 */
function toRow(t: Ticket, cat: Catalogos): Row & { operario: string } {
  // Exactamente uno de los dos viene con valor (ver services/tickets.ts).
  const vehiculo =
    t.idVehiculo != null ? cat.vehiculos.find((v) => v.id === t.idVehiculo) : undefined;
  const herramienta =
    t.idHerramienta != null ? cat.herramientas.find((h) => h.id === t.idHerramienta) : undefined;
  const proveedor = cat.proveedores.find((p) => p.id === t.idProveedor);
  const empleado = cat.empleados.find((e) => e.username === t.empleadoUsername);
  return {
    id: t.id,
    identificador: vehiculo
      ? tituloVehiculo(vehiculo)
      : herramienta
        ? herramienta.nombre
        : t.idVehiculo != null
          ? `Vehículo #${t.idVehiculo}`
          : `Herramienta #${t.idHerramienta}`,
    // Row.tipoVehiculo solo maneja el icono de vehículo: una herramienta no
    // tiene uno propio, así que cae en un fallback (tipoCombustible queda
    // null, así que la ficha no muestra un combustible que no aplica).
    tipoVehiculo: vehiculo?.tipoVehiculo ?? 'CAMION',
    tipoCombustible: vehiculo?.tipoCombustible ?? null,
    fecha: formatFecha(t.fechaCarga),
    fechaCarga: t.fechaCarga,
    proveedor: proveedor?.nombre ?? `Proveedor #${t.idProveedor}`,
    litros: t.litros,
    precioUnitario: t.precioUnitario,
    costo: t.litros * t.precioUnitario,
    ticketFotoUrl: t.ticketFotoUrl,
    anuladoPor: t.anuladoPorUsername,
    fechaAnulacion: t.fechaAnulacion,
    operario: empleado ? `${empleado.nombre} ${empleado.apellido}` : t.empleadoUsername,
  };
}

const styles = StyleSheet.create({
  // Los tres de abajo son los mismos valores que usa la analítica: el panel de
  // filtros tiene que leerse como el mismo componente en las dos pestañas.
  caption: {
    fontSize: 11.5,
    color: colors.textFaint,
    marginBottom: 16,
    marginTop: 8,
    fontFamily: fonts.sans,
  },
  panel: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 13,
    padding: 14,
    marginBottom: 16,
  },
  panelLabel: {
    fontSize: 10,
    letterSpacing: 1.5,
    color: colors.primary,
    marginBottom: 10,
    fontFamily: fonts.sansSemi,
  },
  filterRow: { flexDirection: 'row', gap: 10 },
  filterRowItem: { flex: 1 },
  fieldHint: { fontSize: 11, color: colors.textFaint, marginBottom: 6, fontFamily: fonts.sans },
  // Espeja el `trigger` de FilterDropdown para que el input de monto y el
  // desplegable de al lado tengan la misma altura, borde y tipografía.
  input: {
    backgroundColor: colors.surfaceInput,
    borderWidth: 1,
    borderColor: colors.borderInput,
    borderRadius: 10,
    paddingHorizontal: 13,
    paddingVertical: 9,
    color: colors.text,
    fontSize: 13,
    fontFamily: fonts.sansMed,
  },
  toggle: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 14 },
  checkbox: {
    width: 18,
    height: 18,
    borderRadius: 4,
    borderWidth: 1,
    borderColor: colors.borderSoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkboxOn: { backgroundColor: colors.primary, borderColor: colors.primary },
  checkboxTick: { fontSize: 12, color: colors.bgDeep, fontFamily: fonts.sansSemi },
  toggleText: { fontSize: 12, color: colors.textMuted, fontFamily: fonts.sans },
  count: {
    fontSize: 11,
    color: colors.textFaint,
    fontFamily: fonts.sans,
    marginTop: 16,
    marginBottom: 10,
  },
  card: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  // El anulado se atenúa en vez de ocultarse: sigue siendo consultable.
  cardAnulado: { opacity: 0.5 },
  cardMain: { flex: 1, flexDirection: 'row', alignItems: 'center' },
  cardName: { fontFamily: fonts.displayBold, fontSize: 15, color: colors.text },
  cardSub: { fontSize: 11, color: colors.textFaint, marginTop: 2, fontFamily: fonts.sans },
  anuladoTag: {
    fontSize: 10,
    color: colors.danger,
    marginTop: 4,
    fontFamily: fonts.sansSemi,
    letterSpacing: 0.5,
  },
  chevron: { fontSize: 22, color: colors.textDim, marginLeft: 4 },
  iconBtn: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: colors.bg,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.borderSoft,
  },
  iconBtnText: { color: colors.danger, fontSize: 14, fontFamily: fonts.sansSemi },
  pager: { flexDirection: 'row', gap: 10, marginTop: 8 },
  pagerBtn: {
    flex: 1,
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
    backgroundColor: '#1b1d20',
  },
  pagerBtnOff: { opacity: 0.4 },
  pagerText: { fontFamily: fonts.sansSemi, fontSize: 13, color: colors.text },
});
