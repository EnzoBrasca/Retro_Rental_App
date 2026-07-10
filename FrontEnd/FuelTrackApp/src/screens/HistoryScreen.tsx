import React, { memo, useCallback, useState, useMemo } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { colors, fonts } from '../theme';
import { HISTORY, Load } from '../data/mock';
import { Badge } from '../components/ui';

const FILTERS = ['Todos', 'Esta semana', 'Verificados', 'Pendientes'];

const LoadRow = memo(({ item }: { item: Load }) => {
  const verified = item.status === 'Verificado';
  return (
    <View style={styles.card}>
      <View style={styles.cardTop}>
        <View style={styles.left}>
          <View style={styles.icon}>
            <item.icon width={18} height={18} color={colors.primary} />
          </View>
          <View>
            <Text style={styles.vehicle}>{item.vehicle}</Text>
            <Text style={styles.date}>{item.date}</Text>
          </View>
        </View>
        <Badge
          label={item.status}
          bg={verified ? colors.greenBg : colors.amberBg}
          color={verified ? colors.greenText : colors.amberText}
        />
      </View>
      <View style={styles.cardBottom}>
        <Text style={styles.provider}>
          {item.provider} · <Text style={{ fontFamily: fonts.mono }}>{item.liters}</Text>
        </Text>
        <Text style={styles.cost}>{item.cost}</Text>
      </View>
    </View>
  );
});

export function HistoryScreen() {
  const [activeFilter, setActiveFilter] = useState('Todos');

  const filteredHistory = useMemo(() => {
    if (activeFilter === 'Verificados') return HISTORY.filter(h => h.status === 'Verificado');
    if (activeFilter === 'Pendientes') return HISTORY.filter(h => h.status === 'Pendiente');
    if (activeFilter === 'Esta semana') return HISTORY.filter(h => h.date.includes('05 Jul') || h.date.includes('04 Jul'));
    return HISTORY;
  }, [activeFilter]);

  const renderItem = useCallback(({ item }: { item: Load }) => <LoadRow item={item} />, []);

  const header = (
    <View>
      <Text style={styles.h1}>HISTORIAL</Text>
      <Text style={styles.subtitle}>Registro de cargas de toda tu flota</Text>
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

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <FlatList
        data={filteredHistory}
        keyExtractor={(h) => h.id}
        renderItem={renderItem}
        ListHeaderComponent={header}
        contentContainerStyle={{ padding: 16, paddingBottom: 24 }}
        showsVerticalScrollIndicator={false}
      />
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
    padding: 13,
    marginBottom: 10,
  },
  cardTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 9 },
  left: { flexDirection: 'row', alignItems: 'center', gap: 9 },
  icon: {
    width: 36,
    height: 36,
    borderRadius: 9,
    backgroundColor: colors.primary + '1A',
    alignItems: 'center',
    justifyContent: 'center',
  },
  vehicle: { fontFamily: fonts.displayBold, fontSize: 14.5, color: colors.text },
  date: { fontSize: 11, color: colors.textFaint, fontFamily: fonts.mono },
  cardBottom: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderTopWidth: 1,
    borderTopColor: colors.divider,
    paddingTop: 9,
  },
  provider: { fontSize: 11.5, color: colors.textMuted, fontFamily: fonts.sans },
  cost: { fontFamily: fonts.mono, fontSize: 15, color: colors.text },
});
