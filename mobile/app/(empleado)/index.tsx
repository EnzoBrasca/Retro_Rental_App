import { memo, useCallback, useMemo, useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, useRouter } from 'expo-router';
import { colors, fonts, radius } from '../../constants/theme';
import { Badge } from '../../components/ui';
import { Loading, ErrorState, EmptyState } from '../../components/fuel/ScreenState';
import { useTutorial } from '../../context/TutorialContext';
import { useAuth } from '../../context/AuthContext';
import { useFetch } from '../../hooks/useFetch';
import {
  getVehiculos,
  Vehiculo,
  Estado,
  TipoVehiculo,
  TipoCombustible,
} from '../../services/vehiculos';
import { combustibleLabel, estadoLabel, iconForTipoVehiculo, tipoVehiculoLabel } from '../../constants/labels';

import IconForeman from '../../assets/icons/008-capataz.svg';

const estadoStyle: Record<Estado, { bg: string; color: string }> = {
  DISPONIBLE: { bg: colors.greenBg, color: colors.greenText },
  EN_USO: { bg: colors.amberBg, color: colors.amberText },
  EN_MANTENIMIENTO: { bg: colors.dangerBg, color: colors.danger },
};

const TIPO_FILTERS: { key: TipoVehiculo | null; label: string }[] = [
  { key: null, label: 'Todos' },
  { key: 'MAQUINA', label: tipoVehiculoLabel.MAQUINA },
  { key: 'CAMIONETA', label: tipoVehiculoLabel.CAMIONETA },
  { key: 'CAMION', label: tipoVehiculoLabel.CAMION },
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
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filterRow}>
      {options.map((o) => {
        const active = value === o.key;
        return (
          <Pressable
            key={String(o.key)}
            onPress={() => onChange(o.key)}
            style={[styles.filterChip, active && styles.filterChipActive]}
          >
            <Text style={[styles.filterChipText, active && styles.filterChipTextActive]}>{o.label}</Text>
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

// Card de un vehículo del pool. Toda la card abre el escaneo con ese vehículo ya
// fijado, salvo que esté en mantenimiento (el backend rechaza la carga, así que
// no dejamos entrar). Muestra el operario que lo usó por última vez.
const VehiculoCard = memo(({ item, onOpen }: { item: Vehiculo; onOpen: (id: number) => void }) => {
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
    >
      <View style={styles.cardTop}>
        <View style={styles.cardIcon}>
          <Icon width={26} height={26} color={colors.primary} />
        </View>
        <View style={{ flex: 1, minWidth: 0 }}>
          <Text style={styles.cardName} numberOfLines={1}>
            {item.patente}
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
  const [estadoF, setEstadoF] = useState<Estado | null>(null);
  const [tipoF, setTipoF] = useState<TipoVehiculo | null>(null);
  const [combF, setCombF] = useState<TipoCombustible | null>(null);

  const { data, loading, error, refetch } = useFetch(() => getVehiculos());

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

  const openScan = (id: number) => {
    router.push({ pathname: '/(empleado)/escanear', params: { idVehiculo: String(id) } });
  };

  // Solo vehículos activos (los dados de baja no operan). Los filtros se aplican
  // en cliente: ya tenemos todo el catálogo en memoria, es barato.
  const activos = useMemo(() => (data ?? []).filter((v) => v.fechaBaja === null), [data]);
  const filtrados = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return activos.filter(
      (v) =>
        (needle === '' || v.patente.toLowerCase().includes(needle)) &&
        (estadoF === null || v.estado === estadoF) &&
        (tipoF === null || v.tipoVehiculo === tipoF) &&
        (combF === null || v.tipoCombustible === combF),
    );
  }, [activos, search, estadoF, tipoF, combF]);

  const operativos = activos.filter((v) => v.estado !== 'EN_MANTENIMIENTO').length;
  const enTaller = activos.filter((v) => v.estado === 'EN_MANTENIMIENTO').length;

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
        <Pressable style={styles.helpBtn} onPress={open}>
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
      <ScrollView
        contentContainerStyle={{ padding: 16, paddingBottom: 24 }}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
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
          placeholder="Buscar por patente…"
          placeholderTextColor={colors.textDim}
          autoCapitalize="characters"
          autoCorrect={false}
        />
        
        <FilterRow options={TIPO_FILTERS} value={tipoF} onChange={setTipoF} />
        <FilterRow options={COMBUSTIBLE_FILTERS} value={combF} onChange={setCombF} />

        <View style={styles.listHead}>
          <Text style={styles.sectionTitle}>VEHÍCULOS</Text>
          <Text style={styles.count}>{filtrados.length}</Text>
        </View>

        {filtrados.length === 0 ? (
          <EmptyState message="No hay vehículos que coincidan con los filtros." />
        ) : (
          filtrados.map((v) => <VehiculoCard key={v.id} item={v} onOpen={openScan} />)
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  topRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 },
  greeting: { fontSize: 12, color: colors.textFaint, fontFamily: fonts.sans },
  h1: { fontFamily: fonts.displayBold, fontSize: 26, color: colors.text, marginTop: 2 },
  helpBtn: {
    width: 38,
    height: 38,
    borderRadius: 10,
    backgroundColor: '#1F2226',
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
    backgroundColor: '#1F2226',
    borderWidth: 1,
    borderColor: colors.borderSoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  backAdminText: { color: colors.primary, fontSize: 12, fontFamily: fonts.sansSemi },
  statsRow: { flexDirection: 'row', gap: 9, marginBottom: 16 },
  statCard: {
    flex: 1,
    backgroundColor: '#1F2226',
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
    backgroundColor: '#1F2226',
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
  sectionTitle: { fontFamily: fonts.display, fontSize: 15, letterSpacing: 1, color: '#C9CDD2' },
  count: { fontFamily: fonts.mono, fontSize: 13, color: colors.textFaint },
  card: {
    backgroundColor: '#1F2226',
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
