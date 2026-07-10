import { BottomTabBarProps, createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import React, { createContext, useContext, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, fonts } from '../theme';
import { HomeScreen } from '../screens/HomeScreen';
import { HistoryScreen } from '../screens/HistoryScreen';
import { ScannerScreen } from '../screens/ScannerScreen';
import { ProfileScreen } from '../screens/ProfileScreen';
import { TUTORIAL_STEPS } from '../data/mock';
import { TutorialOverlay } from '../screens/TutorialOverlay';

const Tab = createBottomTabNavigator();

import IconTruck from '../assets/icons/007-camin-de-carga.svg';
import IconHistory from '../assets/icons/003-historial-mdico.svg';
import IconUser from '../assets/icons/009-usuario.svg';
import IconCamera from '../assets/icons/004-cmara.svg';

const ICONS: Record<string, any> = {
  Flota: IconTruck,
  Historial: IconHistory,
  Escanear: IconCamera,
  Perfil: IconUser,
};

type TutorialContextType = {
  open: () => void;
};

const TutorialContext = createContext<TutorialContextType>({ open: () => {} });

export const useTutorial = () => useContext(TutorialContext);

function CustomTabBar({ state, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();
  return (
    <View style={[styles.bar, { paddingBottom: insets.bottom, height: 64 + insets.bottom }]}>
      <View style={styles.barInner}>
        {state.routes.map((route, index) => {
          const focused = state.index === index;
          const IconComp = ICONS[route.name];
          const onPress = () => {
            const event = navigation.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
            if (!focused && !event.defaultPrevented) navigation.navigate(route.name);
          };

          return (
            <Pressable key={route.key} style={styles.tab} onPress={onPress}>
              <IconComp width={24} height={24} color={focused ? colors.bgDeep : 'rgba(0,0,0,0.5)'} />
              <Text style={[styles.tabLabel, focused && styles.tabLabelActive]}>{route.name}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

export function EmployeeArea() {
  const [showTutorial, setShowTutorial] = useState(false);
  const [step, setStep] = useState(0);

  const handleNext = () => {
    if (step === TUTORIAL_STEPS.length - 1) {
      setShowTutorial(false);
    } else {
      setStep(s => s + 1);
    }
  };

  return (
    <TutorialContext.Provider value={{ open: () => { setStep(0); setShowTutorial(true); } }}>
      <Tab.Navigator
        screenOptions={{ headerShown: false, sceneStyle: { backgroundColor: colors.bg } }}
        tabBar={(props) => <CustomTabBar {...props} />}
      >
        <Tab.Screen name="Flota" component={HomeScreen} />
        <Tab.Screen name="Historial" component={HistoryScreen} />
        <Tab.Screen name="Escanear" component={ScannerScreen} />
        <Tab.Screen name="Perfil" component={ProfileScreen} />
      </Tab.Navigator>

      <TutorialOverlay 
        visible={showTutorial} 
        step={step} 
        onNext={handleNext} 
        onClose={() => setShowTutorial(false)} 
      />
    </TutorialContext.Provider>
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
