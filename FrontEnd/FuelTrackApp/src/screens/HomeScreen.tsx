import React, { memo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { colors, fonts, radius } from '../theme';
import { MACHINERY, Machine, updateMachinery } from '../data/mock';
import { Badge } from '../components/ui';
import { useTutorial } from '../navigation/EmployeeArea';
import { useAuth } from '../context/AuthContext';

import IconForeman from '../assets/icons/008-capataz.svg';

const MachineRow = memo(({ item, onAssign }: { item: Machine; onAssign?: () => void }) => (
  <View style={styles.machineRow}>
    <View style={styles.machineIcon}>
      <item.icon width={24} height={24} color={colors.primary} />
    </View>
    <View style={{ flex: 1, minWidth: 0 }}>
      <Text style={styles.machineName} numberOfLines={1}>
        {item.name}
      </Text>
      <Text style={styles.machineSub}>
        {item.operator} · {item.lastRefuel}
      </Text>
    </View>
    {onAssign ? (
      <Pressable style={styles.assignBtn} onPress={onAssign}>
        <Text style={styles.assignBtnText}>Seleccionar</Text>
      </Pressable>
    ) : (
      <View style={{ alignItems: 'flex-end' }}>
        <Text style={[styles.machineLevel, { color: colors.text, fontSize: 13 }]}>{item.consumption}</Text>
        <Text style={styles.machineLevelCaption}>CONSUMO</Text>
      </View>
    )}
  </View>
));

function AssignedMachine({ item, userName }: { item: Machine; userName: string }) {
  return (
    <View style={styles.assignedCard}>
      <View style={styles.hazard} />
      <View style={{ padding: 16, paddingTop: 18 }}>
        <View style={styles.assignedHead}>
          <Text style={styles.assignedTag}>★ VEHÍCULO ASIGNADO</Text>
          <Badge label="OPERATIVO" bg={colors.greenBg} color={colors.greenText} />
        </View>
        <View style={styles.assignedBody}>
          <View style={styles.assignedIcon}>
            <item.icon width={30} height={30} color={colors.primary} />
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.assignedName}>{item.name}</Text>
            <Text style={styles.assignedPlate}>Consumo: {item.consumption}</Text>
            <Text style={styles.assignedOp}>
              Operario: <Text style={{ color: colors.text }}>{userName}</Text>
            </Text>
          </View>
        </View>
        <View style={{ marginTop: 15 }}>
          <View style={styles.levelHead}>
            <Text style={styles.levelHint}>Consumo promedio</Text>
            <Text style={[styles.levelPct, { color: colors.text, fontSize: 14 }]}>{item.consumption}</Text>
          </View>
          <Text style={styles.lastLoad}>Última carga: {item.lastRefuel}</Text>
        </View>
      </View>
    </View>
  );
}

function Stat({ value, label, color }: { value: string; label: string; color: string }) {
  return (
    <View style={styles.statCard}>
      <Text style={[styles.statValue, { color }]}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

export function HomeScreen() {
  const { open } = useTutorial();
  const { user } = useAuth();
  
  const [machinery, setMachinery] = useState(MACHINERY);
  
  const userName = user?.name || 'Juan Pérez';
  const myMachines = machinery.filter(m => m.operator === userName);
  const unassignedMachines = machinery.filter(m => m.operator === 'Sin asignar');

  const handleAssign = (id: string) => {
    const newMachinery = machinery.map(m => m.id === id ? { ...m, operator: userName } : m);
    setMachinery(newMachinery);
    updateMachinery(newMachinery);
  };

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 24 }} showsVerticalScrollIndicator={false}>
        <View style={styles.topRow}>
          <View>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
              <Text style={styles.greeting}>Hola, {userName.split(' ')[0]}</Text>
              <IconForeman width={12} height={12} color={colors.textFaint} />
            </View>
            <Text style={styles.h1}>MI FLOTA</Text>
          </View>
          <Pressable style={styles.helpBtn} onPress={open}>
            <Text style={styles.helpText}>?</Text>
          </Pressable>
        </View>

        <View style={styles.statsRow}>
          <Stat value={String(myMachines.length)} label="Asignados" color={colors.primary} />
          <Stat value={String(myMachines.length)} label="Operativos" color={colors.green} />
          <Stat value="0" label="En taller" color={colors.orange} />
        </View>

        {myMachines.length === 0 ? (
          <Text style={{ color: colors.textFaint, marginBottom: 20, fontFamily: fonts.sans }}>No tenés vehículos asignados.</Text>
        ) : (
          myMachines.map(m => <AssignedMachine key={m.id} item={m} userName={userName} />)
        )}

        <View style={[styles.sectionHead, { marginTop: 10 }]}>
          <Text style={styles.sectionTitle}>VEHÍCULOS DISPONIBLES</Text>
        </View>

        {unassignedMachines.length === 0 ? (
          <Text style={{ color: colors.textFaint, fontFamily: fonts.sans }}>No hay vehículos disponibles sin asignar.</Text>
        ) : (
          unassignedMachines.map(m => <MachineRow key={m.id} item={m} onAssign={() => handleAssign(m.id)} />)
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
  statsRow: { flexDirection: 'row', gap: 9, marginBottom: 18 },
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
  assignedCard: {
    borderRadius: radius.xl,
    overflow: 'hidden',
    backgroundColor: '#22262b',
    borderWidth: 1,
    borderColor: '#33393f',
    marginBottom: 20,
  },
  hazard: { height: 5, backgroundColor: colors.primary },
  assignedHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 },
  assignedTag: { fontSize: 10, letterSpacing: 2, color: colors.primary, fontFamily: fonts.sansSemi },
  assignedBody: { flexDirection: 'row', gap: 14, alignItems: 'center' },
  assignedIcon: {
    width: 64,
    height: 64,
    borderRadius: 13,
    backgroundColor: colors.primary + '26',
    alignItems: 'center',
    justifyContent: 'center',
  },
  assignedName: { fontFamily: fonts.display, fontSize: 19, color: colors.text },
  assignedPlate: { fontFamily: fonts.mono, fontSize: 12, color: colors.textMuted, marginTop: 2 },
  assignedOp: { fontSize: 11.5, color: colors.textFaint, marginTop: 6, fontFamily: fonts.sans },
  levelHead: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 6 },
  levelHint: { color: colors.textFaint, fontSize: 11, fontFamily: fonts.sans },
  levelPct: { fontFamily: fonts.mono, color: colors.primary, fontSize: 11 },
  lastLoad: { fontSize: 11, color: colors.textDim, marginTop: 9, fontFamily: fonts.sans },
  sectionHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
  sectionTitle: { fontFamily: fonts.display, fontSize: 15, letterSpacing: 1, color: '#C9CDD2' },
  machineRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 13,
    backgroundColor: '#1F2226',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 13,
    padding: 12,
    marginBottom: 10,
  },
  machineIcon: {
    width: 50,
    height: 50,
    borderRadius: 11,
    backgroundColor: colors.primary + '1A',
    alignItems: 'center',
    justifyContent: 'center',
  },
  machineName: { fontFamily: fonts.displayBold, fontSize: 15, color: colors.text },
  machineSub: { fontSize: 11, color: colors.textFaint, marginTop: 1, fontFamily: fonts.sans },
  machineLevel: { fontFamily: fonts.mono, fontSize: 15 },
  machineLevelCaption: { fontSize: 9.5, color: colors.textDim, letterSpacing: 0.5, fontFamily: fonts.sans },
  assignBtn: {
    backgroundColor: colors.primary,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
  },
  assignBtnText: {
    color: colors.bgDeep,
    fontSize: 12,
    fontFamily: fonts.sansSemi,
  },
});
