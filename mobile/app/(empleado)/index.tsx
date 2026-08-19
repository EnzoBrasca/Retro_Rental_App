import { memo, useCallback, useMemo, useRef, useState } from 'react';
import {
  Pressable,
  RefreshControl,
  ScrollView,
  SectionList,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, useRouter } from 'expo-router';
import { colors, fonts } from '../../constants/theme';
import { Badge } from '../../components/ui';
import { Loading, ErrorState, EmptyState } from '../../components/fuel/ScreenState';
import { useTutorial } from '../../context/TutorialContext';
import { useAuth } from '../../context/AuthContext';
import { useFetch } from '../../hooks/useFetch';
import {
  getVehiculos,
  tituloVehiculo,
  textoBusquedaVehiculo,
  Vehiculo,
  Estado,
  TipoVehiculo,
  TipoCombustible,
} from '../../services/vehiculos';
import { getHerramientas, Herramienta } from '../../services/herramientas';
import {
  combustibleLabel,
  estadoLabel,
  iconForTipoVehiculo,
  tipoVehiculoLabel,
  TIPO_HERRAMIENTA,
} from '../../constants/labels';

import IconForeman from '../../assets/icons/008-capataz.svg';

const estadoStyle: Record<Estado, { bg: string; color: string }> = {
  DISPONIBLE: { bg: colors.greenBg, color: colors.greenText },
  EN_USO: { bg: colors.amberBg, color: colors.amberText },
  EN_MANTENIMIENTO: { bg: colors.dangerBg, color: colors.danger },
};

// El filtro de tipo suma HERRAMIENTA (opción de UI, no del enum del backend):
// al elegirlo se ve solo la lista de herramientas. Con "Todos" o un tipo de
// vehículo se ven los vehículos; las herramientas no tienen tipoVehiculo ni
// combustible fijo, así que solo aparecen bajo "Todos" o bajo su propio filtro.
type TipoFiltro = TipoVehiculo | typeof TIPO_HERRAMIENTA;

// Fila de la lista unificada de la flota. Vehículos y herramientas son tablas
// distintas en el backend, así que se representan como una unión discriminada en
// vez de forzarlas a un mismo shape. Mismo criterio que el ABM del admin.
type FlotaFila = { kind: 'vehiculo'; v: Vehiculo } | { kind: 'herramienta'; h: Herramienta };

const TIPO_FILTERS: { key: TipoFiltro | null; label: string }[] = [
  { key: null, label: 'Todos' },
  { key: 'MAQUINA', label: tipoVehiculoLabel.MAQUINA },
  { key: 'CAMIONETA', label: tipoVehiculoLabel.CAMIONETA },
  { key: 'CAMION', label: tipoVehiculoLabel.CAMION },
  { key: TIPO_HERRAMIENTA, label: 'Herramientas' },
];
const COMBUSTIBLE_FILTERS: { key: TipoCombustible | null; label: string }[] = [
  { key: null, label: 'Todos' },
  { key: 'NAFTA_SUPER', label: combustibleLabel.NAFTA_SUPER },
  { key: 'NAFTA_PREMIUM', label: combustibleLabel.NAFTA_PREMIUM },
  { key: 'GASOIL_GRADO_2', label: combustibleLabel.GASOIL_GRADO_2 },
  { key: 'GASOIL_GRADO_3', label: combustibleLabel.GASOIL_GRADO_3 },
  { key: 'GNC', label: combustibleLabel.GNC },
];

function FilterRow<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { key: T | null; label: string }[];
  value: T | null;
  onChange: (v: T | null) => void;
}) {
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.filterRow}
    >
      {options.map((o) => {
        const active = value === o.key;
        return (
          <Pressable
            key={String(o.key)}
            onPress={() => onChange(o.key)}
            style={[styles.filterChip, active && styles.filterChipActive]}
          >
            <Text style={[styles.filterChipText, active && styles.filterChipTextActive]}>
              {o.label}
            </Text>
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

// Card de un vehículo del pool. Toda la card abre el escaneo con ese vehículo ya
// fijado, salvo que esté en mantenimiento (el backend rechaza la carga, así que
// no dejamos entrar). Muestra el operario que lo usó por última vez.
const VehiculoCard = memo(function VehiculoCard({
  item,
  onOpen,
}: {
  item: Vehiculo;
  onOpen: (id: number) => void;
}) {
  const Icon = iconForTipoVehiculo(item.tipoVehiculo);
  const est = estadoStyle[item.estado];
  const enMantenimiento = item.estado === 'EN_MANTENIMIENTO';
  const operarioText = item.operarioNombre
    ? `En uso por ${item.operarioNombre} ${item.operarioApellido ?? ''}`.trim()
    : 'Sin uso reciente';

  return (
    <Pressable
      style={[styles.card, enMantenimiento && styles.cardDisabled]}
      onPress={() => onOpen(item.id)}
      disabled={enMantenimiento}
      accessibilityRole="button"
      accessibilityLabel={`${tituloVehiculo(item)}, ${estadoLabel[item.estado]}. ${operarioText}`}
      accessibilityState={{ disabled: enMantenimiento }}
      accessibilityHint={
        enMantenimiento ? undefined : 'Abre el registro de carga para este vehículo'
      }
    >
      <View style={styles.cardTop}>
        <View style={styles.cardIcon}>
          <Icon width={26} height={26} color={colors.primary} />
        </View>
        <View style={{ flex: 1, minWidth: 0 }}>
          <Text style={styles.cardName} numberOfLines={1}>
            {tituloVehiculo(item)}
          </Text>
          <Text style={styles.cardSub}>
            {tipoVehiculoLabel[item.tipoVehiculo]} · {combustibleLabel[item.tipoCombustible]}
          </Text>
        </View>
        <Badge label={estadoLabel[item.estado].toUpperCase()} bg={est.bg} color={est.color} />
      </View>

      <View style={styles.cardFooter}>
        <Text style={styles.operario} numberOfLines={1}>
          {operarioText}
        </Text>
        {enMantenimiento ? (
          <Text style={styles.cardBlocked}>No disponible</Text>
        ) : (
          <Text style={styles.cardHint}>Registrar carga →</Text>
        )}
      </View>
    </Pressable>
  );
});

// Card de una herramienta. No tiene estado ni operario asignado (no hay
// contador ni asignación como en un vehículo), así que solo muestra nombre y
// capacidad, y siempre está disponible para cargarle combustible.
const HerramientaCard = memo(function HerramientaCard({
  item,
  onOpen,
}: {
  item: Herramienta;
  onOpen: (id: number) => void;
}) {
  return (
    <Pressable
      style={styles.card}
      onPress={() => onOpen(item.id)}
      accessibilityRole="button"
      accessibilityLabel={`${item.nombre}, herramienta, capacidad ${item.capacidad} litros`}
      accessibilityHint="Abre el registro de carga para esta herramienta"
    >
      <View style={styles.cardTop}>
        <View style={styles.cardIcon} accessibilityElementsHidden importantForAccessibility="no">
          <Text style={{ fontSize: 22 }}>🔧</Text>
        </View>
        <View style={{ flex: 1, minWidth: 0 }}>
          <Text style={styles.cardName} numberOfLines={1}>
            {item.nombre}
          </Text>
          <Text style={styles.cardSub}>Herramienta · Capacidad {item.capacidad} L</Text>
        </View>
      </View>

      <View style={styles.cardFooter}>
        <Text style={styles.operario} numberOfLines={1}>
          Combustible a elección en la carga
        </Text>
        <Text style={styles.cardHint}>Registrar carga →</Text>
      </View>
    </Pressable>
  );
});

function Stat({ value, label, color }: { value: string; label: string; color: string }) {
  return (
    <View style={styles.statCard}>
      <Text style={[styles.statValue, { color }]}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

export default function FlotaScreen() {
  const { open } = useTutorial();
  const { user } = useAuth();
  const router = useRouter();
  const userName = user ? user.nombre : 'Operario';

  const [search, setSearch] = useState('');
  const [tipoF, setTipoF] = useState<TipoFiltro | null>(null);
  const [combF, setCombF] = useState<TipoCombustible | null>(null);

  const { data, loading, error, refetch } = useFetch(async () => {
    const [vehiculos, herramientas] = await Promise.all([getVehiculos(), getHerramientas()]);
    return { vehiculos, herramientas };
  });

  // Al volver a la pestaña refrescamos: el operario "actual" cambia cuando otro
  // empleado carga, y un vehículo puede darse de baja o entrar a taller. Salteamos
  // el primer focus (useFetch ya cargó al montar) para no duplicar el fetch.
  const firstFocus = useRef(true);
  useFocusEffect(
    useCallback(() => {
      if (firstFocus.current) {
        firstFocus.current = false;
        return;
      }
      refetch();
    }, [refetch]),
  );

  // useCallback no es decoración acá: VehiculoCard y HerramientaCard están
  // envueltas en `memo`, que compara props por identidad. Con la función
  // recreada en cada render la comparación fallaba SIEMPRE y el memo no ahorraba
  // nada — solo agregaba una comparación que nunca daba positivo. Como el
  // buscador es estado de esta pantalla, eso se pagaba en cada tecla: escribir
  // "hilux" eran cinco re-renders de la lista entera. `router` es estable.
  const openScan = useCallback(
    (id: number) => {
      router.push({ pathname: '/(empleado)/escanear', params: { idVehiculo: String(id) } });
    },
    [router],
  );
  const openScanHerramienta = useCallback(
    (id: number) => {
      router.push({ pathname: '/(empleado)/escanear', params: { idHerramienta: String(id) } });
    },
    [router],
  );

  // Solo activos (los dados de baja no operan). Los filtros se aplican en
  // cliente: ya tenemos todo el catálogo en memoria, es barato.
  const activos = useMemo(
    () => (data?.vehiculos ?? []).filter((v) => v.fechaBaja === null),
    [data],
  );
  const activasHerramientas = useMemo(
    () => (data?.herramientas ?? []).filter((h) => (h.fechaBaja ?? null) === null),
    [data],
  );

  // Un filtro de tipo de vehículo o de combustible no aplica a una herramienta
  // (no tiene tipoVehiculo ni combustible fijo), así que con cualquiera de los
  // dos activo se la deja fuera de la lista de vehículos filtrados.
  const filtrados = useMemo(() => {
    if (tipoF === TIPO_HERRAMIENTA) return [];
    const needle = search.trim().toLowerCase();
    return activos.filter(
      (v) =>
        (needle === '' || textoBusquedaVehiculo(v).includes(needle)) &&
        (tipoF === null || v.tipoVehiculo === tipoF) &&
        (combF === null || v.tipoCombustible === combF),
    );
  }, [activos, search, tipoF, combF]);

  // Las herramientas solo se muestran con "Todos" o con el filtro de tipo
  // "Herramientas": un filtro de tipo de vehículo o de combustible las excluye,
  // porque ninguno de los dos las describe.
  const filtradasHerramientas = useMemo(() => {
    if (tipoF !== null && tipoF !== TIPO_HERRAMIENTA) return [];
    if (combF !== null) return [];
    const needle = search.trim().toLowerCase();
    return activasHerramientas.filter(
      (h) => needle === '' || h.nombre.toLowerCase().includes(needle),
    );
  }, [activasHerramientas, search, tipoF, combF]);

  // Memoizados como sus vecinos `activos` y `filtrados`: son baratos, pero
  // recalcularlos en cada tecla del buscador mientras el resto no lo hace es
  // inconsistencia sin motivo.
  const operativos = useMemo(
    () => activos.filter((v) => v.estado !== 'EN_MANTENIMIENTO').length,
    [activos],
  );
  const enTaller = useMemo(
    () => activos.filter((v) => v.estado === 'EN_MANTENIMIENTO').length,
    [activos],
  );

  // La lista se arma como secciones para poder virtualizarla con SectionList.
  // Antes era un ScrollView con dos .map(): un ScrollView MONTA TODOS sus hijos,
  // estén o no en pantalla. Cada card son un SVG, un badge, cuatro Text y tres
  // View, así que con 200 vehículos son ~2.000 vistas nativas montadas de golpe.
  // Las secciones vacías se incluyen igual: su pie muestra el mensaje de "no hay
  // nada que coincida", que es información, no ausencia de información.
  const secciones = useMemo(() => {
    const s: { title: string; vacio: string; data: FlotaFila[] }[] = [];
    if (tipoF !== TIPO_HERRAMIENTA) {
      s.push({
        title: 'VEHÍCULOS',
        vacio: 'No hay vehículos que coincidan con los filtros.',
        data: filtrados.map((v) => ({ kind: 'vehiculo', v }) as const),
      });
    }
    // Con un tipo de vehículo o un combustible puntual elegidos se oculta esta
    // sección entera: ninguna herramienta los cumple.
    if ((tipoF === null || tipoF === TIPO_HERRAMIENTA) && combF === null) {
      s.push({
        title: 'HERRAMIENTAS',
        vacio: 'No hay herramientas que coincidan con los filtros.',
        data: filtradasHerramientas.map((h) => ({ kind: 'herramienta', h }) as const),
      });
    }
    return s;
  }, [tipoF, combF, filtrados, filtradasHerramientas]);

  const renderFila = useCallback(
    ({ item }: { item: FlotaFila }) =>
      item.kind === 'vehiculo' ? (
        <VehiculoCard item={item.v} onOpen={openScan} />
      ) : (
        <HerramientaCard item={item.h} onOpen={openScanHerramienta} />
      ),
    [openScan, openScanHerramienta],
  );

  const header = (
    <View style={styles.topRow}>
      <View>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
          <Text style={styles.greeting}>Hola, {userName}</Text>
          <IconForeman width={12} height={12} color={colors.textFaint} />
        </View>
        <Text style={styles.h1}>FLOTA</Text>
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        {user?.rol === 'ADMINISTRADOR' && (
          <Pressable style={styles.backAdminBtn} onPress={() => router.replace('/(administrador)')}>
            <Text style={styles.backAdminText}>‹ Admin</Text>
          </Pressable>
        )}
        <Pressable
          style={styles.helpBtn}
          onPress={open}
          accessibilityRole="button"
          accessibilityLabel="Ver el tutorial"
        >
          <Text style={styles.helpText}>?</Text>
        </Pressable>
      </View>
    </View>
  );

  if (loading) {
    return (
      <SafeAreaView style={styles.safe} edges={['top']}>
        <View style={{ padding: 16 }}>{header}</View>
        <Loading />
      </SafeAreaView>
    );
  }

  if (error || !data) {
    return (
      <SafeAreaView style={styles.safe} edges={['top']}>
        <View style={{ padding: 16 }}>{header}</View>
        <ErrorState message={error ?? 'Error'} onRetry={refetch} />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <SectionList
        sections={secciones}
        keyExtractor={(item) => (item.kind === 'vehiculo' ? `v-${item.v.id}` : `h-${item.h.id}`)}
        renderItem={renderFila}
        renderSectionHeader={({ section }) => (
          <View style={styles.listHead}>
            <Text style={styles.sectionTitle} accessibilityRole="header">
              {section.title}
            </Text>
            <Text style={styles.count}>{section.data.length}</Text>
          </View>
        )}
        renderSectionFooter={({ section }) =>
          section.data.length === 0 ? <EmptyState message={section.vacio} /> : null
        }
        // El encabezado va como ELEMENTO, no como componente: pasar una función
        // acá remonta el subárbol en cada render y el buscador pierde el foco a
        // la primera tecla.
        ListHeaderComponent={
          <View>
            {header}

            <View style={styles.statsRow}>
              <Stat value={String(activos.length)} label="Total" color={colors.primary} />
              <Stat value={String(operativos)} label="Operativos" color={colors.green} />
              <Stat value={String(enTaller)} label="En taller" color={colors.orange} />
            </View>

            <TextInput
              style={styles.searchInput}
              value={search}
              onChangeText={setSearch}
              placeholder="Buscar por patente, modelo o interno…"
              placeholderTextColor={colors.textDim}
              autoCapitalize="characters"
              autoCorrect={false}
              accessibilityLabel="Buscar por patente, modelo o interno"
            />

            <FilterRow options={TIPO_FILTERS} value={tipoF} onChange={setTipoF} />
            <FilterRow options={COMBUSTIBLE_FILTERS} value={combF} onChange={setCombF} />
          </View>
        }
        // Tirar para actualizar es el reflejo de todo el mundo cuando algo no
        // cargó. Sin esto, la única forma de refrescar era salir de la pestaña y
        // volver, que no es evidente — y en una app de campo con conexión
        // intermitente se necesita seguido.
        refreshControl={
          <RefreshControl
            refreshing={loading}
            onRefresh={refetch}
            tintColor={colors.primary}
            colors={[colors.primary]}
          />
        }
        contentContainerStyle={{ padding: 16, paddingBottom: 24 }}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
        stickySectionHeadersEnabled={false}
        removeClippedSubviews
        maxToRenderPerBatch={10}
        windowSize={5}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  topRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 16,
  },
  greeting: { fontSize: 12, color: colors.textFaint, fontFamily: fonts.sans },
  h1: { fontFamily: fonts.displayBold, fontSize: 26, color: colors.text, marginTop: 2 },
  helpBtn: {
    width: 38,
    height: 38,
    borderRadius: 10,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.borderSoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  helpText: { color: colors.primary, fontSize: 16, fontFamily: fonts.sansSemi },
  backAdminBtn: {
    height: 38,
    paddingHorizontal: 12,
    borderRadius: 10,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.borderSoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  backAdminText: { color: colors.primary, fontSize: 12, fontFamily: fonts.sansSemi },
  statsRow: { flexDirection: 'row', gap: 9, marginBottom: 16 },
  statCard: {
    flex: 1,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    paddingVertical: 12,
    paddingHorizontal: 10,
  },
  statValue: { fontFamily: fonts.mono, fontSize: 20 },
  statLabel: { fontSize: 10, color: colors.textFaint, marginTop: 2, fontFamily: fonts.sans },
  searchInput: {
    height: 44,
    backgroundColor: colors.surfaceInput,
    borderWidth: 1,
    borderColor: colors.borderInput,
    borderRadius: 10,
    paddingHorizontal: 14,
    color: colors.text,
    fontSize: 14,
    fontFamily: fonts.sans,
    marginBottom: 10,
  },
  filterRow: { gap: 8, paddingVertical: 3, paddingRight: 4 },
  filterChip: {
    paddingHorizontal: 13,
    paddingVertical: 7,
    borderRadius: 8,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
  },
  filterChipActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  filterChipText: { fontSize: 12, color: colors.textFaint, fontFamily: fonts.sans },
  filterChipTextActive: { color: colors.bgDeep, fontFamily: fonts.sansSemi },
  listHead: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 16,
    marginBottom: 12,
  },
  sectionTitle: {
    fontFamily: fonts.display,
    fontSize: 15,
    letterSpacing: 1,
    color: colors.textStrong,
  },
  count: { fontFamily: fonts.mono, fontSize: 13, color: colors.textFaint },
  card: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 13,
    padding: 13,
    marginBottom: 10,
  },
  cardDisabled: { opacity: 0.55 },
  cardTop: { flexDirection: 'row', alignItems: 'center', gap: 13 },
  cardIcon: {
    width: 50,
    height: 50,
    borderRadius: 11,
    backgroundColor: colors.primary + '1A',
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardName: { fontFamily: fonts.displayBold, fontSize: 16, color: colors.text },
  cardSub: { fontSize: 11.5, color: colors.textFaint, marginTop: 2, fontFamily: fonts.sans },
  cardFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 12,
    paddingTop: 11,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    gap: 10,
  },
  operario: { flex: 1, minWidth: 0, fontSize: 12, color: colors.textDim, fontFamily: fonts.sans },
  cardHint: { fontSize: 12, color: colors.primary, fontFamily: fonts.sansSemi },
  cardBlocked: { fontSize: 12, color: colors.danger, fontFamily: fonts.sansSemi },
});
