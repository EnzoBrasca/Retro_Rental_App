import { useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View, TextInput } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { colors, fonts } from '../../constants/theme';
import { BarChart } from '../../components/fuel/BarChart';
import { Loading, ErrorState, EmptyState } from '../../components/fuel/ScreenState';
import { OptionChips } from '../../components/fuel/OptionChips';
import { useAuth } from '../../context/AuthContext';
import { useFetch } from '../../hooks/useFetch';
import { getStats, StatsRange } from '../../services/stats';
import {
  getAdminVehiculos,
  createVehiculo,
  updateVehiculo,
  desactivarVehiculo,
  Vehiculo,
  Estado,
  TipoVehiculo,
  TipoCombustible,
} from '../../services/vehiculos';
import {
  formatDay,
  formatMoney,
  tipoVehiculoLabel,
  combustibleLabel,
  estadoLabel,
  iconForTipoVehiculo,
} from '../../constants/labels';

const RANGES: { key: StatsRange; label: string }[] = [
  { key: 'daily', label: 'Diario' },
  { key: 'weekly', label: 'Semanal' },
  { key: 'monthly', label: 'Mensual' },
];

const CHART_COLORS = ['#F5C518', '#ffd94d', '#c99a00', '#8a7220', '#5f5220'];

const estadoStyle: Record<Estado, { bg: string; color: string }> = {
  DISPONIBLE: { bg: colors.greenBg, color: colors.greenText },
  EN_USO: { bg: colors.amberBg, color: colors.amberText },
  EN_MANTENIMIENTO: { bg: colors.dangerBg, color: colors.danger },
};

const TIPO_VEHICULO_OPTS = (Object.keys(tipoVehiculoLabel) as TipoVehiculo[]).map((k) => ({ key: k, label: tipoVehiculoLabel[k] }));
const COMBUSTIBLE_OPTS = (Object.keys(combustibleLabel) as TipoCombustible[]).map((k) => ({ key: k, label: combustibleLabel[k] }));
const ESTADO_OPTS = (Object.keys(estadoLabel) as Estado[]).map((k) => ({ key: k, label: estadoLabel[k] }));

export default function AdministradorScreen() {
  const { user } = useAuth();
  const router = useRouter();
  const [viewMode, setViewMode] = useState<'analytics' | 'vehicles'>('analytics');

  const initials = user
    ? `${user.nombre[0] ?? ''}${user.apellido[0] ?? ''}`.toUpperCase()
    : 'AD';

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 40 }} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <View>
            <Text style={styles.eyebrow}>PANEL DE CONTROL</Text>
            <Text style={styles.h1}>ADMINISTRADOR</Text>
          </View>
          <Pressable onPress={() => router.push('/(administrador)/perfil')} style={styles.avatar}>
            <Text style={styles.avatarText}>{initials}</Text>
          </Pressable>
        </View>

        <View style={styles.topTabs}>
          <Pressable style={[styles.topTab, viewMode === 'analytics' && styles.topTabActive]} onPress={() => setViewMode('analytics')}>
            <Text style={[styles.topTabText, viewMode === 'analytics' && styles.topTabTextActive]}>Analítica</Text>
          </Pressable>
          <Pressable style={[styles.topTab, viewMode === 'vehicles' && styles.topTabActive]} onPress={() => setViewMode('vehicles')}>
            <Text style={[styles.topTabText, viewMode === 'vehicles' && styles.topTabTextActive]}>Gestión de Flota</Text>
          </Pressable>
        </View>

        {viewMode === 'analytics' ? <Analytics /> : <VehiclesABM />}
      </ScrollView>
    </SafeAreaView>
  );
}

function Analytics() {
  const [range, setRange] = useState<StatsRange>('weekly');
  const { data: stats, loading, error, refetch } = useFetch(() => getStats(range), [range]);

  return (
    <>
      <Text style={styles.caption}>Consumo de combustible de toda la flota</Text>

      <View style={styles.panel}>
        <Text style={styles.panelLabel}>FILTROS</Text>
        <Text style={styles.fieldHint}>Período</Text>
        <View style={styles.segment}>
          {RANGES.map((r) => (
            <Pressable key={r.key} style={[styles.seg, range === r.key && styles.segActive]} onPress={() => setRange(r.key)}>
              <Text style={[styles.segText, range === r.key && styles.segTextActive]}>{r.label}</Text>
            </Pressable>
          ))}
        </View>
        {stats && (
          <Text style={styles.rangeLabel}>
            {formatDay(stats.desde)} – {formatDay(stats.hasta)}
          </Text>
        )}
      </View>

      {loading ? (
        <View style={{ height: 260 }}>
          <Loading />
        </View>
      ) : error || !stats ? (
        <View style={{ height: 200 }}>
          <ErrorState message={error ?? 'Error'} onRetry={refetch} />
        </View>
      ) : (
        <>
          <View style={styles.kpiGrid}>
            <Kpi label="Costo total" value={formatMoney(stats.gastoTotal)} valueColor={colors.primary} delta={`${stats.cantidadRegistros} cargas`} deltaColor={colors.textFaint} accent />
            <Kpi label="Litros cargados" value={`${stats.totalLitros} L`} delta={`${stats.vehiculosActivos} vehículos`} deltaColor={colors.textFaint} />
            <Kpi
              label="Promedio / carga"
              value={stats.cantidadRegistros > 0 ? formatMoney(stats.gastoTotal / stats.cantidadRegistros) : '$0'}
              delta="por carga"
              deltaColor={colors.orange}
            />
            <Kpi label="Litros / vehículo" value={`${Math.round(stats.promedioLitrosPorVehiculo)} L`} delta="promedio" deltaColor={colors.textFaint} />
          </View>

          <View style={styles.chartCard}>
            <View style={styles.chartHead}>
              <View style={{ flex: 1 }}>
                <Text style={styles.chartTitle}>COSTO POR VEHÍCULO</Text>
                <Text style={styles.chartSub}>Distribución del gasto por cada unidad</Text>
              </View>
            </View>
            {stats.desglosePorVehiculo.length === 0 ? (
              <EmptyState message="No hay cargas registradas en este período." />
            ) : (
              <BarChart
                data={stats.desglosePorVehiculo.map((d, i) => ({
                  l: d.patente,
                  v: d.gasto,
                  c: CHART_COLORS[i % CHART_COLORS.length],
                  amount: formatMoney(d.gasto),
                }))}
              />
            )}
          </View>
        </>
      )}
    </>
  );
}

const todayISO = () => new Date().toISOString().slice(0, 10);

type FormState = {
  patente: string;
  tipoVehiculo: TipoVehiculo | null;
  tipoCombustible: TipoCombustible | null;
  capacidadTanque: string;
  estado: Estado;
  fechaUltimoMantenimiento: string;
  kilometraje: string;
  consumoPromedio: string;
};

const emptyForm = (): FormState => ({
  patente: '',
  tipoVehiculo: null,
  tipoCombustible: null,
  capacidadTanque: '',
  estado: 'DISPONIBLE',
  fechaUltimoMantenimiento: todayISO(),
  kilometraje: '',
  consumoPromedio: '',
});

const formFrom = (v: Vehiculo): FormState => ({
  patente: v.patente,
  tipoVehiculo: v.tipoVehiculo,
  tipoCombustible: v.tipoCombustible,
  capacidadTanque: String(v.capacidadTanque),
  estado: v.estado,
  fechaUltimoMantenimiento: todayISO(),
  kilometraje: String(v.kilometraje),
  consumoPromedio: String(v.consumoPromedio),
});

function VehiclesABM() {
  const { data, loading, error, refetch } = useFetch(getAdminVehiculos);

  // editing: null (lista) | 'new' | id del vehiculo en edición.
  const [editing, setEditing] = useState<'new' | number | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm());
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const openNew = () => {
    setForm(emptyForm());
    setFormError(null);
    setEditing('new');
  };
  const openEdit = (v: Vehiculo) => {
    setForm(formFrom(v));
    setFormError(null);
    setEditing(v.id);
  };

  const save = async () => {
    setFormError(null);
    if (!form.patente.trim()) return setFormError('Ingresá la patente.');
    if (!form.tipoVehiculo) return setFormError('Elegí el tipo de vehículo.');
    if (!form.tipoCombustible) return setFormError('Elegí el combustible.');
    const capacidad = parseInt(form.capacidadTanque, 10);
    const km = parseInt(form.kilometraje, 10);
    const consumo = parseFloat(form.consumoPromedio.replace(',', '.'));
    if (!(capacidad > 0)) return setFormError('Capacidad de tanque inválida.');
    if (!(km >= 0)) return setFormError('Kilometraje inválido.');
    if (!(consumo > 0)) return setFormError('Consumo promedio inválido.');

    const payload = {
      patente: form.patente.trim(),
      tipoVehiculo: form.tipoVehiculo,
      tipoCombustible: form.tipoCombustible,
      capacidadTanque: capacidad,
      estado: form.estado,
      fechaUltimoMantenimiento: form.fechaUltimoMantenimiento,
      kilometraje: km,
      consumoPromedio: consumo,
    };

    try {
      setSaving(true);
      if (editing === 'new') await createVehiculo(payload);
      else if (typeof editing === 'number') await updateVehiculo(editing, payload);
      setEditing(null);
      await refetch();
    } catch (e) {
      setFormError(e instanceof Error ? e.message : 'No se pudo guardar.');
    } finally {
      setSaving(false);
    }
  };

  const remove = (v: Vehiculo) => {
    Alert.alert('Dar de baja', `¿Dar de baja el vehículo ${v.patente}?`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Dar de baja',
        style: 'destructive',
        onPress: async () => {
          try {
            await desactivarVehiculo(v.id);
            await refetch();
          } catch (e) {
            Alert.alert('Error', e instanceof Error ? e.message : 'No se pudo dar de baja.');
          }
        },
      },
    ]);
  };

  if (editing !== null) {
    return (
      <View style={{ marginTop: 4 }}>
        <Text style={styles.formTitle}>{editing === 'new' ? 'Nuevo vehículo' : 'Editar vehículo'}</Text>

        <Text style={styles.fieldHint}>Patente</Text>
        <TextInput style={styles.abmInput} value={form.patente} autoCapitalize="characters" onChangeText={(t) => setForm({ ...form, patente: t })} placeholder="AB123CD" placeholderTextColor={colors.textDim} />

        <Text style={styles.fieldHint}>Tipo de vehículo</Text>
        <OptionChips options={TIPO_VEHICULO_OPTS} value={form.tipoVehiculo} onChange={(k) => setForm({ ...form, tipoVehiculo: k })} />

        <Text style={[styles.fieldHint, { marginTop: 12 }]}>Combustible</Text>
        <OptionChips options={COMBUSTIBLE_OPTS} value={form.tipoCombustible} onChange={(k) => setForm({ ...form, tipoCombustible: k })} />

        <Text style={[styles.fieldHint, { marginTop: 12 }]}>Estado</Text>
        <OptionChips options={ESTADO_OPTS} value={form.estado} onChange={(k) => setForm({ ...form, estado: k })} />

        <Text style={[styles.fieldHint, { marginTop: 12 }]}>Capacidad de tanque (L)</Text>
        <TextInput style={styles.abmInput} value={form.capacidadTanque} keyboardType="number-pad" onChangeText={(t) => setForm({ ...form, capacidadTanque: t })} />

        <Text style={styles.fieldHint}>Kilometraje</Text>
        <TextInput style={styles.abmInput} value={form.kilometraje} keyboardType="number-pad" onChangeText={(t) => setForm({ ...form, kilometraje: t })} />

        <Text style={styles.fieldHint}>Consumo promedio (L/100km)</Text>
        <TextInput style={styles.abmInput} value={form.consumoPromedio} keyboardType="numeric" onChangeText={(t) => setForm({ ...form, consumoPromedio: t })} />

        <Text style={styles.fieldHint}>Último mantenimiento (AAAA-MM-DD)</Text>
        <TextInput style={styles.abmInput} value={form.fechaUltimoMantenimiento} onChangeText={(t) => setForm({ ...form, fechaUltimoMantenimiento: t })} />

        {formError && <Text style={styles.error}>{formError}</Text>}

        <View style={styles.abmActions}>
          <Pressable style={[styles.abmBtn, { backgroundColor: colors.primary }]} onPress={save} disabled={saving}>
            <Text style={[styles.abmBtnText, { color: colors.bgDeep }]}>{saving ? 'Guardando…' : 'Guardar'}</Text>
          </Pressable>
          <Pressable style={[styles.abmBtn, { backgroundColor: '#1b1d20' }]} onPress={() => setEditing(null)} disabled={saving}>
            <Text style={[styles.abmBtnText, { color: colors.text }]}>Cancelar</Text>
          </Pressable>
        </View>
      </View>
    );
  }

  return (
    <View style={{ flex: 1, marginTop: 4 }}>
      <Pressable style={styles.addBtn} onPress={openNew}>
        <Text style={styles.addBtnText}>+ NUEVO VEHÍCULO</Text>
      </Pressable>

      {loading ? (
        <View style={{ height: 160 }}>
          <Loading />
        </View>
      ) : error ? (
        <ErrorState message={error} onRetry={refetch} />
      ) : !data || data.length === 0 ? (
        <EmptyState message="No hay vehículos cargados. Agregá el primero con los datos reales de la empresa." />
      ) : (
        data.map((v) => {
          const Icon = iconForTipoVehiculo(v.tipoVehiculo);
          const est = estadoStyle[v.estado];
          const baja = v.fechaBaja !== null;
          return (
            <View key={v.id} style={[styles.abmCard, baja && { opacity: 0.5 }]}>
              <View style={styles.abmHeader}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, flex: 1, paddingRight: 10 }}>
                  <View style={styles.abmIcon}>
                    <Icon width={24} height={24} color={colors.primary} />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.abmName} numberOfLines={1}>
                      {v.patente}
                    </Text>
                    <Text style={styles.abmSub}>
                      {tipoVehiculoLabel[v.tipoVehiculo]} · {combustibleLabel[v.tipoCombustible]} · {v.consumoPromedio} L
                    </Text>
                    <Text style={[styles.abmEstado, { color: est.color }]}>
                      {baja ? 'DADO DE BAJA' : estadoLabel[v.estado]}
                    </Text>
                  </View>
                </View>
                {!baja && (
                  <View style={{ flexDirection: 'row', gap: 6 }}>
                    <Pressable style={styles.iconBtn} onPress={() => openEdit(v)}>
                      <Text>✏️</Text>
                    </Pressable>
                    <Pressable style={styles.iconBtn} onPress={() => remove(v)}>
                      <Text>🗑️</Text>
                    </Pressable>
                  </View>
                )}
              </View>
            </View>
          );
        })
      )}
    </View>
  );
}

function Kpi({
  label,
  value,
  valueColor,
  delta,
  deltaColor,
  accent,
}: {
  label: string;
  value: string;
  valueColor?: string;
  delta: string;
  deltaColor: string;
  accent?: boolean;
}) {
  return (
    <View style={styles.kpi}>
      {accent && <View style={styles.kpiAccent} />}
      <Text style={styles.kpiLabel}>{label}</Text>
      <Text style={[styles.kpiValue, valueColor && { color: valueColor }]}>{value}</Text>
      <Text style={[styles.kpiDelta, { color: deltaColor }]}>{delta}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 },
  eyebrow: { fontSize: 11, color: colors.textFaint, letterSpacing: 1.5, fontFamily: fonts.sans },
  h1: { fontFamily: fonts.displayBold, fontSize: 24, color: colors.text, marginTop: 2 },
  avatar: { width: 40, height: 40, borderRadius: 20, backgroundColor: colors.primaryDark, alignItems: 'center', justifyContent: 'center' },
  avatarText: { fontFamily: fonts.displayBold, color: colors.bgDeep, fontSize: 14 },
  caption: { fontSize: 11.5, color: colors.textFaint, marginBottom: 16, marginTop: 8, fontFamily: fonts.sans },
  panel: { backgroundColor: '#1F2226', borderWidth: 1, borderColor: colors.border, borderRadius: 13, padding: 14, marginBottom: 16 },
  panelLabel: { fontSize: 10, letterSpacing: 1.5, color: colors.primary, marginBottom: 10, fontFamily: fonts.sansSemi },
  fieldHint: { fontSize: 11, color: colors.textFaint, marginBottom: 6, marginTop: 8, fontFamily: fonts.sans },
  segment: { flexDirection: 'row', backgroundColor: colors.bg, borderWidth: 1, borderColor: colors.borderSoft, borderRadius: 9, padding: 3, gap: 2, marginBottom: 12 },
  seg: { flex: 1, paddingVertical: 6, borderRadius: 7, alignItems: 'center' },
  segActive: { backgroundColor: colors.primary },
  segText: { fontSize: 12, color: colors.textMuted, fontFamily: fonts.sansSemi },
  segTextActive: { color: colors.bgDeep },
  rangeLabel: { fontSize: 11, color: colors.textDim, fontFamily: fonts.mono },
  kpiGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginBottom: 16 },
  kpi: {
    width: '47.8%',
    flexGrow: 1,
    backgroundColor: '#1F2226',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 13,
    padding: 14,
    overflow: 'hidden',
  },
  kpiAccent: { position: 'absolute', top: 0, left: 0, width: 4, height: '100%', backgroundColor: colors.primary },
  kpiLabel: { fontSize: 10.5, color: colors.textFaint, fontFamily: fonts.sans },
  kpiValue: { fontFamily: fonts.mono, fontSize: 20, color: colors.text, marginTop: 6 },
  kpiDelta: { fontSize: 10.5, marginTop: 4, fontFamily: fonts.sans },
  chartCard: { backgroundColor: '#1F2226', borderWidth: 1, borderColor: colors.border, borderRadius: 14, padding: 16 },
  chartHead: { flexDirection: 'row', marginBottom: 12 },
  chartTitle: { fontFamily: fonts.display, fontSize: 15, color: colors.text },
  chartSub: { fontSize: 11, color: colors.textFaint, marginTop: 2, fontFamily: fonts.sans },

  topTabs: { flexDirection: 'row', backgroundColor: '#1b1d20', borderRadius: 9, padding: 4, marginBottom: 20 },
  topTab: { flex: 1, paddingVertical: 10, borderRadius: 7, alignItems: 'center' },
  topTabActive: { backgroundColor: colors.primary },
  topTabText: { fontSize: 13, color: colors.textMuted, fontFamily: fonts.sansSemi },
  topTabTextActive: { color: colors.bgDeep },

  formTitle: { fontFamily: fonts.display, fontSize: 18, color: colors.text, marginBottom: 6 },
  addBtn: { backgroundColor: colors.primary, padding: 14, borderRadius: 10, alignItems: 'center', marginBottom: 16 },
  addBtnText: { color: colors.bgDeep, fontFamily: fonts.displayBold, letterSpacing: 1 },
  abmCard: { backgroundColor: '#1F2226', borderWidth: 1, borderColor: colors.border, borderRadius: 12, padding: 14, marginBottom: 10 },
  abmInput: { backgroundColor: colors.bg, borderWidth: 1, borderColor: colors.borderSoft, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 10, color: colors.text, fontFamily: fonts.sans, marginBottom: 4 },
  abmActions: { flexDirection: 'row', gap: 10, marginTop: 18 },
  abmBtn: { flex: 1, padding: 12, borderRadius: 8, alignItems: 'center' },
  abmBtnText: { fontFamily: fonts.sansSemi, fontSize: 14 },
  abmHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  abmIcon: { width: 44, height: 44, borderRadius: 10, backgroundColor: colors.primary + '1A', alignItems: 'center', justifyContent: 'center' },
  abmName: { fontFamily: fonts.displayBold, fontSize: 15, color: colors.text },
  abmSub: { fontSize: 11, color: colors.textFaint, marginTop: 2, fontFamily: fonts.sans },
  abmEstado: { fontSize: 10, fontFamily: fonts.sansSemi, marginTop: 3, letterSpacing: 0.5 },
  iconBtn: { width: 34, height: 34, borderRadius: 17, backgroundColor: colors.bg, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.borderSoft },
  error: { color: colors.danger, fontSize: 13, marginTop: 12, fontFamily: fonts.sans },
});
