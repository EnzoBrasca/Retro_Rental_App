import { Tabs } from 'expo-router';
import { BottomTabBarProps } from '@react-navigation/bottom-tabs';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { FC } from 'react';
import { SvgProps } from 'react-native-svg';
import { colors, fonts } from '../../constants/theme';
import { TutorialProvider } from '../../context/TutorialContext';

import IconTruck from '../../assets/icons/007-camin-de-carga.svg';
import IconHistory from '../../assets/icons/003-historial-mdico.svg';
import IconUser from '../../assets/icons/009-usuario.svg';

/**
 * Layout del área de EMPLEADO: barra de tabs inferior custom (amarilla) +
 * proveedor del tutorial. Es el equivalente Expo Router del `EmployeeArea`
 * (Tab.Navigator) del prototipo.
 *
 * En Expo Router el `name` de cada tab es el nombre del archivo:
 *   index → Flota · historial → Historial · perfil → Perfil
 *
 * `escanear` sigue siendo una ruta viva pero YA NO es una tab: se accede tocando
 * la card del vehículo en la flota (index.tsx). Por eso no está en TABS y su
 * Tabs.Screen usa `href: null` para quedar fuera de la barra.
 */
const TABS: Record<string, { label: string; Icon: FC<SvgProps> }> = {
  index: { label: 'Flota', Icon: IconTruck },
  historial: { label: 'Historial', Icon: IconHistory },
  perfil: { label: 'Perfil', Icon: IconUser },
};

function CustomTabBar({ state, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();
  return (
    <View style={[styles.bar, { paddingBottom: insets.bottom, height: 64 + insets.bottom }]}>
      <View style={styles.barInner}>
        {state.routes.map((route, index) => {
          const config = TABS[route.name];
          if (!config) return null;
          const focused = state.index === index;
          const { Icon, label } = config;

          const onPress = () => {
            const event = navigation.emit({
              type: 'tabPress',
              target: route.key,
              canPreventDefault: true,
            });
            if (!focused && !event.defaultPrevented) navigation.navigate(route.name);
          };

          return (
            // Esta es la navegación principal de toda el área de operario. Sin
            // el rol y el estado, para un lector de pantalla no hay pestañas:
            // hay tres cosas tocables sin relación entre sí y sin forma de
            // saber cuál está activa.
            <Pressable
              key={route.key}
              style={styles.tab}
              onPress={onPress}
              accessibilityRole="tab"
              accessibilityState={{ selected: focused }}
              accessibilityLabel={label}
            >
              <Icon width={24} height={24} color={focused ? colors.bgDeep : 'rgba(0,0,0,0.5)'} />
              <Text style={[styles.tabLabel, focused && styles.tabLabelActive]}>{label}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

export default function EmpleadoLayout() {
  return (
    <TutorialProvider>
      <Tabs
        screenOptions={{ headerShown: false, sceneStyle: { backgroundColor: colors.bg } }}
        tabBar={(props) => <CustomTabBar {...props} />}
      >
        <Tabs.Screen name="index" />
        <Tabs.Screen name="historial" />
        <Tabs.Screen name="escanear" options={{ href: null }} />
        <Tabs.Screen name="perfil" />
      </Tabs>
    </TutorialProvider>
  );
}

const styles = StyleSheet.create({
  bar: {
    backgroundColor: colors.primary,
    borderTopWidth: 1,
    borderTopColor: colors.primaryDark,
  },
  barInner: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    height: 64,
    justifyContent: 'space-around',
    paddingHorizontal: 10,
  },
  tab: { flex: 1, alignItems: 'center', gap: 3 },
  tabLabel: { fontSize: 10, color: 'rgba(0,0,0,0.5)', fontFamily: fonts.sans },
  tabLabelActive: { color: colors.bgDeep, fontFamily: fonts.sansSemi },
});
