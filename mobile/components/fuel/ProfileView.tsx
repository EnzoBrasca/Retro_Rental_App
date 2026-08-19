import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Constants from 'expo-constants';
import { colors, fonts } from '../../constants/theme';
import { useAuth } from '../../context/AuthContext';

/**
 * Vista de Perfil compartida por el área de empleado (como tab) y por el
 * administrador (abierta desde el avatar del dashboard).
 *
 * Es role-aware: el subtítulo muestra "Administrador" u "Operario" según
 * `user.rol`. Cuando se le pasa `onBack`, dibuja un botón "volver" arriba
 * (útil en el flujo del admin, donde no es una tab).
 *
 * ES DE SOLO LECTURA, A PROPÓSITO. Antes cada campo tenía un lápiz que abría un
 * input y "guardaba" el valor: no había ningún request detrás, era `useState`
 * local. El usuario corregía su teléfono, veía el cambio, cerraba la app y el
 * dato seguía viejo en la base — sin que nada se lo dijera. También había un
 * switch de notificaciones sin sistema detrás (`expo-notifications` ni está
 * instalado), un "✓ Cuenta verificada" fijo y una obra inventada.
 *
 * No hay endpoint para que un usuario edite sus propios datos, así que la
 * pantalla muestra lo que hay y no promete más. Cuando exista un `PUT /me`, la
 * edición vuelve con estado de guardado, error y reversión.
 *
 * Los valores se derivan de `user` en cada render, no se copian a estado: una
 * copia con `useState(() => ...)` no se re-sincroniza si `user` cambia después
 * del montaje.
 */
export function ProfileView({ onBack }: { onBack?: () => void }) {
  const { user, logout } = useAuth();

  const fullName = user ? `${user.nombre} ${user.apellido}` : '—';
  const initials = user ? `${user.nombre[0] ?? ''}${user.apellido[0] ?? ''}`.toUpperCase() : '—';
  const roleLabel = user?.rol === 'ADMINISTRADOR' ? 'Administrador' : 'Operario';

  const datos = [
    { label: 'Nombre completo', value: fullName },
    { label: 'Usuario', value: user?.username ?? '—' },
    { label: 'Teléfono', value: user?.telefono ?? 'No cargado' },
  ];

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <ScrollView
        contentContainerStyle={{ padding: 16, paddingBottom: 24 }}
        showsVerticalScrollIndicator={false}
      >
        {onBack && (
          <Pressable
            style={styles.back}
            onPress={onBack}
            hitSlop={10}
            accessibilityRole="button"
            accessibilityLabel="Volver"
          >
            <Text style={styles.backText}>‹ Volver</Text>
          </Pressable>
        )}

        <Text style={styles.h1} accessibilityRole="header">
          PERFIL
        </Text>

        <View style={styles.hero}>
          {/* El avatar son las iniciales: para un lector de pantalla no aporta
              nada que no diga el nombre de abajo. */}
          <View style={styles.avatar} accessibilityElementsHidden importantForAccessibility="no">
            <Text style={styles.avatarText}>{initials}</Text>
          </View>
          <Text style={styles.name}>{fullName}</Text>
          <Text style={styles.role}>{roleLabel}</Text>
        </View>

        <Text style={styles.section} accessibilityRole="header">
          Datos personales
        </Text>
        <View style={styles.group}>
          {datos.map((d, i) => (
            <View
              key={d.label}
              style={[styles.row, i < datos.length - 1 && styles.rowBorder]}
              accessible
              accessibilityLabel={`${d.label}: ${d.value}`}
            >
              <View style={{ flex: 1, paddingRight: 10 }}>
                <Text style={styles.rowLabel}>{d.label}</Text>
                <Text style={styles.rowValue}>{d.value}</Text>
              </View>
            </View>
          ))}
        </View>

        <Pressable
          style={styles.logout}
          onPress={logout}
          accessibilityRole="button"
          accessibilityLabel="Cerrar sesión"
        >
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
    backgroundColor: colors.surface,
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
    backgroundColor: colors.surface,
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
    color: colors.textDim,
    marginTop: 16,
    fontFamily: fonts.sans,
  },
});
