import React, { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Switch, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { colors, fonts } from '../theme';
import { PROFILE_FIELDS } from '../data/mock';
import { useAuth } from '../context/AuthContext';
import { useTutorial } from '../navigation/EmployeeArea';

import IconNotif from '../assets/icons/002-notificacin.svg';

export function ProfileScreen() {
  const { user, logout, updateName } = useAuth();
  const { open } = useTutorial();
  
  const [fields, setFields] = useState(() => {
    const arr = [...PROFILE_FIELDS];
    if (user) {
      arr[0].value = user.name;
      arr[1].value = user.email;
    }
    return arr;
  });
  
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [notifsEnabled, setNotifsEnabled] = useState(true);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 24 }} showsVerticalScrollIndicator={false}>
        <Text style={styles.h1}>PERFIL</Text>

        <View style={styles.hero}>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>
              {user?.name ? user.name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase() : 'JP'}
            </Text>
          </View>
          <Text style={styles.name}>{user?.name || 'Juan Pérez'}</Text>
          <Text style={styles.role}>Operario · Obra Ruta 33 Sur</Text>
          <View style={styles.verified}>
            <Text style={styles.verifiedText}>✓ Cuenta verificada</Text>
          </View>
        </View>

        <Text style={styles.section}>Datos personales</Text>
        <View style={styles.group}>
          {fields.map((f, i) => (
            <View key={f.label} style={[styles.row, i < fields.length - 1 && styles.rowBorder]}>
              <View style={{ flex: 1, paddingRight: 10 }}>
                <Text style={styles.rowLabel}>{f.label}</Text>
                {editingIndex === i ? (
                  <TextInput
                    style={[styles.rowValue, styles.inputEditing]}
                    value={f.value}
                    autoFocus
                    onChangeText={(v) => {
                      const newFields = [...fields];
                      newFields[i].value = v;
                      setFields(newFields);
                    }}
                    onBlur={() => {
                      setEditingIndex(null);
                      if (i === 0) updateName(fields[0].value);
                    }}
                  />
                ) : (
                  <Text style={styles.rowValue}>{f.value}</Text>
                )}
              </View>
              <Pressable onPress={() => {
                if (editingIndex === i) {
                  setEditingIndex(null);
                  if (i === 0) updateName(fields[0].value);
                } else {
                  setEditingIndex(i);
                }
              }} hitSlop={10}>
                <Text style={styles.edit}>{editingIndex === i ? '✓' : '✎'}</Text>
              </Pressable>
            </View>
          ))}
        </View>

        <View style={styles.group}>
          <View style={styles.row}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
              <IconNotif width={16} height={16} color={colors.textDim} />
              <Text style={styles.settingLabel}>Notificaciones</Text>
            </View>
            <Switch
              value={notifsEnabled}
              onValueChange={setNotifsEnabled}
              trackColor={{ false: '#4d525a', true: colors.primary }}
              thumbColor={colors.bgDeep}
            />
          </View>
        </View>

        <Pressable style={styles.logout} onPress={logout}>
          <Text style={styles.logoutText}>Cerrar sesión</Text>
        </Pressable>
        <Text style={styles.version}>FuelTrack v1.0 · Vial Sur S.A.</Text>
      </ScrollView>
    </SafeAreaView>
  );
}



const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  h1: { fontFamily: fonts.displayBold, fontSize: 26, color: colors.text, marginBottom: 16 },
  hero: {
    alignItems: 'center',
    backgroundColor: '#1F2226',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 16,
    paddingVertical: 22,
    paddingHorizontal: 16,
    marginBottom: 18,
  },
  avatar: {
    width: 78,
    height: 78,
    borderRadius: 39,
    backgroundColor: colors.primaryDark,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 12,
  },
  avatarText: { fontFamily: fonts.displayBold, fontSize: 30, color: colors.bgDeep },
  name: { fontFamily: fonts.display, fontSize: 20, color: colors.text },
  role: { fontSize: 12, color: colors.textFaint, marginTop: 2, fontFamily: fonts.sans },
  verified: { marginTop: 12, backgroundColor: colors.greenBg, paddingVertical: 5, paddingHorizontal: 12, borderRadius: 20 },
  verifiedText: { color: colors.greenText, fontSize: 11, fontFamily: fonts.sansSemi },
  section: {
    fontSize: 11,
    letterSpacing: 1.5,
    textTransform: 'uppercase',
    color: colors.textDim,
    marginHorizontal: 4,
    marginBottom: 10,
    fontFamily: fonts.sans,
  },
  group: {
    backgroundColor: '#1F2226',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 14,
    overflow: 'hidden',
    marginBottom: 18,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 14,
    paddingHorizontal: 15,
  },
  rowBorder: { borderBottomWidth: 1, borderBottomColor: colors.divider },
  rowLabel: { fontSize: 10.5, color: colors.textFaint, fontFamily: fonts.sans },
  rowValue: { fontSize: 14, color: colors.text, marginTop: 2, fontFamily: fonts.sans },
  inputEditing: {
    backgroundColor: '#16181B',
    borderWidth: 1,
    borderColor: colors.primary,
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 2,
    marginTop: 2,
    color: colors.text,
  },
  edit: { color: colors.primary, fontSize: 16 },
  settingLabel: { fontSize: 14, color: colors.text, fontFamily: fonts.sans },
  chevron: { color: colors.textDim, fontSize: 18 },
  logout: {
    height: 50,
    backgroundColor: colors.dangerBg,
    borderWidth: 1,
    borderColor: colors.dangerBorder,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  logoutText: { color: colors.danger, fontSize: 14, fontFamily: fonts.sansSemi },
  version: { textAlign: 'center', fontSize: 11, color: '#4d525a', marginTop: 16, fontFamily: fonts.sans },
});
