import { memo, useCallback, useMemo, useRef, useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect } from 'expo-router';
import { colors, fonts } from '../../constants/theme';
import { Loading, ErrorState, EmptyState } from '../../components/fuel/ScreenState';
import { LoadDetailModal } from '../../components/fuel/LoadDetailModal';
import { useFetch } from '../../hooks/useFetch';
import { getMisTickets, Ticket } from '../../services/tickets';
import { getVehiculos, tituloVehiculo, Vehiculo, TipoCombustible } from '../../services/vehiculos';
import { getHerramientas, Herramienta } from '../../services/herramientas';
import { getProveedores, getPrecios, Proveedor, Precio } from '../../services/catalogos';
import { formatFecha, formatMoney } from '../../constants/labels';

type Filtro = 'Todos' | 'Esta semana' | 'Este mes';
const FILTERS: Filtro[] = ['Todos', 'Esta semana', 'Este mes'];

// El tipo de la fila vive en LoadDetailModal, que es quien la consume. Se
// reexporta para no romper a quien la importe desde acá.
import type { Row } from '../../components/fuel/LoadDetailModal';
export type { Row };

// Card colapsada: solo litros e importe total. El detalle (proveedor, fecha,
// combustible, precio/L y la foto del ticket) se abre al tocarla.
const LoadRow = memo(({ item, onPress }: { item: Row; onPress: (r: Row) => void }) => (
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
));

export default function HistorialScreen() {
  const [activeFilter, setActiveFilter] = useState<Filtro>('Todos');
  const [selected, setSelected] = useState<Row | null>(null);

  const { data, loading, error, refetch } = useFetch(async () => {
    const [tickets, vehiculos, herramientas, proveedores, precios] = await Promise.all([
      getMisTickets(),
      getVehiculos(),
      getHerramientas(),
      getProveedores(),
      getPrecios(),
    ]);
    return { tickets, vehiculos, herramientas, proveedores, precios };
  });

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
    const precioById = new Map<number, Precio>(data.precios.map((p) => [p.id, p]));

    return data.tickets.map((t: Ticket) => {
      // Exactamente uno de los dos viene con valor (ver services/tickets.ts).
      const v = t.idVehiculo != null ? vById.get(t.idVehiculo) : undefined;
      const h = t.idHerramienta != null ? hById.get(t.idHerramienta) : undefined;
      const precio = precioById.get(t.idPrecio);
      const unitario = precio ? precio.precioUnitario : 0;
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
        precioUnitario: unitario,
        costo: t.litros * unitario,
        ticketFotoUrl: t.ticketFotoUrl,
      };
    });
  }, [data]);

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
