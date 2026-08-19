import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Switch, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Constants from 'expo-constants';
import { colors, fonts } from '../../constants/theme';
import { PROFILE_FIELDS } from '../../data/mock';
import { useAuth } from '../../context/AuthContext';

import IconNotif from '../../assets/icons/002-notificacin.svg';

/**
 * Vista de Perfil compartida por el área de empleado (como tab) y por el
 * administrador (abierta desde el avatar del dashboard).
 *
 * Ya es role-aware: el subtítulo muestra "Administrador" u "Operario" según
 * `user.rol`. Cuando se le pasa `onBack`, dibuja un botón "volver" arriba
 * (útil en el flujo del admin, donde no es una tab).
 */
export function ProfileView({ onBack }: { onBack?: () => void }) {
  const { user, logout } = useAuth();

  const fullName = user ? `${user.nombre} ${user.apellido}` : 'Juan Pérez';
  const initials = user ? `${user.nombre[0] ?? ''}${user.apellido[0] ?? ''}`.toUpperCase() : 'JP';
  const roleLabel = user?.rol === 'ADMINISTRADOR' ? 'Administrador' : 'Operario';

  // Edición local (mock): sin persistencia todavía. Se enchufará a un PUT del
  // backend en la fase de cableado.
  // Índices de PROFILE_FIELDS: 0 = Nombre completo, 1 = Usuario, 2 = Teléfono.
  // Los tres se pisan con los datos reales del usuario logueado.
  const [fields, setFields] = useState(() =>
    PROFILE_FIELDS.map((f, i) => {
      if (!user) return f;
      if (i === 0) return { ...f, value: fullName };
      if (i === 1) return { ...f, value: user.username };
      if (i === 2) return { ...f, value: user.telefono ?? 'No cargado' };
      return f;
    }),
  );

  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [notifsEnabled, setNotifsEnabled] = useState(true);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <ScrollView
        contentContainerStyle={{ padding: 16, paddingBottom: 24 }}
        showsVerticalScrollIndicator={false}
      >
        {onBack && (
          <Pressable style={styles.back} onPress={onBack} hitSlop={10}>
            <Text style={styles.backText}>‹ Volver</Text>
          </Pressable>
        )}

        <Text style={styles.h1}>PERFIL</Text>

        <View style={styles.hero}>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>{initials}</Text>
          </View>
          <Text style={styles.name}>{fullName}</Text>
          <Text style={styles.role}>{roleLabel} · Obra Ruta 33 Sur</Text>
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
                      newFields[i] = { ...newFields[i], value: v };
                      setFields(newFields);
                    }}
                    onBlur={() => setEditingIndex(null)}
                  />
                ) : (
                  <Text style={styles.rowValue}>{f.value}</Text>
                )}
              </View>
              <Pressable
                onPress={() => setEditingIndex(editingIndex === i ? null : i)}
                hitSlop={10}
              >
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
        {/* La versión sale de app.json y no de una constante escrita a mano:
            un número de versión desactualizado en el pie es peor que no
            mostrarlo, porque hace perder tiempo diagnosticando la app
            equivocada. */}
        <Text style={styles.version}>RetroRental v{Constants.expoConfig?.version ?? '—'}</Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  back: { marginBottom: 12, alignSelf: 'flex-start' },
  backText: { color: colors.primary, fontSize: 15, fontFamily: fonts.sansSemi },
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
  verified: {
    marginTop: 12,
    backgroundColor: colors.greenBg,
    paddingVertical: 5,
    paddingHorizontal: 12,
    borderRadius: 20,
  },
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
  version: {
    textAlign: 'center',
    fontSize: 11,
    color: '#4d525a',
    marginTop: 16,
    fontFamily: fonts.sans,
  },
});
