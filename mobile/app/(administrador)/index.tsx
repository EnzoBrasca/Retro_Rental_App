import { useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View, TextInput } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { colors, fonts } from '../../constants/theme';
import { BarChart } from '../../components/fuel/BarChart';
import { Loading, ErrorState, EmptyState } from '../../components/fuel/ScreenState';
import { OptionChips } from '../../components/fuel/OptionChips';
import { TicketsABM } from '../../components/admin/TicketsABM';
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
  unidadConsumo,
  consumoParaStats,
  etiquetaIdentificador,
  placeholderIdentificador,
  requiereModelo,
  tituloVehiculo,
} from '../../services/vehiculos';
import {
  getAdminHerramientas,
  createHerramienta,
  updateHerramienta,
  desactivarHerramienta,
  Herramienta,
} from '../../services/herramientas';
import {
  getAdminEmpleados,
  createEmpleado,
  updateEmpleado,
  desactivarEmpleado,
  Empleado,
} from '../../services/empleados';
import {
  getHabilitados,
  createHabilitado,
  deleteHabilitado,
  Habilitado,
} from '../../services/habilitados';
import { getAdminPersonas, PersonaOpcion } from '../../services/personas';
import {
  formatDay,
  formatMoney,
  tipoVehiculoLabel,
  combustibleLabel,
  estadoLabel,
  iconForTipoVehiculo,
  TIPO_HERRAMIENTA,
  TipoSeleccionVehiculo,
  tipoSeleccionLabel,
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

// Opciones del selector de tipo en el formulario de la flota. HERRAMIENTA es
// SOLO de UI (ver TipoSeleccionVehiculo): al elegirla el formulario muestra
// nombre + capacidad en vez de los campos de un vehículo, y el submit va a los
// endpoints de herramientas.
const TIPO_VEHICULO_OPTS = (Object.keys(tipoSeleccionLabel) as TipoSeleccionVehiculo[]).map(
  (k) => ({
    key: k,
    label: tipoSeleccionLabel[k],
  }),
);
const COMBUSTIBLE_OPTS = (Object.keys(combustibleLabel) as TipoCombustible[]).map((k) => ({
  key: k,
  label: combustibleLabel[k],
}));
const ESTADO_OPTS = (Object.keys(estadoLabel) as Estado[]).map((k) => ({
  key: k,
  label: estadoLabel[k],
}));

// Vistas del panel. El orden es el del uso: se entra a mirar cómo viene el
// gasto, y recién después a tocar flota, tickets o personal.
type ViewMode = 'analytics' | 'vehicles' | 'tickets' | 'personal';

const TABS: { key: ViewMode; label: string }[] = [
  { key: 'analytics', label: 'Analítica' },
  { key: 'vehicles', label: 'Flota' },
  { key: 'tickets', label: 'Tickets' },
  { key: 'personal', label: 'Personal' },
];

export default function AdministradorScreen() {
  const { user } = useAuth();
  const router = useRouter();
  const [viewMode, setViewMode] = useState<ViewMode>('analytics');

  const initials = user ? `${user.nombre[0] ?? ''}${user.apellido[0] ?? ''}`.toUpperCase() : 'AD';

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <ScrollView
        contentContainerStyle={{ padding: 16, paddingBottom: 40 }}
        showsVerticalScrollIndicator={false}
      >
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

        {/* Las pestañas se recorren desde TABS en vez de escribirse una por una:
            con cuatro, el bloque repetido era la mitad del render y agregar la
            quinta significaba copiar y pegar tres líneas más. */}
        <View style={styles.topTabs}>
          {TABS.map((tab) => {
            const activa = viewMode === tab.key;
            return (
              <Pressable
                key={tab.key}
                style={[styles.topTab, activa && styles.topTabActive]}
                onPress={() => setViewMode(tab.key)}
              >
                <Text style={[styles.topTabText, activa && styles.topTabTextActive]}>
                  {tab.label}
                </Text>
              </Pressable>
            );
          })}
        </View>

        {viewMode === 'analytics' ? (
          <Analytics />
        ) : viewMode === 'vehicles' ? (
          <VehiclesABM />
        ) : viewMode === 'tickets' ? (
          <TicketsABM />
        ) : (
          <PersonalABM />
        )}
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
  const {
    data: stats,
    loading,
    error,
    refetch,
  } = useFetch(
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
    ...(filterData?.vehiculos.map((v) => ({ key: v.id, label: tituloVehiculo(v) })) ?? []),
  ];

  const personaOpts = (filterData?.personas ?? []).map((p: PersonaOpcion) => ({
    key: p.id,
    label:
      p.rol === 'ADMINISTRADOR' ? `${p.nombre} ${p.apellido} (Admin)` : `${p.nombre} ${p.apellido}`,
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
            <Pressable
              key={r.key}
              style={[styles.seg, range === r.key && styles.segActive]}
              onPress={() => changeRange(r.key)}
            >
              <Text style={[styles.segText, range === r.key && styles.segTextActive]}>
                {r.label}
              </Text>
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
          <Pressable
            style={[styles.rangeArrow, !canGoNext && styles.rangeArrowDisabled]}
            onPress={goNext}
            disabled={!canGoNext}
          >
            <Text style={[styles.rangeArrowText, !canGoNext && styles.rangeArrowTextDisabled]}>
              ›
            </Text>
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
            <Kpi
              label="Costo total"
              value={formatMoney(stats.gastoTotal)}
              valueColor={colors.primary}
              delta={`${stats.cantidadRegistros} cargas`}
              deltaColor={colors.textFaint}
              accent
            />
            <Kpi
              label="Litros cargados"
              value={`${stats.totalLitros} L`}
              delta={`${stats.vehiculosActivos} vehículos`}
              deltaColor={colors.textFaint}
            />
            <Kpi
              label="Promedio / carga"
              value={
                stats.cantidadRegistros > 0
                  ? formatMoney(stats.gastoTotal / stats.cantidadRegistros)
                  : '$0'
              }
              delta="por carga"
              deltaColor={colors.orange}
            />
            {/* Un mismo slot con dos lecturas, según haya o no un vehículo
                filtrado. Con "Todos" el consumo se omite a propósito: promediar
                L/h de las máquinas con los km/L de los camiones no da un número
                con sentido. Con un vehículo elegido, en cambio, "litros /
                vehículo" sería solo el total repetido. */}
            {stats.consumoPeriodo != null && stats.unidadUso != null ? (
              <Kpi
                label="Consumo"
                value={consumoParaStats(stats.consumoPeriodo, stats.unidadUso)}
                delta="en el período"
                deltaColor={colors.textFaint}
              />
            ) : vehiculoId != null ? (
              // Vehículo elegido pero sin dos lecturas en el período con las que
              // formar un intervalo. Se dice, en vez de mostrar un cero que se
              // leería como "no consumió".
              <Kpi
                label="Consumo"
                value="—"
                delta="sin datos suficientes"
                deltaColor={colors.textFaint}
              />
            ) : (
              <Kpi
                label="Litros / vehículo"
                value={`${Math.round(stats.promedioLitrosPorVehiculo)} L`}
                delta="promedio"
                deltaColor={colors.textFaint}
              />
            )}
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

// Item de la lista unificada de la flota: un vehículo o una herramienta. Son
// tablas distintas en el backend, así que se representan como una unión
// discriminada en vez de forzarlas a un único shape.
type FlotaItem = { kind: 'vehiculo'; v: Vehiculo } | { kind: 'herramienta'; h: Herramienta };

type FormState = {
  identificador: string;
  modelo: string;
  // 'HERRAMIENTA' es la opción de UI que no existe en el backend (ver
  // TipoSeleccionVehiculo). Cuando vale eso, el resto de los campos de
  // vehículo se ignoran y se usan nombreHerramienta + capacidadTanque.
  tipoVehiculo: TipoSeleccionVehiculo | null;
  tipoCombustible: TipoCombustible | null;
  capacidadTanque: string;
  estado: Estado;
  fechaUltimoMantenimiento: string;
  usoAcumulado: string;
  consumoPromedio: string;
  // Solo para HERRAMIENTA: una herramienta no tiene identificador, se nombra
  // directamente.
  nombreHerramienta: string;
};

const emptyForm = (): FormState => ({
  identificador: '',
  modelo: '',
  tipoVehiculo: null,
  tipoCombustible: null,
  capacidadTanque: '',
  estado: 'DISPONIBLE',
  fechaUltimoMantenimiento: todayISO(),
  usoAcumulado: '',
  consumoPromedio: '',
  nombreHerramienta: '',
});

const formFrom = (v: Vehiculo): FormState => ({
  identificador: v.identificador,
  // Los vehículos cargados antes del cambio no tienen modelo. El input no
  // acepta null, y editar una máquina vieja va a exigir completarlo.
  modelo: v.modelo ?? '',
  tipoVehiculo: v.tipoVehiculo,
  tipoCombustible: v.tipoCombustible,
  capacidadTanque: String(v.capacidadTanque),
  estado: v.estado,
  // La fecha REAL del vehículo, no la de hoy: el payload de edición la exige,
  // así que inventarla acá le pisaba el mantenimiento registrado a cualquier
  // vehículo con solo abrirlo y guardar.
  fechaUltimoMantenimiento: v.fechaUltimoMantenimiento,
  usoAcumulado: String(v.usoAcumulado),
  consumoPromedio: String(v.consumoPromedio),
  nombreHerramienta: '',
});

const formFromHerramienta = (h: Herramienta): FormState => ({
  identificador: '',
  modelo: '',
  tipoVehiculo: TIPO_HERRAMIENTA,
  tipoCombustible: null,
  capacidadTanque: String(h.capacidad),
  estado: 'DISPONIBLE',
  fechaUltimoMantenimiento: todayISO(),
  usoAcumulado: '',
  consumoPromedio: '',
  nombreHerramienta: h.nombre,
});

function VehiclesABM() {
  const { data, loading, error, refetch } = useFetch(async () => {
    const [vehiculos, herramientas] = await Promise.all([
      getAdminVehiculos(),
      getAdminHerramientas(),
    ]);
    return { vehiculos, herramientas };
  });

  // editing: null (lista) | 'new' | id de la fila en edición.
  const [editing, setEditing] = useState<'new' | number | null>(null);
  // De qué tabla es la fila que se está editando. null en 'new' (ahí el tipo
  // es libre) y en la lista. Es la base del bloqueo del punto 6: una vez que
  // se sabe que la fila es un vehículo o una herramienta, ya no se puede
  // cruzar a la otra categoría porque son registros distintos en el backend.
  const [editingKind, setEditingKind] = useState<'vehiculo' | 'herramienta' | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm());
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const openNew = () => {
    setForm(emptyForm());
    setFormError(null);
    setEditingKind(null);
    setEditing('new');
  };
  const openEdit = (item: FlotaItem) => {
    setFormError(null);
    if (item.kind === 'vehiculo') {
      setForm(formFrom(item.v));
      setEditingKind('vehiculo');
      setEditing(item.v.id);
    } else {
      setForm(formFromHerramienta(item.h));
      setEditingKind('herramienta');
      setEditing(item.h.id);
    }
  };

  // Al editar, el tipo no puede cruzar entre vehículo y herramienta: son
  // tablas distintas y "convertir" uno en otro editando no tiene un endpoint
  // que lo soporte. Se deshabilitan las opciones que no aplican en vez de
  // dejar elegirlas y fallar recién al guardar.
  const disabledTipoKeys: TipoSeleccionVehiculo[] =
    editing === 'new'
      ? []
      : editingKind === 'herramienta'
        ? ['MAQUINA', 'CAMIONETA', 'CAMION']
        : [TIPO_HERRAMIENTA];

  const save = async () => {
    setFormError(null);
    // El tipo se valida primero: sin él no se sabe qué formulario mostrar ni a
    // qué endpoint mandar el alta/edición.
    if (!form.tipoVehiculo) return setFormError('Elegí el tipo de vehículo.');

    if (form.tipoVehiculo === TIPO_HERRAMIENTA) {
      if (!form.nombreHerramienta.trim())
        return setFormError('Ingresá el nombre de la herramienta.');
      const capacidad = parseInt(form.capacidadTanque, 10);
      if (!(capacidad > 0)) return setFormError('Capacidad inválida.');

      const payload = { nombre: form.nombreHerramienta.trim(), capacidad };
      try {
        setSaving(true);
        if (editing === 'new') await createHerramienta(payload);
        else if (typeof editing === 'number') await updateHerramienta(editing, payload);
        setEditing(null);
        await refetch();
      } catch (e) {
        setFormError(e instanceof Error ? e.message : 'No se pudo guardar.');
      } finally {
        setSaving(false);
      }
      return;
    }

    // A partir de acá form.tipoVehiculo ya no puede ser HERRAMIENTA (return
    // arriba), así que TypeScript lo angosta a TipoVehiculo.
    if (!form.identificador.trim()) {
      return setFormError(`Ingresá ${etiquetaIdentificador(form.tipoVehiculo).toLowerCase()}.`);
    }
    if (requiereModelo(form.tipoVehiculo) && !form.modelo.trim()) {
      return setFormError('Ingresá el modelo de la máquina.');
    }
    if (!form.tipoCombustible) return setFormError('Elegí el combustible.');
    const capacidad = parseInt(form.capacidadTanque, 10);
    const uso = parseInt(form.usoAcumulado, 10);
    const consumo = parseFloat(form.consumoPromedio.replace(',', '.'));
    if (!(capacidad > 0)) return setFormError('Capacidad de tanque inválida.');
    if (!(uso >= 0)) return setFormError(`${etiquetaUso(form.tipoVehiculo)} inválido.`);
    if (!(consumo > 0)) return setFormError('Consumo promedio inválido.');

    const payload = {
      identificador: form.identificador.trim(),
      // "" no es un modelo vacío: es la ausencia de modelo. Se manda null para
      // que el backend no guarde una cadena vacía.
      modelo: form.modelo.trim() || null,
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

  const removeVehiculo = (v: Vehiculo) => {
    Alert.alert('Dar de baja', `¿Dar de baja el vehículo ${tituloVehiculo(v)}?`, [
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

  const removeHerramienta = (h: Herramienta) => {
    Alert.alert('Dar de baja', `¿Dar de baja la herramienta ${h.nombre}?`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Dar de baja',
        style: 'destructive',
        onPress: async () => {
          try {
            await desactivarHerramienta(h.id);
            await refetch();
          } catch (e) {
            Alert.alert('Error', e instanceof Error ? e.message : 'No se pudo dar de baja.');
          }
        },
      },
    ]);
  };

  if (editing !== null) {
    const esHerramienta = form.tipoVehiculo === TIPO_HERRAMIENTA;
    // El JSX de abajo separa las dos ramas con un ternario sobre `esHerramienta`,
    // pero TS no lo usa para angostar `form.tipoVehiculo` dentro del JSX (no es
    // un narrowing directo sobre la unión). Esta variable evita repetir el cast
    // en cada uso de la rama de vehículo.
    const tipoVehiculoForm: TipoVehiculo | null = esHerramienta
      ? null
      : (form.tipoVehiculo as TipoVehiculo | null);
    return (
      <View style={{ marginTop: 4 }}>
        <Text style={styles.formTitle}>
          {editing === 'new'
            ? 'Nuevo vehículo'
            : esHerramienta
              ? 'Editar herramienta'
              : 'Editar vehículo'}
        </Text>

        {/* El tipo va PRIMERO: de él dependen qué campos aparecen debajo (los
            de un vehículo, o solo nombre + capacidad de una herramienta). */}
        <Text style={styles.fieldHint}>Tipo de vehículo</Text>
        <OptionChips
          options={TIPO_VEHICULO_OPTS}
          value={form.tipoVehiculo}
          onChange={(k) => setForm({ ...form, tipoVehiculo: k })}
          disabledKeys={disabledTipoKeys}
        />
        {editing !== 'new' && (
          <Text style={styles.fieldNote}>
            Vehículo y herramienta son registros distintos: no se puede convertir uno en otro
            editando. Para eso, dalo de baja y cargalo de nuevo con el tipo correcto.
          </Text>
        )}

        {esHerramienta ? (
          <>
            <Text style={[styles.fieldHint, { marginTop: 12 }]}>Nombre</Text>
            <TextInput
              style={styles.abmInput}
              value={form.nombreHerramienta}
              onChangeText={(t) => setForm({ ...form, nombreHerramienta: t })}
              placeholder="Motosierra Stihl"
              placeholderTextColor={colors.textDim}
            />

            <Text style={[styles.fieldHint, { marginTop: 12 }]}>Capacidad (L)</Text>
            <TextInput
              style={styles.abmInput}
              value={form.capacidadTanque}
              keyboardType="number-pad"
              onChangeText={(t) => setForm({ ...form, capacidadTanque: t })}
            />
          </>
        ) : (
          <>
            <Text style={[styles.fieldHint, { marginTop: 12 }]}>
              {etiquetaIdentificador(tipoVehiculoForm)}
            </Text>
            <TextInput
              style={styles.abmInput}
              value={form.identificador}
              autoCapitalize="characters"
              onChangeText={(t) => setForm({ ...form, identificador: t })}
              placeholder={placeholderIdentificador(tipoVehiculoForm)}
              placeholderTextColor={colors.textDim}
            />

            {requiereModelo(tipoVehiculoForm) && (
              <>
                <Text style={[styles.fieldHint, { marginTop: 12 }]}>Modelo</Text>
                <TextInput
                  style={styles.abmInput}
                  value={form.modelo}
                  onChangeText={(t) => setForm({ ...form, modelo: t })}
                  placeholder="CAT 320D"
                  placeholderTextColor={colors.textDim}
                />
                <Text style={styles.fieldNote}>
                  Dos máquinas pueden compartir modelo: el número interno es el que las distingue.
                </Text>
              </>
            )}

            <Text style={[styles.fieldHint, { marginTop: 12 }]}>Combustible</Text>
            <OptionChips
              options={COMBUSTIBLE_OPTS}
              value={form.tipoCombustible}
              onChange={(k) => setForm({ ...form, tipoCombustible: k })}
            />

            <Text style={[styles.fieldHint, { marginTop: 12 }]}>Estado</Text>
            <OptionChips
              options={ESTADO_OPTS}
              value={form.estado}
              onChange={(k) => setForm({ ...form, estado: k })}
            />

            <Text style={[styles.fieldHint, { marginTop: 12 }]}>Capacidad de tanque (L)</Text>
            <TextInput
              style={styles.abmInput}
              value={form.capacidadTanque}
              keyboardType="number-pad"
              onChangeText={(t) => setForm({ ...form, capacidadTanque: t })}
            />

            {/* La etiqueta sigue al tipo elegido: una máquina vial mide horas de
                horómetro, no kilómetros. */}
            <Text style={styles.fieldHint}>{etiquetaUso(tipoVehiculoForm)}</Text>
            <TextInput
              style={styles.abmInput}
              value={form.usoAcumulado}
              keyboardType="number-pad"
              onChangeText={(t) => setForm({ ...form, usoAcumulado: t })}
            />

            <Text style={styles.fieldHint}>{etiquetaConsumo(tipoVehiculoForm)}</Text>
            <TextInput
              style={styles.abmInput}
              value={form.consumoPromedio}
              keyboardType="numeric"
              onChangeText={(t) => setForm({ ...form, consumoPromedio: t })}
            />
            <Text style={styles.abmHint}>
              Estimación inicial. A partir de la segunda carga se reemplaza por el consumo real
              calculado con las cargas del vehículo.
            </Text>

            <Text style={styles.fieldHint}>Último mantenimiento (AAAA-MM-DD)</Text>
            <TextInput
              style={styles.abmInput}
              value={form.fechaUltimoMantenimiento}
              onChangeText={(t) => setForm({ ...form, fechaUltimoMantenimiento: t })}
            />
          </>
        )}

        {formError && <Text style={styles.error}>{formError}</Text>}

        <View style={styles.abmActions}>
          <Pressable
            style={[styles.abmBtn, { backgroundColor: colors.primary }]}
            onPress={save}
            disabled={saving}
          >
            <Text style={[styles.abmBtnText, { color: colors.bgDeep }]}>
              {saving ? 'Guardando…' : 'Guardar'}
            </Text>
          </Pressable>
          <Pressable
            style={[styles.abmBtn, { backgroundColor: '#1b1d20' }]}
            onPress={() => setEditing(null)}
            disabled={saving}
          >
            <Text style={[styles.abmBtnText, { color: colors.text }]}>Cancelar</Text>
          </Pressable>
        </View>
      </View>
    );
  }

  // Lista unificada: vehículos y herramientas conviven en la misma sección de
  // Flota (no hay una pestaña aparte para herramientas).
  const items: FlotaItem[] = data
    ? [
        ...data.vehiculos.map((v): FlotaItem => ({ kind: 'vehiculo', v })),
        ...data.herramientas.map((h): FlotaItem => ({ kind: 'herramienta', h })),
      ]
    : [];

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
      ) : items.length === 0 ? (
        <EmptyState message="No hay vehículos ni herramientas cargados. Agregá el primero con los datos reales de la empresa." />
      ) : (
        items.map((item) => {
          if (item.kind === 'vehiculo') {
            const v = item.v;
            const Icon = iconForTipoVehiculo(v.tipoVehiculo);
            const est = estadoStyle[v.estado];
            const baja = v.fechaBaja !== null;
            return (
              <View key={`v-${v.id}`} style={[styles.abmCard, baja && { opacity: 0.5 }]}>
                <View style={styles.abmHeader}>
                  <View
                    style={{
                      flexDirection: 'row',
                      alignItems: 'center',
                      gap: 10,
                      flex: 1,
                      paddingRight: 10,
                    }}
                  >
                    <View style={styles.abmIcon}>
                      <Icon width={24} height={24} color={colors.primary} />
                    </View>
                    <View style={{ flex: 1 }}>
                      {/* Mismo nombre que ve el operario: en una máquina manda el
                          modelo con el interno entre paréntesis, en el resto la
                          patente. Si el admin y el operario nombraran distinto al
                          mismo vehículo, no podrían entenderse por teléfono. */}
                      <Text style={styles.abmName} numberOfLines={1}>
                        {tituloVehiculo(v)}
                      </Text>
                      <Text style={styles.abmSub}>
                        {tipoVehiculoLabel[v.tipoVehiculo]} · {combustibleLabel[v.tipoCombustible]}{' '}
                        · {v.consumoPromedio} {unidadConsumo(v.tipoVehiculo)}
                      </Text>
                      {/* El reciente solo se muestra cuando difiere del histórico:
                          si son iguales no aporta nada, y cuando se despega es
                          justamente la señal que interesa ver. */}
                      {v.consumoReciente != null && v.consumoReciente !== v.consumoPromedio && (
                        <Text style={styles.abmSub}>
                          Últimas cargas: {v.consumoReciente} {unidadConsumo(v.tipoVehiculo)}
                        </Text>
                      )}
                      <Text style={[styles.abmEstado, { color: est.color }]}>
                        {baja ? 'DADO DE BAJA' : estadoLabel[v.estado]}
                      </Text>
                    </View>
                  </View>
                  {!baja && (
                    <View style={{ flexDirection: 'row', gap: 6 }}>
                      <Pressable style={styles.iconBtn} onPress={() => openEdit(item)}>
                        <Text>✏️</Text>
                      </Pressable>
                      <Pressable style={styles.iconBtn} onPress={() => removeVehiculo(v)}>
                        <Text>🗑️</Text>
                      </Pressable>
                    </View>
                  )}
                </View>
              </View>
            );
          }

          // Herramienta: se identifica por su nombre (no tiene identificador ni
          // patente), y no muestra estado/consumo porque no aplican.
          const h = item.h;
          const baja = (h.fechaBaja ?? null) !== null;
          return (
            <View key={`h-${h.id}`} style={[styles.abmCard, baja && { opacity: 0.5 }]}>
              <View style={styles.abmHeader}>
                <View
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: 10,
                    flex: 1,
                    paddingRight: 10,
                  }}
                >
                  <View style={styles.abmIcon}>
                    <Text style={{ fontSize: 20 }}>🔧</Text>
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.abmName} numberOfLines={1}>
                      {h.nombre}
                    </Text>
                    <Text style={styles.abmSub}>Herramienta · Capacidad {h.capacidad} L</Text>
                    {baja && (
                      <Text style={[styles.abmEstado, { color: colors.danger }]}>DADO DE BAJA</Text>
                    )}
                  </View>
                </View>
                {!baja && (
                  <View style={{ flexDirection: 'row', gap: 6 }}>
                    <Pressable style={styles.iconBtn} onPress={() => openEdit(item)}>
                      <Text>✏️</Text>
                    </Pressable>
                    <Pressable style={styles.iconBtn} onPress={() => removeHerramienta(h)}>
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

/**
 * Pestaña "Personal": dos vistas de la misma realidad.
 *
 *  - Empleados: quienes YA tienen cuenta.
 *  - Habilitados: los documentos autorizados a crearse una.
 *
 * Van juntas y no en pestañas separadas del nivel superior porque el jefe
 * piensa en "mi gente", no en dos entidades: habilitar a alguien es el paso
 * previo a que aparezca en la otra lista, no una tarea de otro rubro.
 */
function PersonalABM() {
  const [sub, setSub] = useState<'empleados' | 'habilitados'>('empleados');

  return (
    <View style={{ flex: 1 }}>
      <View style={[styles.topTabs, { marginBottom: 14 }]}>
        <Pressable
          style={[styles.topTab, sub === 'empleados' && styles.topTabActive]}
          onPress={() => setSub('empleados')}
        >
          <Text style={[styles.topTabText, sub === 'empleados' && styles.topTabTextActive]}>
            Empleados
          </Text>
        </Pressable>
        <Pressable
          style={[styles.topTab, sub === 'habilitados' && styles.topTabActive]}
          onPress={() => setSub('habilitados')}
        >
          <Text style={[styles.topTabText, sub === 'habilitados' && styles.topTabTextActive]}>
            Habilitados
          </Text>
        </Pressable>
      </View>

      {sub === 'empleados' ? <EmpleadosList /> : <HabilitadosList />}
    </View>
  );
}

function EmpleadosList() {
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
        <Text style={styles.formTitle}>
          {editing === 'new' ? 'Nuevo empleado' : 'Editar empleado'}
        </Text>

        <Text style={styles.fieldHint}>Nombre</Text>
        <TextInput
          style={styles.abmInput}
          value={form.nombre}
          onChangeText={(t) => setForm({ ...form, nombre: t })}
          placeholder="Juan"
          placeholderTextColor={colors.textDim}
        />

        <Text style={styles.fieldHint}>Apellido</Text>
        <TextInput
          style={styles.abmInput}
          value={form.apellido}
          onChangeText={(t) => setForm({ ...form, apellido: t })}
          placeholder="Pérez"
          placeholderTextColor={colors.textDim}
        />

        {editing === 'new' && (
          <>
            <Text style={styles.fieldHint}>Documento</Text>
            <TextInput
              style={styles.abmInput}
              value={form.documento}
              keyboardType="number-pad"
              onChangeText={(t) => setForm({ ...form, documento: t })}
              placeholder="30123456"
              placeholderTextColor={colors.textDim}
            />
          </>
        )}

        {editing === 'new' && (
          <>
            <Text style={styles.fieldHint}>Contraseña</Text>
            <TextInput
              style={styles.abmInput}
              value={form.password}
              secureTextEntry
              onChangeText={(t) => setForm({ ...form, password: t })}
              placeholder="••••••••"
              placeholderTextColor={colors.textDim}
            />
          </>
        )}

        <Text style={[styles.fieldHint, { marginTop: 12 }]}>Código de área</Text>
        <TextInput
          style={styles.abmInput}
          value={form.codigoArea}
          keyboardType="number-pad"
          onChangeText={(t) => setForm({ ...form, codigoArea: t })}
        />

        <Text style={styles.fieldHint}>Teléfono</Text>
        <TextInput
          style={styles.abmInput}
          value={form.telefonoNumero}
          keyboardType="number-pad"
          onChangeText={(t) => setForm({ ...form, telefonoNumero: t })}
        />

        {formError && <Text style={styles.error}>{formError}</Text>}

        <View style={styles.abmActions}>
          <Pressable
            style={[styles.abmBtn, { backgroundColor: colors.primary }]}
            onPress={save}
            disabled={saving}
          >
            <Text style={[styles.abmBtnText, { color: colors.bgDeep }]}>
              {saving ? 'Guardando…' : 'Guardar'}
            </Text>
          </Pressable>
          <Pressable
            style={[styles.abmBtn, { backgroundColor: '#1b1d20' }]}
            onPress={() => setEditing(null)}
            disabled={saving}
          >
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
                <View
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: 10,
                    flex: 1,
                    paddingRight: 10,
                  }}
                >
                  <View style={styles.abmIcon}>
                    <Text
                      style={{ color: colors.primary, fontFamily: fonts.displayBold, fontSize: 14 }}
                    >
                      {initials}
                    </Text>
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
                    {baja && (
                      <Text style={[styles.abmEstado, { color: colors.danger }]}>DADO DE BAJA</Text>
                    )}
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

/**
 * Padrón de habilitados: los documentos que el jefe autoriza a registrarse.
 *
 * Por qué existe esta pantalla: /auth/register es público a propósito (el
 * empleado se da de alta solo, sin fricción). Sin padrón, "público" significaba
 * que cualquier persona de internet con la URL de la API obtenía una cuenta de
 * empleado válida. Acá el jefe carga los documentos de su nómina — que ya
 * tiene — y solo esa gente puede registrarse.
 *
 * Cada fila muestra su estado (sin registrar / registrado), así el jefe ve en
 * un solo lugar a quién habilitó y quién efectivamente entró.
 */
function HabilitadosList() {
  const { data, loading, error, refetch } = useFetch(getHabilitados);

  const [creando, setCreando] = useState(false);
  const [documento, setDocumento] = useState('');
  const [apellido, setApellido] = useState('');
  const [nombre, setNombre] = useState('');
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const abrirNuevo = () => {
    setDocumento('');
    setApellido('');
    setNombre('');
    setFormError(null);
    setCreando(true);
  };

  const guardar = async () => {
    setFormError(null);
    if (!DOCUMENTO_REGEX.test(documento.trim())) {
      return setFormError('El documento debe tener entre 7 y 9 dígitos.');
    }
    if (!apellido.trim()) {
      return setFormError('Ingresá el apellido.');
    }

    try {
      setSaving(true);
      await createHabilitado({
        documento: documento.trim(),
        apellido: apellido.trim(),
        nombre: nombre.trim() || undefined,
      });
      setCreando(false);
      await refetch();
    } catch (e) {
      setFormError(e instanceof Error ? e.message : 'No se pudo guardar.');
    } finally {
      setSaving(false);
    }
  };

  const quitar = (h: Habilitado) => {
    Alert.alert(
      'Quitar del padrón',
      `¿Quitar el documento ${h.documento}? No va a poder registrarse en la app.`,
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Quitar',
          style: 'destructive',
          onPress: async () => {
            try {
              await deleteHabilitado(h.id);
              await refetch();
            } catch (err) {
              Alert.alert('Error', err instanceof Error ? err.message : 'No se pudo quitar.');
            }
          },
        },
      ],
    );
  };

  if (creando) {
    return (
      <View style={{ marginTop: 4 }}>
        <Text style={styles.formTitle}>Habilitar empleado</Text>
        <Text style={[styles.abmSub, { marginBottom: 12 }]}>
          El empleado se registra solo desde la app. El apellido tiene que coincidir con el que
          cargues acá.
        </Text>

        <Text style={styles.fieldHint}>Documento</Text>
        <TextInput
          style={styles.abmInput}
          value={documento}
          keyboardType="number-pad"
          onChangeText={setDocumento}
          placeholder="30123456"
          placeholderTextColor={colors.textDim}
        />

        <Text style={styles.fieldHint}>Apellido</Text>
        <TextInput
          style={styles.abmInput}
          value={apellido}
          onChangeText={setApellido}
          placeholder="Pérez"
          placeholderTextColor={colors.textDim}
        />

        <Text style={styles.fieldHint}>Nombre (opcional)</Text>
        <TextInput
          style={styles.abmInput}
          value={nombre}
          onChangeText={setNombre}
          placeholder="Juan"
          placeholderTextColor={colors.textDim}
        />

        {formError && <Text style={styles.error}>{formError}</Text>}

        <View style={styles.abmActions}>
          <Pressable
            style={[styles.abmBtn, { backgroundColor: colors.primary }]}
            onPress={guardar}
            disabled={saving}
          >
            <Text style={[styles.abmBtnText, { color: colors.bgDeep }]}>
              {saving ? 'Guardando…' : 'Habilitar'}
            </Text>
          </Pressable>
          <Pressable
            style={[styles.abmBtn, { backgroundColor: '#1b1d20' }]}
            onPress={() => setCreando(false)}
            disabled={saving}
          >
            <Text style={[styles.abmBtnText, { color: colors.text }]}>Cancelar</Text>
          </Pressable>
        </View>
      </View>
    );
  }

  return (
    <View style={{ flex: 1, marginTop: 4 }}>
      <Pressable style={styles.addBtn} onPress={abrirNuevo}>
        <Text style={styles.addBtnText}>+ HABILITAR EMPLEADO</Text>
      </Pressable>

      {loading ? (
        <View style={{ height: 160 }}>
          <Loading />
        </View>
      ) : error ? (
        <ErrorState message={error} onRetry={refetch} />
      ) : !data || data.length === 0 ? (
        <EmptyState message="Nadie habilitado todavía. Sin esto, nadie puede registrarse en la app." />
      ) : (
        data.map((h) => (
          <View key={h.id} style={[styles.abmCard, h.registrado && { opacity: 0.6 }]}>
            <View style={styles.abmHeader}>
              <View style={{ flex: 1, paddingRight: 10 }}>
                <Text style={styles.abmName} numberOfLines={1}>
                  {h.nombre ? `${h.nombre} ${h.apellido}` : h.apellido}
                </Text>
                <Text style={styles.abmSub}>DNI {h.documento}</Text>
                <Text
                  style={[
                    styles.abmEstado,
                    { color: h.registrado ? colors.textMuted : colors.primary },
                  ]}
                >
                  {h.registrado ? `REGISTRADO · ${h.username}` : 'SIN REGISTRAR'}
                </Text>
              </View>
              {!h.registrado && (
                <Pressable style={styles.iconBtn} onPress={() => quitar(h)}>
                  <Text>🗑️</Text>
                </Pressable>
              )}
            </View>
          </View>
        ))
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
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  eyebrow: { fontSize: 11, color: colors.textFaint, letterSpacing: 1.5, fontFamily: fonts.sans },
  h1: { fontFamily: fonts.displayBold, fontSize: 24, color: colors.text, marginTop: 2 },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: colors.primaryDark,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: { fontFamily: fonts.displayBold, color: colors.bgDeep, fontSize: 14 },
  operarioBtn: {
    paddingHorizontal: 13,
    paddingVertical: 9,
    borderRadius: 9,
    backgroundColor: colors.primary,
  },
  operarioBtnText: { fontFamily: fonts.sansSemi, fontSize: 12, color: colors.bgDeep },
  caption: {
    fontSize: 11.5,
    color: colors.textFaint,
    marginBottom: 16,
    marginTop: 8,
    fontFamily: fonts.sans,
  },
  panel: {
    backgroundColor: '#1F2226',
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
  fieldHint: {
    fontSize: 11,
    color: colors.textFaint,
    marginBottom: 6,
    marginTop: 8,
    fontFamily: fonts.sans,
  },
  // Aclaración bajo un input, para explicar por qué se pide el dato.
  fieldNote: {
    fontSize: 11,
    color: colors.textDim,
    marginTop: 6,
    lineHeight: 15,
    fontFamily: fonts.sans,
  },
  abmHint: {
    fontSize: 11,
    color: colors.textDim,
    marginTop: 4,
    lineHeight: 15,
    fontFamily: fonts.sans,
  },
  segment: {
    flexDirection: 'row',
    backgroundColor: colors.bg,
    borderWidth: 1,
    borderColor: colors.borderSoft,
    borderRadius: 9,
    padding: 3,
    gap: 2,
    marginBottom: 12,
  },
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
  kpiAccent: {
    position: 'absolute',
    top: 0,
    left: 0,
    width: 4,
    height: '100%',
    backgroundColor: colors.primary,
  },
  kpiLabel: { fontSize: 10.5, color: colors.textFaint, fontFamily: fonts.sans },
  kpiValue: { fontFamily: fonts.mono, fontSize: 20, color: colors.text, marginTop: 6 },
  kpiDelta: { fontSize: 10.5, marginTop: 4, fontFamily: fonts.sans },
  chartCard: {
    backgroundColor: '#1F2226',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 14,
    padding: 16,
  },
  chartHead: { flexDirection: 'row', marginBottom: 12 },
  chartTitle: { fontFamily: fonts.display, fontSize: 15, color: colors.text },
  chartSub: { fontSize: 11, color: colors.textFaint, marginTop: 2, fontFamily: fonts.sans },

  topTabs: {
    flexDirection: 'row',
    backgroundColor: '#1b1d20',
    borderRadius: 9,
    padding: 4,
    marginBottom: 20,
  },
  topTab: { flex: 1, paddingVertical: 10, borderRadius: 7, alignItems: 'center' },
  topTabActive: { backgroundColor: colors.primary },
  // 12px y no 13: con cuatro pestañas, "Analítica" se cortaba en pantallas de
  // 360dp de ancho, que son las que tienen los teléfonos de la obra.
  topTabText: { fontSize: 12, color: colors.textMuted, fontFamily: fonts.sansSemi },
  topTabTextActive: { color: colors.bgDeep },

  formTitle: { fontFamily: fonts.display, fontSize: 18, color: colors.text, marginBottom: 6 },
  addBtn: {
    backgroundColor: colors.primary,
    padding: 14,
    borderRadius: 10,
    alignItems: 'center',
    marginBottom: 16,
  },
  addBtnText: { color: colors.bgDeep, fontFamily: fonts.displayBold, letterSpacing: 1 },
  abmCard: {
    backgroundColor: '#1F2226',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
  },
  abmInput: {
    backgroundColor: colors.bg,
    borderWidth: 1,
    borderColor: colors.borderSoft,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    color: colors.text,
    fontFamily: fonts.sans,
    marginBottom: 4,
  },
  abmActions: { flexDirection: 'row', gap: 10, marginTop: 18 },
  abmBtn: { flex: 1, padding: 12, borderRadius: 8, alignItems: 'center' },
  abmBtnText: { fontFamily: fonts.sansSemi, fontSize: 14 },
  abmHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  abmIcon: {
    width: 44,
    height: 44,
    borderRadius: 10,
    backgroundColor: colors.primary + '1A',
    alignItems: 'center',
    justifyContent: 'center',
  },
  abmName: { fontFamily: fonts.displayBold, fontSize: 15, color: colors.text },
  abmSub: { fontSize: 11, color: colors.textFaint, marginTop: 2, fontFamily: fonts.sans },
  abmEstado: { fontSize: 10, fontFamily: fonts.sansSemi, marginTop: 3, letterSpacing: 0.5 },
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
  error: { color: colors.danger, fontSize: 13, marginTop: 12, fontFamily: fonts.sans },
});
