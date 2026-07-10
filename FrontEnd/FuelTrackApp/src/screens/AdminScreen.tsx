import React, { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View, TextInput } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { colors, fonts } from '../theme';
import { kpis, providerData, rangeLabel, vehicleData, userData, type Range, MACHINERY, updateMachinery, type Machine } from '../data/mock';
import { BarChart } from '../components/BarChart';
import { useAuth } from '../context/AuthContext';
import IconPickup from '../assets/icons/005-camioneta.svg';

type ChartKind = 'vehicle' | 'provider' | 'user';

const RANGES: { key: Range; label: string }[] = [
  { key: '1d', label: 'Diario' },
  { key: '7d', label: 'Semanal' },
  { key: '30d', label: 'Mensual' },
];

const CHARTS: { key: ChartKind; label: string }[] = [
  { key: 'vehicle', label: 'Por Vehículo' },
  { key: 'provider', label: 'Por Proveedor' },
  { key: 'user', label: 'Por Operario' },
];

const CHART_TITLES: Record<ChartKind, { title: string; sub: string }> = {
  vehicle: { title: 'COSTO POR VEHÍCULO', sub: 'Distribución del gasto por cada unidad' },
  provider: { title: 'COSTO POR PROVEEDOR', sub: 'Distribución del gasto' },
  user: { title: 'COSTO POR OPERARIO', sub: 'Gasto asignado a cada usuario' },
};

export function AdminScreen() {
  const { logout } = useAuth();
  const [viewMode, setViewMode] = useState<'analytics' | 'vehicles'>('analytics');
  const [range, setRange] = useState<Range>('7d');
  const [chart, setChart] = useState<ChartKind>('vehicle');
  const k = kpis(range);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 40 }} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <View>
            <Text style={styles.eyebrow}>PANEL DE CONTROL</Text>
            <Text style={styles.h1}>ADMINISTRADOR</Text>
          </View>
          <Pressable onPress={logout} style={styles.avatar}>
            <Text style={styles.avatarText}>AD</Text>
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

        {viewMode === 'analytics' ? (
          <>
            <Text style={styles.caption}>Consumo de combustible · Obra Ruta 33 Sur</Text>

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
          <Text style={styles.rangeLabel}>{rangeLabel(range)}</Text>
        </View>

        <View style={styles.kpiGrid}>
          <Kpi label="Costo total" value={k.total} valueColor={colors.primary} delta="↑ 8,4%" deltaColor={colors.green} accent />
          <Kpi label="Litros cargados" value={k.liters} delta={`en ${k.loads} cargas`} deltaColor={colors.textFaint} />
          <Kpi label="Promedio / carga" value={k.avg} delta="↑ 3,1% precio/L" deltaColor={colors.orange} />
          <Kpi label="Tickets pendientes" value="7" valueColor={colors.orange} delta={`de ${k.loads} cargas`} deltaColor={colors.textFaint} />
        </View>

        <View style={styles.chartCard}>
          <View style={styles.chartHead}>
            <View style={{ flex: 1 }}>
              <Text style={styles.chartTitle}>{CHART_TITLES[chart].title}</Text>
              <Text style={styles.chartSub}>{CHART_TITLES[chart].sub}</Text>
            </View>
          </View>

          <View style={styles.chartTabs}>
            {CHARTS.map((c) => (
              <Pressable key={c.key} style={[styles.chartTab, chart === c.key && styles.chartTabActive]} onPress={() => setChart(c.key)}>
                <Text style={[styles.chartTabText, chart === c.key && styles.chartTabTextActive]}>{c.label}</Text>
              </Pressable>
            ))}
          </View>

          {chart === 'vehicle' && <BarChart data={vehicleData(range)} />}
          {chart === 'provider' && <BarChart data={providerData(range)} />}
          {chart === 'user' && <BarChart data={userData(range)} />}
        </View>
          </>
        ) : (
          <VehiclesABM />
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function VehiclesABM() {
  const [machinery, setMachinery] = useState(MACHINERY);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<Partial<Machine>>({});

  const handleAdd = () => {
    const newMachine: Machine = {
      id: 'm' + Date.now(),
      icon: IconPickup,
      name: 'Nuevo Vehículo',
      operator: 'Sin asignar',
      lastRefuel: '-',
      consumption: '0 L/100km',
    };
    const newList = [newMachine, ...machinery];
    setMachinery(newList);
    updateMachinery(newList);
    setEditingId(newMachine.id);
    setEditForm(newMachine);
  };

  const handleEdit = (m: Machine) => {
    setEditingId(m.id);
    setEditForm(m);
  };

  const handleSave = () => {
    const newList = machinery.map(m => m.id === editingId ? { ...m, ...editForm } as Machine : m);
    setMachinery(newList);
    updateMachinery(newList);
    setEditingId(null);
  };

  const handleDelete = (id: string) => {
    const newList = machinery.filter(m => m.id !== id);
    setMachinery(newList);
    updateMachinery(newList);
  };

  return (
    <View style={{ flex: 1, marginTop: 4 }}>
      <Pressable style={styles.addBtn} onPress={handleAdd}>
        <Text style={styles.addBtnText}>+ NUEVO VEHÍCULO</Text>
      </Pressable>
      
      {machinery.map(m => {
        const isEditing = editingId === m.id;
        return (
          <View key={m.id} style={styles.abmCard}>
            {isEditing ? (
              <View>
                <Text style={styles.fieldHint}>Nombre / Patente</Text>
                <TextInput style={styles.abmInput} value={editForm.name} onChangeText={t => setEditForm({...editForm, name: t})} />
                
                <Text style={styles.fieldHint}>Operario Asignado</Text>
                <TextInput style={styles.abmInput} value={editForm.operator} onChangeText={t => setEditForm({...editForm, operator: t})} />
                
                <Text style={styles.fieldHint}>Consumo Promedio</Text>
                <TextInput style={styles.abmInput} value={editForm.consumption} onChangeText={t => setEditForm({...editForm, consumption: t})} />
                
                <View style={styles.abmActions}>
                  <Pressable style={[styles.abmBtn, { backgroundColor: colors.greenBg }]} onPress={handleSave}>
                    <Text style={[styles.abmBtnText, { color: colors.greenText }]}>Guardar</Text>
                  </Pressable>
                  <Pressable style={[styles.abmBtn, { backgroundColor: '#1b1d20' }]} onPress={() => setEditingId(null)}>
                    <Text style={[styles.abmBtnText, { color: colors.text }]}>Cancelar</Text>
                  </Pressable>
                </View>
              </View>
            ) : (
              <View>
                <View style={styles.abmHeader}>
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, flex: 1, paddingRight: 10 }}>
                    <View style={styles.abmIcon}><m.icon width={24} height={24} color={colors.primary} /></View>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.abmName} numberOfLines={1}>{m.name}</Text>
                      <Text style={styles.abmSub}>{m.operator} · {m.consumption}</Text>
                    </View>
                  </View>
                  <View style={{ flexDirection: 'row', gap: 6 }}>
                    <Pressable style={styles.iconBtn} onPress={() => handleEdit(m)}><Text>✏️</Text></Pressable>
                    <Pressable style={styles.iconBtn} onPress={() => handleDelete(m.id)}><Text>🗑️</Text></Pressable>
                  </View>
                </View>
              </View>
            )}
          </View>
        );
      })}
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
  fieldHint: { fontSize: 11, color: colors.textFaint, marginBottom: 6, fontFamily: fonts.sans },
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
  chartTabs: { flexDirection: 'row', backgroundColor: colors.bg, borderWidth: 1, borderColor: colors.borderSoft, borderRadius: 9, padding: 3, gap: 2, marginBottom: 14 },
  chartTab: { flex: 1, paddingVertical: 6, borderRadius: 7, alignItems: 'center' },
  chartTabActive: { backgroundColor: colors.primary },
  chartTabText: { fontSize: 11.5, color: colors.textMuted, fontFamily: fonts.sansSemi },
  chartTabTextActive: { color: colors.bgDeep },

  topTabs: { flexDirection: 'row', backgroundColor: '#1b1d20', borderRadius: 9, padding: 4, marginBottom: 20 },
  topTab: { flex: 1, paddingVertical: 10, borderRadius: 7, alignItems: 'center' },
  topTabActive: { backgroundColor: colors.primary },
  topTabText: { fontSize: 13, color: colors.textMuted, fontFamily: fonts.sansSemi },
  topTabTextActive: { color: colors.bgDeep },

  addBtn: { backgroundColor: colors.primary, padding: 14, borderRadius: 10, alignItems: 'center', marginBottom: 16 },
  addBtnText: { color: colors.bgDeep, fontFamily: fonts.displayBold, letterSpacing: 1 },
  abmCard: { backgroundColor: '#1F2226', borderWidth: 1, borderColor: colors.border, borderRadius: 12, padding: 14, marginBottom: 10 },
  abmInput: { backgroundColor: colors.bg, borderWidth: 1, borderColor: colors.borderSoft, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 8, color: colors.text, fontFamily: fonts.sans, marginBottom: 12 },
  abmActions: { flexDirection: 'row', gap: 10, marginTop: 4 },
  abmBtn: { flex: 1, padding: 10, borderRadius: 8, alignItems: 'center' },
  abmBtnText: { fontFamily: fonts.sansSemi, fontSize: 13 },
  abmHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  abmIcon: { width: 44, height: 44, borderRadius: 10, backgroundColor: colors.primary + '1A', alignItems: 'center', justifyContent: 'center' },
  abmName: { fontFamily: fonts.displayBold, fontSize: 15, color: colors.text },
  abmSub: { fontSize: 11, color: colors.textFaint, marginTop: 2, fontFamily: fonts.sans },
  iconBtn: { width: 34, height: 34, borderRadius: 17, backgroundColor: colors.bg, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.borderSoft },
});
