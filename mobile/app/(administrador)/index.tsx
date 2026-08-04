import { useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View, TextInput } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { colors, fonts } from '../../constants/theme';
import { BarChart } from '../../components/fuel/BarChart';
import { Loading, ErrorState, EmptyState } from '../../components/fuel/ScreenState';
import { OptionChips } from '../../components/fuel/OptionChips';
import { FilterDropdown } from '../../components/fuel/FilterDropdown';
import { useAuth } from '../../context/AuthContext';
import { useFetch } from '../../hooks/useFetch';
import { getStats, StatsRange } from '../../services/stats';
import {
  getAdminVehiculos,
  getVehiculos,
  createVehiculo,
  updateVehiculo,
  desactivarVehiculo,
  Vehiculo,
  Estado,
  TipoVehiculo,
  TipoCombustible,
  etiquetaUso,
  etiquetaConsumo,
} from '../../services/vehiculos';
import {
  getAdminEmpleados,
  createEmpleado,
  updateEmpleado,
  desactivarEmpleado,
  Empleado,
} from '../../services/empleados';
import { getAdminPersonas, PersonaOpcion } from '../../services/personas';
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
  const [viewMode, setViewMode] = useState<'analytics' | 'vehicles' | 'personal'>('analytics');

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
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            <Pressable style={styles.operarioBtn} onPress={() => router.push('/(empleado)')}>
              <Text style={styles.operarioBtnText}>Modo operario</Text>
            </Pressable>
            <Pressable onPress={() => router.push('/(administrador)/perfil')} style={styles.avatar}>
              <Text style={styles.avatarText}>{initials}</Text>
            </Pressable>
          </View>
        </View>

        <View style={styles.topTabs}>
          <Pressable style={[styles.topTab, viewMode === 'analytics' && styles.topTabActive]} onPress={() => setViewMode('analytics')}>
            <Text style={[styles.topTabText, viewMode === 'analytics' && styles.topTabTextActive]}>Analítica</Text>
          </Pressable>
          <Pressable style={[styles.topTab, viewMode === 'vehicles' && styles.topTabActive]} onPress={() => setViewMode('vehicles')}>
            <Text style={[styles.topTabText, viewMode === 'vehicles' && styles.topTabTextActive]}>Flota</Text>
          </Pressable>
          <Pressable style={[styles.topTab, viewMode === 'personal' && styles.topTabActive]} onPress={() => setViewMode('personal')}>
            <Text style={[styles.topTabText, viewMode === 'personal' && styles.topTabTextActive]}>Personal</Text>
          </Pressable>
        </View>

        {viewMode === 'analytics' ? <Analytics /> : viewMode === 'vehicles' ? <VehiclesABM /> : <PersonalABM />}
      </ScrollView>
    </SafeAreaView>
  );
}

const CHART_VIEW_OPTS = [
  { key: 'proveedor' as const, label: 'Proveedor' },
  { key: 'usuario' as const, label: 'Usuario' },
];

// Sentinel de "Todos" para el selector de vehículo (single-select con OptionChips,
// que no admite null como key). Los ids reales de vehículo siempre son >= 1.
const TODOS_VEHICULO = -1;

/** Arma 'AAAA-MM-DD' con los componentes LOCALES del Date (no usar toISOString: corre el día por UTC). */
function toISODate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** Suma/resta un período completo (día/semana/mes) a una fecha ancla. */
function shiftAnchor(d: Date, range: StatsRange, dir: 1 | -1): Date {
  if (range === 'daily') {
    const nd = new Date(d);
    nd.setDate(nd.getDate() + dir);
    return nd;
  }
  if (range === 'weekly') {
    const nd = new Date(d);
    nd.setDate(nd.getDate() + dir * 7);
    return nd;
  }
  // monthly: usamos el constructor Date(y, m, d) para evitar los problemas de
  // "sumar 30 días" en meses de distinta longitud.
  return new Date(d.getFullYear(), d.getMonth() + dir, d.getDate());
}

/** true si el Date cae después del día de hoy (comparando solo la fecha, sin hora). */
function isFutureDay(d: Date): boolean {
  const today = new Date();
  const day = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const todayDay = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return day.getTime() > todayDay.getTime();
}

function Analytics() {
  const [range, setRange] = useState<StatsRange>('weekly');
  const [chartView, setChartView] = useState<'proveedor' | 'usuario'>('proveedor');
  const [anchor, setAnchor] = useState<Date>(new Date());
  const [vehiculoId, setVehiculoId] = useState<number | null>(null);
  const [empleadoIds, setEmpleadoIds] = useState<number[]>([]);

  const { data: filterData } = useFetch(async () => {
    const [vehiculos, personas] = await Promise.all([getVehiculos(), getAdminPersonas()]);
    return { vehiculos: vehiculos.filter((v) => v.fechaBaja === null), personas };
  });

  const fecha = toISODate(anchor);
  const { data: stats, loading, error, refetch } = useFetch(
    () => getStats(range, { fecha, vehiculoId, empleadoIds }),
    [range, fecha, vehiculoId, empleadoIds],
  );

  const changeRange = (r: StatsRange) => {
    setRange(r);
    setAnchor(new Date());
  };

  const canGoNext = !isFutureDay(shiftAnchor(anchor, range, 1));
  const goPrev = () => setAnchor(shiftAnchor(anchor, range, -1));
  const goNext = () => {
    if (canGoNext) setAnchor(shiftAnchor(anchor, range, 1));
  };

  const vehiculoOpts = [
    { key: TODOS_VEHICULO, label: 'Todos' },
    ...(filterData?.vehiculos.map((v) => ({ key: v.id, label: v.patente })) ?? []),
  ];

  const personaOpts = (filterData?.personas ?? []).map((p: PersonaOpcion) => ({
    key: p.id,
    label: p.rol === 'ADMINISTRADOR' ? `${p.nombre} ${p.apellido} (Admin)` : `${p.nombre} ${p.apellido}`,
  }));

  const toggleEmpleado = (id: number) => {
    setEmpleadoIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  };

  return (
    <>
      <Text style={styles.caption}>Consumo de combustible de toda la flota</Text>

      <View style={styles.panel}>
        <Text style={styles.panelLabel}>FILTROS</Text>
        <Text style={styles.fieldHint}>Período</Text>
        <View style={styles.segment}>
          {RANGES.map((r) => (
            <Pressable key={r.key} style={[styles.seg, range === r.key && styles.segActive]} onPress={() => changeRange(r.key)}>
              <Text style={[styles.segText, range === r.key && styles.segTextActive]}>{r.label}</Text>
            </Pressable>
          ))}
        </View>
        <View style={styles.rangeNav}>
          <Pressable style={styles.rangeArrow} onPress={goPrev}>
            <Text style={styles.rangeArrowText}>‹</Text>
          </Pressable>
          {stats && (
            <Text style={styles.rangeLabel}>
              {formatDay(stats.desde)} – {formatDay(stats.hasta)}
            </Text>
          )}
          <Pressable style={[styles.rangeArrow, !canGoNext && styles.rangeArrowDisabled]} onPress={goNext} disabled={!canGoNext}>
            <Text style={[styles.rangeArrowText, !canGoNext && styles.rangeArrowTextDisabled]}>›</Text>
          </Pressable>
        </View>

        <View style={[styles.filterRow, { marginTop: 12 }]}>
          <View style={styles.filterRowItem}>
            <FilterDropdown
              mode="multi"
              label="Usuario"
              options={personaOpts}
              selected={empleadoIds}
              onToggle={toggleEmpleado}
              onClear={() => setEmpleadoIds([])}
            />
          </View>
          <View style={styles.filterRowItem}>
            <FilterDropdown
              mode="single"
              label="Vehículo"
              options={vehiculoOpts}
              selected={vehiculoId ?? TODOS_VEHICULO}
              onSelect={(k) => setVehiculoId(k === TODOS_VEHICULO ? null : k)}
            />
          </View>
        </View>
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
                <Text style={styles.chartTitle}>
                  {chartView === 'proveedor' ? 'GASTO POR PROVEEDOR' : 'GASTO POR USUARIO'}
                </Text>
                <Text style={styles.chartSub}>
                  {chartView === 'proveedor'
                    ? 'Distribución del gasto por cada proveedor'
                    : 'Distribución del gasto por cada usuario'}
                </Text>
              </View>
            </View>
            <View style={{ marginBottom: 12 }}>
              <OptionChips options={CHART_VIEW_OPTS} value={chartView} onChange={setChartView} />
            </View>
            {chartView === 'proveedor' ? (
              stats.desglosePorProveedor.length === 0 ? (
                <EmptyState message="No hay cargas registradas en este período." />
              ) : (
                <BarChart
                  data={stats.desglosePorProveedor.map((d, i) => ({
                    l: d.nombre,
                    v: d.gasto,
                    c: CHART_COLORS[i % CHART_COLORS.length],
                    amount: formatMoney(d.gasto),
                  }))}
                />
              )
            ) : stats.desglosePorEmpleado.length === 0 ? (
              <EmptyState message="No hay cargas registradas en este período." />
            ) : (
              <BarChart
                data={stats.desglosePorEmpleado.map((d, i) => ({
                  l: d.nombreCompleto,
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
  usoAcumulado: string;
  consumoPromedio: string;
};

const emptyForm = (): FormState => ({
  patente: '',
  tipoVehiculo: null,
  tipoCombustible: null,
  capacidadTanque: '',
  estado: 'DISPONIBLE',
  fechaUltimoMantenimiento: todayISO(),
  usoAcumulado: '',
  consumoPromedio: '',
});

const formFrom = (v: Vehiculo): FormState => ({
  patente: v.patente,
  tipoVehiculo: v.tipoVehiculo,
  tipoCombustible: v.tipoCombustible,
  capacidadTanque: String(v.capacidadTanque),
  estado: v.estado,
  fechaUltimoMantenimiento: todayISO(),
  usoAcumulado: String(v.usoAcumulado),
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
    const uso = parseInt(form.usoAcumulado, 10);
    const consumo = parseFloat(form.consumoPromedio.replace(',', '.'));
    if (!(capacidad > 0)) return setFormError('Capacidad de tanque inválida.');
    if (!(uso >= 0)) return setFormError(`${etiquetaUso(form.tipoVehiculo)} inválido.`);
    if (!(consumo > 0)) return setFormError('Consumo promedio inválido.');

    const payload = {
      patente: form.patente.trim(),
      tipoVehiculo: form.tipoVehiculo,
      tipoCombustible: form.tipoCombustible,
      capacidadTanque: capacidad,
      estado: form.estado,
      fechaUltimoMantenimiento: form.fechaUltimoMantenimiento,
      usoAcumulado: uso,
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

        {/* La etiqueta sigue al tipo elegido: una máquina vial mide horas de
            horómetro, no kilómetros. */}
        <Text style={styles.fieldHint}>{etiquetaUso(form.tipoVehiculo)}</Text>
        <TextInput style={styles.abmInput} value={form.usoAcumulado} keyboardType="number-pad" onChangeText={(t) => setForm({ ...form, usoAcumulado: t })} />

        <Text style={styles.fieldHint}>{etiquetaConsumo(form.tipoVehiculo)}</Text>
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

const DOCUMENTO_REGEX = /^\d{7,9}$/;

type EmpleadoFormState = {
  nombre: string;
  apellido: string;
  documento: string;
  password: string;
  codigoArea: string;
  telefonoNumero: string;
};

const emptyEmpleadoForm = (): EmpleadoFormState => ({
  nombre: '',
  apellido: '',
  documento: '',
  password: '',
  codigoArea: '',
  telefonoNumero: '',
});

const empleadoFormFrom = (e: Empleado): EmpleadoFormState => ({
  nombre: e.nombre,
  apellido: e.apellido,
  documento: e.documento,
  password: '',
  codigoArea: e.telefono?.codigoArea ?? '',
  telefonoNumero: e.telefono?.numero ?? '',
});

function PersonalABM() {
  const { data, loading, error, refetch } = useFetch(getAdminEmpleados);

  // editing: null (lista) | 'new' | id del empleado en edición.
  const [editing, setEditing] = useState<'new' | number | null>(null);
  const [form, setForm] = useState<EmpleadoFormState>(emptyEmpleadoForm());
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const openNew = () => {
    setForm(emptyEmpleadoForm());
    setFormError(null);
    setEditing('new');
  };
  const openEdit = (e: Empleado) => {
    setForm(empleadoFormFrom(e));
    setFormError(null);
    setEditing(e.id);
  };

  const save = async () => {
    setFormError(null);
    if (!form.nombre.trim()) return setFormError('Ingresá el nombre.');
    if (!form.apellido.trim()) return setFormError('Ingresá el apellido.');
    if (editing === 'new' && !DOCUMENTO_REGEX.test(form.documento.trim())) {
      return setFormError('El documento debe tener entre 7 y 9 dígitos.');
    }
    if (editing === 'new' && form.password.length < 8) {
      return setFormError('La contraseña debe tener al menos 8 caracteres.');
    }
    if (!form.codigoArea.trim() || !form.telefonoNumero.trim()) {
      return setFormError('Completá el teléfono.');
    }

    const telefono = {
      codigoArea: form.codigoArea.trim(),
      numero: form.telefonoNumero.trim(),
    };

    try {
      setSaving(true);
      if (editing === 'new') {
        await createEmpleado({
          nombre: form.nombre.trim(),
          apellido: form.apellido.trim(),
          documento: form.documento.trim(),
          password: form.password,
          telefono,
        });
      } else if (typeof editing === 'number') {
        await updateEmpleado(editing, {
          nombre: form.nombre.trim(),
          apellido: form.apellido.trim(),
          telefono,
        });
      }
      setEditing(null);
      await refetch();
    } catch (e) {
      setFormError(e instanceof Error ? e.message : 'No se pudo guardar.');
    } finally {
      setSaving(false);
    }
  };

  const remove = (e: Empleado) => {
    Alert.alert('Dar de baja', `¿Dar de baja a ${e.nombre} ${e.apellido}?`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Dar de baja',
        style: 'destructive',
        onPress: async () => {
          try {
            await desactivarEmpleado(e.id);
            await refetch();
          } catch (err) {
            Alert.alert('Error', err instanceof Error ? err.message : 'No se pudo dar de baja.');
          }
        },
      },
    ]);
  };

  if (editing !== null) {
    return (
      <View style={{ marginTop: 4 }}>
        <Text style={styles.formTitle}>{editing === 'new' ? 'Nuevo empleado' : 'Editar empleado'}</Text>

        <Text style={styles.fieldHint}>Nombre</Text>
        <TextInput style={styles.abmInput} value={form.nombre} onChangeText={(t) => setForm({ ...form, nombre: t })} placeholder="Juan" placeholderTextColor={colors.textDim} />

        <Text style={styles.fieldHint}>Apellido</Text>
        <TextInput style={styles.abmInput} value={form.apellido} onChangeText={(t) => setForm({ ...form, apellido: t })} placeholder="Pérez" placeholderTextColor={colors.textDim} />

        {editing === 'new' && (
          <>
            <Text style={styles.fieldHint}>Documento</Text>
            <TextInput style={styles.abmInput} value={form.documento} keyboardType="number-pad" onChangeText={(t) => setForm({ ...form, documento: t })} placeholder="30123456" placeholderTextColor={colors.textDim} />
          </>
        )}

        {editing === 'new' && (
          <>
            <Text style={styles.fieldHint}>Contraseña</Text>
            <TextInput style={styles.abmInput} value={form.password} secureTextEntry onChangeText={(t) => setForm({ ...form, password: t })} placeholder="••••••••" placeholderTextColor={colors.textDim} />
          </>
        )}

        <Text style={[styles.fieldHint, { marginTop: 12 }]}>Código de área</Text>
        <TextInput style={styles.abmInput} value={form.codigoArea} keyboardType="number-pad" onChangeText={(t) => setForm({ ...form, codigoArea: t })} />

        <Text style={styles.fieldHint}>Teléfono</Text>
        <TextInput style={styles.abmInput} value={form.telefonoNumero} keyboardType="number-pad" onChangeText={(t) => setForm({ ...form, telefonoNumero: t })} />

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
        <Text style={styles.addBtnText}>+ NUEVO EMPLEADO</Text>
      </Pressable>

      {loading ? (
        <View style={{ height: 160 }}>
          <Loading />
        </View>
      ) : error ? (
        <ErrorState message={error} onRetry={refetch} />
      ) : !data || data.length === 0 ? (
        <EmptyState message="No hay empleados cargados." />
      ) : (
        data.map((e) => {
          const baja = e.fechaBaja !== null;
          const initials = `${e.nombre[0] ?? ''}${e.apellido[0] ?? ''}`.toUpperCase();
          return (
            <View key={e.id} style={[styles.abmCard, baja && { opacity: 0.5 }]}>
              <View style={styles.abmHeader}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, flex: 1, paddingRight: 10 }}>
                  <View style={styles.abmIcon}>
                    <Text style={{ color: colors.primary, fontFamily: fonts.displayBold, fontSize: 14 }}>{initials}</Text>
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.abmName} numberOfLines={1}>
                      {e.nombre} {e.apellido}
                    </Text>
                    <Text style={styles.abmSub}>
                      DNI {e.documento} · {e.username}
                      {' · '}
                      {e.telefono ? `${e.telefono.codigoArea} ${e.telefono.numero}` : '—'}
                    </Text>
                    {baja && <Text style={[styles.abmEstado, { color: colors.danger }]}>DADO DE BAJA</Text>}
                  </View>
                </View>
                {!baja && (
                  <View style={{ flexDirection: 'row', gap: 6 }}>
                    <Pressable style={styles.iconBtn} onPress={() => openEdit(e)}>
                      <Text>✏️</Text>
                    </Pressable>
                    <Pressable style={styles.iconBtn} onPress={() => remove(e)}>
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
  operarioBtn: {
    paddingHorizontal: 13,
    paddingVertical: 9,
    borderRadius: 9,
    backgroundColor: colors.primary,
  },
  operarioBtnText: { fontFamily: fonts.sansSemi, fontSize: 12, color: colors.bgDeep },
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
  rangeNav: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 14 },
  rangeArrow: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: colors.bg,
    borderWidth: 1,
    borderColor: colors.borderSoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rangeArrowDisabled: { opacity: 0.35 },
  rangeArrowText: { fontSize: 16, color: colors.primary, fontFamily: fonts.sansSemi },
  rangeArrowTextDisabled: { color: colors.textFaint },
  filterRow: { flexDirection: 'row', gap: 10 },
  filterRowItem: { flex: 1 },
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
