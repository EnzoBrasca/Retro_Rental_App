import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect } from 'expo-router';
import { colors, fonts } from '../../constants/theme';
import { Loading, ErrorState, EmptyState } from '../../components/fuel/ScreenState';
import { LoadDetailModal } from '../../components/fuel/LoadDetailModal';
import { useFetch } from '../../hooks/useFetch';
import { getMisTickets, Ticket } from '../../services/tickets';
import { getVehiculos, tituloVehiculo, Vehiculo } from '../../services/vehiculos';
import { getHerramientas, Herramienta } from '../../services/herramientas';
import { getProveedores, Proveedor } from '../../services/catalogos';
import { formatFecha, formatMoney } from '../../constants/labels';
// El tipo de la fila vive en LoadDetailModal, que es quien la consume. Se
// reexporta para no romper a quien la importe desde acá.
import type { Row } from '../../components/fuel/LoadDetailModal';

export type { Row };

type Filtro = 'Todos' | 'Esta semana' | 'Este mes';
const FILTERS: Filtro[] = ['Todos', 'Esta semana', 'Este mes'];

// Card colapsada: solo litros e importe total. El detalle (proveedor, fecha,
// combustible, precio/L y la foto del ticket) se abre al tocarla.
const LoadRow = memo(function LoadRow({ item, onPress }: { item: Row; onPress: (r: Row) => void }) {
  return (
    <Pressable style={styles.card} onPress={() => onPress(item)}>
      <View style={styles.litrosBox}>
        <Text style={styles.litros}>{item.litros}</Text>
        <Text style={styles.litrosUnit}>L</Text>
      </View>
      <View style={styles.costWrap}>
        <Text style={styles.cost}>{formatMoney(item.costo)}</Text>
        <Text style={styles.chevron}>›</Text>
      </View>
    </Pressable>
  );
});

export default function HistorialScreen() {
  const [activeFilter, setActiveFilter] = useState<Filtro>('Todos');
  const [selected, setSelected] = useState<Row | null>(null);

  // El historial viene PAGINADO del backend. useFetch trae la primera página
  // junto con los catálogos; las siguientes se van agregando al llegar al final
  // de la lista (ver cargarMas). Los catálogos no se vuelven a pedir.
  const { data, loading, error, refetch } = useFetch(async () => {
    const [pagina, vehiculos, herramientas, proveedores] = await Promise.all([
      getMisTickets(0),
      getVehiculos(),
      getHerramientas(),
      getProveedores(),
    ]);
    return { pagina, vehiculos, herramientas, proveedores };
  });

  // Tickets acumulados de todas las páginas traídas hasta ahora.
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [ultimaPagina, setUltimaPagina] = useState(0);
  const [hayMas, setHayMas] = useState(false);
  const [cargandoMas, setCargandoMas] = useState(false);

  // Cada vez que useFetch resuelve (montaje o refetch al ganar foco) se
  // REEMPLAZA el acumulado con la página 0: si el empleado acaba de registrar
  // una carga, tiene que verla arriba de todo y sin duplicados.
  useEffect(() => {
    if (!data) return;
    setTickets(data.pagina.content);
    setUltimaPagina(0);
    setHayMas(data.pagina.page.number + 1 < data.pagina.page.totalPages);
  }, [data]);

  const cargarMas = useCallback(async () => {
    if (cargandoMas || !hayMas) return;
    setCargandoMas(true);
    try {
      const siguiente = ultimaPagina + 1;
      const pagina = await getMisTickets(siguiente);
      // Se filtra por id antes de concatenar: si entró una carga nueva entre
      // dos pedidos, el corte de página se corre y una fila podría repetirse.
      setTickets((previos) => {
        const vistos = new Set(previos.map((t) => t.id));
        return [...previos, ...pagina.content.filter((t) => !vistos.has(t.id))];
      });
      setUltimaPagina(siguiente);
      setHayMas(pagina.page.number + 1 < pagina.page.totalPages);
    } catch {
      // Silencioso a propósito: la lista ya cargada sigue usable y el próximo
      // scroll reintenta. Un error acá no debe tapar el historial visible.
    } finally {
      setCargandoMas(false);
    }
  }, [cargandoMas, hayMas, ultimaPagina]);

  // El tab de Historial queda montado en el navegador de pestañas, así que
  // useFetch (fetch-on-mount) no vuelve a correr al volver desde otra pestaña.
  // Tras registrar una carga en Escanear, el usuario necesita ver el ticket
  // nuevo sin re-loguearse: refrescamos cada vez que la pestaña gana foco. El
  // primer foco coincide con el montaje (useFetch ya trae los datos), así que
  // lo salteamos para no disparar dos requests iguales al abrir.
  const primerFoco = useRef(true);
  useFocusEffect(
    useCallback(() => {
      if (primerFoco.current) {
        primerFoco.current = false;
        return;
      }
      refetch();
    }, [refetch]),
  );

  // Arma las filas resueltas una sola vez por respuesta.
  const rows = useMemo<Row[]>(() => {
    if (!data) return [];
    const vById = new Map<number, Vehiculo>(data.vehiculos.map((v) => [v.id, v]));
    const hById = new Map<number, Herramienta>(data.herramientas.map((h) => [h.id, h]));
    const pById = new Map<number, Proveedor>(data.proveedores.map((p) => [p.id, p]));

    return tickets.map((t: Ticket) => {
      // Exactamente uno de los dos viene con valor (ver services/tickets.ts).
      const v = t.idVehiculo != null ? vById.get(t.idVehiculo) : undefined;
      const h = t.idHerramienta != null ? hById.get(t.idHerramienta) : undefined;
      return {
        id: t.id,
        identificador: v
          ? tituloVehiculo(v)
          : h
            ? h.nombre
            : t.idVehiculo != null
              ? `Vehículo #${t.idVehiculo}`
              : `Herramienta #${t.idHerramienta}`,
        // Row.tipoVehiculo solo maneja el icono de vehículo: una herramienta no
        // tiene uno propio, así que cae en un fallback (no se muestra combustible
        // para ella, tipoCombustible queda null).
        tipoVehiculo: v ? v.tipoVehiculo : 'CAMION',
        tipoCombustible: v ? v.tipoCombustible : null,
        fecha: formatFecha(t.fechaCarga),
        fechaCarga: t.fechaCarga,
        proveedor: pById.get(t.idProveedor)?.nombre ?? `Proveedor #${t.idProveedor}`,
        litros: t.litros,
        // El precio SALE DEL TICKET, no del catálogo de precios: GET /precios
        // devuelve solo los vigentes, así que resolver t.idPrecio contra él
        // daba 0 en toda carga anterior al último cambio de precio. Además el
        // ticket es el único que conoce la corrección que hizo el empleado
        // cuando el surtidor cobró otra cosa.
        precioUnitario: t.precioUnitario,
        costo: t.litros * t.precioUnitario,
        ticketFotoUrl: t.ticketFotoUrl,
      };
    });
  }, [data, tickets]);

  const filtered = useMemo(() => {
    if (activeFilter === 'Todos') return rows;
    const now = new Date();
    return rows.filter((r) => {
      const d = new Date(r.fechaCarga);
      if (activeFilter === 'Esta semana') {
        const sieteDias = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
        return d >= sieteDias;
      }
      return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth();
    });
  }, [rows, activeFilter]);

  const renderItem = useCallback(
    ({ item }: { item: Row }) => <LoadRow item={item} onPress={setSelected} />,
    [],
  );

  const header = (
    <View>
      <Text style={styles.h1}>HISTORIAL</Text>
      <Text style={styles.subtitle}>Tus cargas registradas</Text>
      <FlatList
        data={FILTERS}
        keyExtractor={(f) => f}
        horizontal
        showsHorizontalScrollIndicator={false}
        style={{ marginBottom: 18 }}
        renderItem={({ item }) => {
          const isActive = item === activeFilter;
          return (
            <Pressable
              style={[styles.chip, isActive && styles.chipActive]}
              onPress={() => setActiveFilter(item)}
            >
              <Text style={[styles.chipText, isActive && styles.chipTextActive]}>{item}</Text>
            </Pressable>
          );
        }}
        ItemSeparatorComponent={() => <View style={{ width: 8 }} />}
      />
    </View>
  );

  if (loading) {
    return (
      <SafeAreaView style={styles.safe} edges={['top']}>
        <View style={{ padding: 16 }}>
          <Text style={styles.h1}>HISTORIAL</Text>
        </View>
        <Loading />
      </SafeAreaView>
    );
  }

  if (error) {
    return (
      <SafeAreaView style={styles.safe} edges={['top']}>
        <View style={{ padding: 16 }}>
          <Text style={styles.h1}>HISTORIAL</Text>
        </View>
        <ErrorState message={error} onRetry={refetch} />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <FlatList
        data={filtered}
        keyExtractor={(r) => String(r.id)}
        renderItem={renderItem}
        ListHeaderComponent={header}
        ListEmptyComponent={<EmptyState message="Todavía no registraste cargas." />}
        contentContainerStyle={{ padding: 16, paddingBottom: 24 }}
        showsVerticalScrollIndicator={false}
        // Trae la página siguiente al acercarse al final. Con el filtro activo
        // se sigue paginando sobre el historial completo: las filas que el
        // filtro descarta igual cuentan para llegar al final de la lista.
        onEndReached={cargarMas}
        onEndReachedThreshold={0.5}
        ListFooterComponent={
          cargandoMas ? (
            <View style={{ paddingVertical: 16 }}>
              <ActivityIndicator color={colors.textFaint} />
            </View>
          ) : null
        }
      />
      <LoadDetailModal row={selected} onClose={() => setSelected(null)} />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  h1: { fontFamily: fonts.displayBold, fontSize: 26, color: colors.text, marginBottom: 4 },
  subtitle: { fontSize: 12, color: colors.textFaint, marginBottom: 16, fontFamily: fonts.sans },
  chip: {
    backgroundColor: '#1F2226',
    borderWidth: 1,
    borderColor: colors.borderSoft,
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 20,
  },
  chipActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { color: colors.textMuted, fontSize: 12, fontFamily: fonts.sans },
  chipTextActive: { color: colors.bgDeep, fontFamily: fonts.sansSemi },
  card: {
    backgroundColor: '#1F2226',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 13,
    paddingVertical: 15,
    paddingHorizontal: 16,
    marginBottom: 10,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  litrosBox: { flexDirection: 'row', alignItems: 'baseline', gap: 4 },
  litros: { fontFamily: fonts.mono, fontSize: 20, color: colors.text },
  litrosUnit: { fontFamily: fonts.sans, fontSize: 13, color: colors.textFaint },
  costWrap: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  cost: { fontFamily: fonts.mono, fontSize: 18, color: colors.primary },
  chevron: { fontSize: 22, color: colors.textDim, marginTop: -2 },
});
