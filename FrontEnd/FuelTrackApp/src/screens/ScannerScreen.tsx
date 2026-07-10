import { useNavigation } from '@react-navigation/native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import * as ImagePicker from 'expo-image-picker';
import React, { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Animated,
  Easing,
  Image,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { colors, fonts, radius } from '../theme';
import { SCAN_DEFAULTS, MACHINERY } from '../data/mock';
import { useAuth } from '../context/AuthContext';

type Stage = 'camera' | 'scanning' | 'results';

const parseNum = (s: string) => parseFloat(String(s).replace(/\./g, '').replace(',', '.')) || 0;
const money = (n: number) => '$' + Math.round(n).toLocaleString('es-AR');

export function ScannerScreen() {
  const navigation = useNavigation<any>();
  const [permission, requestPermission] = useCameraPermissions();
  const cameraRef = useRef<CameraView>(null);

  const [stage, setStage] = useState<Stage>('camera');
  const [photoUri, setPhotoUri] = useState<string | null>(null);
  const [scan, setScan] = useState(SCAN_DEFAULTS);
  const [flash, setFlash] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const { user } = useAuth();

  // Reset to the camera every time the tab regains focus.
  useEffect(() => {
    const unsub = navigation.addListener('focus', () => {
      setStage('camera');
      setPhotoUri(null);
      
      const userName = user?.name || 'Juan Pérez';
      const myMachines = MACHINERY.filter(m => m.operator === userName);
      const sorted = [...myMachines].sort((a, b) => {
        const score = (d: string) => {
          if (d.startsWith('Hoy')) return 100;
          if (d.startsWith('Ayer')) return 50;
          return parseInt(d) || 0;
        };
        return score(a.lastRefuel) - score(b.lastRefuel);
      });
      const oldest = sorted[0];
      
      setScan({ ...SCAN_DEFAULTS, patente: oldest ? oldest.name : SCAN_DEFAULTS.patente });
    });
    return unsub;
  }, [navigation, user]);

  useEffect(() => () => clearTimeout(timer.current), []);

  const capture = async () => {
    try {
      const photo = await cameraRef.current?.takePictureAsync({ quality: 0.6, skipProcessing: true });
      if (photo?.uri) setPhotoUri(photo.uri);
    } catch {
      // Fall through to the scanning stage even if capture fails on a simulator.
    }
    setStage('scanning');
    timer.current = setTimeout(() => setStage('results'), 2200);
  };

  const pickImage = async () => {
    try {
      const result = await ImagePicker.launchImageLibraryAsync({ quality: 0.6 });
      if (!result.canceled && result.assets && result.assets.length > 0) {
        setPhotoUri(result.assets[0].uri);
        setStage('scanning');
        timer.current = setTimeout(() => setStage('results'), 2200);
      }
    } catch {
      // Ignore errors
    }
  };

  const total = money(parseNum(scan.litros) * parseNum(scan.precioL));

  if (stage === 'scanning') return <Scanning photoUri={photoUri} />;
  if (stage === 'results') {
    return (
      <Results
        scan={scan}
        total={total}
        onChange={(patch) => setScan((s) => ({ ...s, ...patch }))}
        onRescan={() => setStage('camera')}
        onConfirm={() => navigation.navigate('Historial')}
      />
    );
  }

  // ---- camera stage ----
  if (!permission) {
    return (
      <SafeAreaView style={styles.center}>
        <ActivityIndicator color={colors.primary} />
      </SafeAreaView>
    );
  }

  if (!permission.granted) {
    return (
      <SafeAreaView style={styles.center}>
        <Text style={{ fontSize: 40, marginBottom: 14 }}>📷</Text>
        <Text style={styles.permTitle}>Necesitamos la cámara</Text>
        <Text style={styles.permText}>Para escanear el ticket de carga hay que permitir el acceso a la cámara.</Text>
        <Pressable style={styles.permBtn} onPress={requestPermission}>
          <Text style={styles.permBtnText}>Permitir cámara</Text>
        </Pressable>
      </SafeAreaView>
    );
  }

  return (
    <View style={styles.cameraWrap}>
      <SafeAreaView style={styles.cameraHeader} edges={['top']}>
        <Text style={styles.cameraTitle}>ESCANEAR TICKET</Text>
        <View style={styles.autoPill}>
          <Text style={styles.autoText}>Auto ▾</Text>
        </View>
      </SafeAreaView>

      <View style={styles.viewport}>
        <CameraView ref={cameraRef} style={StyleSheet.absoluteFill} facing="back" enableTorch={flash} />
        <Corner style={{ top: 26, left: 26 }} tl />
        <Corner style={{ top: 26, right: 26 }} tr />
        <Corner style={{ bottom: 26, left: 26 }} bl />
        <Corner style={{ bottom: 26, right: 26 }} br />
        <Text style={styles.hint}>Alineá el ticket dentro del recuadro</Text>
      </View>

      <SafeAreaView style={styles.controls} edges={['bottom']}>
        <Pressable style={styles.sideBtn} onPress={pickImage}>
          <Text style={{ fontSize: 18 }}>🖼️</Text>
        </Pressable>
        <Pressable style={styles.shutter} onPress={capture} />
        <Pressable style={[styles.sideBtn, flash && { backgroundColor: colors.primary }]} onPress={() => setFlash(!flash)}>
          <Text style={{ fontSize: 18 }}>⚡</Text>
        </Pressable>
      </SafeAreaView>
    </View>
  );
}

function Corner({ style, tl, tr, bl, br }: { style: object; tl?: boolean; tr?: boolean; bl?: boolean; br?: boolean }) {
  return (
    <View
      style={[
        styles.corner,
        style,
        tl && { borderTopWidth: 3, borderLeftWidth: 3, borderTopLeftRadius: 4 },
        tr && { borderTopWidth: 3, borderRightWidth: 3, borderTopRightRadius: 4 },
        bl && { borderBottomWidth: 3, borderLeftWidth: 3, borderBottomLeftRadius: 4 },
        br && { borderBottomWidth: 3, borderRightWidth: 3, borderBottomRightRadius: 4 },
      ]}
    />
  );
}

function Scanning({ photoUri }: { photoUri: string | null }) {
  const y = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(y, { toValue: 1, duration: 800, easing: Easing.inOut(Easing.ease), useNativeDriver: true }),
        Animated.timing(y, { toValue: 0, duration: 800, easing: Easing.inOut(Easing.ease), useNativeDriver: true }),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [y]);

  const translateY = y.interpolate({ inputRange: [0, 1], outputRange: [12, 126] });

  return (
    <View style={styles.scanWrap}>
      <View style={styles.scanBox}>
        {photoUri ? (
          <Image source={{ uri: photoUri }} style={StyleSheet.absoluteFill} resizeMode="cover" />
        ) : (
          <Text style={{ fontSize: 44, opacity: 0.5 }}>🧾</Text>
        )}
        <Animated.View style={[styles.scanLine, { transform: [{ translateY }] }]} />
      </View>
      <Text style={styles.scanTitle}>ANALIZANDO TICKET…</Text>
      <Text style={styles.scanSub}>Extrayendo proveedor, litros y monto</Text>
      <ActivityIndicator color={colors.primary} style={{ marginTop: 18 }} />
    </View>
  );
}

function Results({
  scan,
  total,
  onChange,
  onRescan,
  onConfirm,
}: {
  scan: typeof SCAN_DEFAULTS;
  total: string;
  onChange: (patch: Partial<typeof SCAN_DEFAULTS>) => void;
  onRescan: () => void;
  onConfirm: () => void;
}) {
  const { user } = useAuth();
  const userName = user?.name || 'Juan Pérez';
  const myMachines = MACHINERY.filter((m) => m.operator === userName);
  const [dropdownOpen, setDropdownOpen] = useState(false);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 30 }} keyboardShouldPersistTaps="handled">
          <View style={styles.resHead}>
            <View style={styles.check}>
              <Text style={{ color: colors.greenText, fontSize: 14 }}>✓</Text>
            </View>
            <Text style={styles.resTitle}>RESULTADO DEL ESCANEO</Text>
          </View>
          <Text style={styles.resSub}>Revisá los datos detectados y corregí lo que esté mal antes de confirmar.</Text>

          <ResField label="Proveedor" tag="alta confianza" tagColor={colors.green}>
            <TextInput style={styles.input} value={scan.proveedor} onChangeText={(v) => onChange({ proveedor: v })} />
          </ResField>

          <View style={styles.pair}>
            <ResField label="Litros" flex>
              <TextInput style={[styles.input, styles.mono]} value={scan.litros} onChangeText={(v) => onChange({ litros: v })} keyboardType="numeric" />
            </ResField>
            <ResField label="Precio / L" flex>
              <TextInput style={[styles.input, styles.mono]} value={scan.precioL} onChangeText={(v) => onChange({ precioL: v })} keyboardType="numeric" />
            </ResField>
          </View>

          <ResField label="Vehículo Asignado" tag="⚠ revisar" tagColor={colors.orange}>
            <Pressable onPress={() => setDropdownOpen(!dropdownOpen)}>
              <View pointerEvents="none">
                <TextInput style={[styles.input, styles.warn, { paddingRight: 40 }]} value={scan.patente} />
              </View>
              <Text style={{ position: 'absolute', right: 15, top: 13, color: colors.orange, fontSize: 16 }}>▾</Text>
            </Pressable>
            {dropdownOpen && (
              <View style={styles.dropdown}>
                {myMachines.map((m, idx) => (
                  <Pressable
                    key={m.id}
                    style={[styles.dropdownItem, idx === myMachines.length - 1 && { borderBottomWidth: 0 }]}
                    onPress={() => {
                      onChange({ patente: m.name });
                      setDropdownOpen(false);
                    }}
                  >
                    <Text style={{ color: colors.text, fontFamily: fonts.sans, fontSize: 14 }}>{m.name}</Text>
                  </Pressable>
                ))}
                {myMachines.length === 0 && (
                  <Text style={{ padding: 12, color: colors.textFaint, fontFamily: fonts.sans }}>No hay vehículos</Text>
                )}
              </View>
            )}
          </ResField>

          <View style={styles.pair}>
            <ResField label="Fecha" flex>
              <TextInput style={[styles.input, styles.mono]} value={scan.fecha} onChangeText={(v) => onChange({ fecha: v })} />
            </ResField>
            <ResField label="Combustible" flex>
              <TextInput style={styles.input} value={scan.combustible} onChangeText={(v) => onChange({ combustible: v })} />
            </ResField>
          </View>

          <View style={styles.totalBox}>
            <Text style={styles.totalLabel}>TOTAL</Text>
            <Text style={styles.totalValue}>{total}</Text>
          </View>

          <View style={styles.actions}>
            <Pressable style={styles.rescan} onPress={onRescan}>
              <Text style={styles.rescanText}>Reescanear</Text>
            </Pressable>
            <Pressable style={styles.confirm} onPress={onConfirm}>
              <Text style={styles.confirmText}>Confirmar carga</Text>
            </Pressable>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function ResField({
  label,
  tag,
  tagColor,
  flex,
  children,
}: {
  label: string;
  tag?: string;
  tagColor?: string;
  flex?: boolean;
  children: React.ReactNode;
}) {
  return (
    <View style={[{ marginBottom: 12 }, flex && { flex: 1 }]}>
      <View style={styles.fieldHead}>
        <Text style={styles.fieldLabel}>{label}</Text>
        {tag && <Text style={[styles.fieldTag, { color: tagColor }]}>{tag}</Text>}
      </View>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  center: { flex: 1, backgroundColor: colors.bgBlack, alignItems: 'center', justifyContent: 'center', padding: 30 },
  permTitle: { fontFamily: fonts.display, fontSize: 18, color: colors.text, marginBottom: 8 },
  permText: { fontSize: 13, color: colors.textFaint, textAlign: 'center', lineHeight: 20, marginBottom: 22, fontFamily: fonts.sans },
  permBtn: { backgroundColor: colors.primary, borderRadius: radius.md, paddingHorizontal: 24, paddingVertical: 14 },
  permBtnText: { color: colors.bgDeep, fontFamily: fonts.display, fontSize: 15, letterSpacing: 1, textTransform: 'uppercase' },

  cameraWrap: { flex: 1, backgroundColor: colors.bgBlack },
  cameraHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 16, paddingVertical: 12 },
  cameraTitle: { fontFamily: fonts.display, fontSize: 16, letterSpacing: 1, color: colors.text },
  autoPill: { backgroundColor: '#1b1d20', paddingHorizontal: 10, paddingVertical: 5, borderRadius: 20 },
  autoText: { fontSize: 11, color: colors.textFaint, fontFamily: fonts.sans },
  viewport: {
    flex: 1,
    marginHorizontal: 16,
    borderRadius: radius.xl,
    overflow: 'hidden',
    backgroundColor: '#141517',
    borderWidth: 1,
    borderColor: '#2c2f33',
  },
  corner: { position: 'absolute', width: 34, height: 34, borderColor: colors.primary },
  hint: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 14,
    textAlign: 'center',
    fontSize: 11,
    color: '#cfd3d8',
    fontFamily: fonts.sans,
  },
  controls: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-around', paddingVertical: 22, paddingHorizontal: 16 },
  sideBtn: { width: 44, height: 44, borderRadius: 10, backgroundColor: '#1b1d20', alignItems: 'center', justifyContent: 'center' },
  shutter: { width: 74, height: 74, borderRadius: 37, backgroundColor: colors.primary, borderWidth: 5, borderColor: '#2a2c30' },

  scanWrap: { flex: 1, backgroundColor: colors.bgBlack, alignItems: 'center', justifyContent: 'center', padding: 30 },
  scanBox: {
    width: 150,
    height: 150,
    borderWidth: 2,
    borderColor: '#2c2f33',
    borderRadius: radius.xl,
    marginBottom: 26,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  scanLine: { position: 'absolute', left: '8%', right: '8%', height: 2, backgroundColor: colors.primary, shadowColor: colors.primary, shadowRadius: 8, shadowOpacity: 1 },
  scanTitle: { fontFamily: fonts.display, fontSize: 18, letterSpacing: 1, color: colors.text },
  scanSub: { fontSize: 12.5, color: colors.textFaint, marginTop: 8, textAlign: 'center', fontFamily: fonts.sans },

  resHead: { flexDirection: 'row', alignItems: 'center', gap: 9, marginBottom: 6 },
  check: { width: 26, height: 26, borderRadius: 13, backgroundColor: colors.greenBg, alignItems: 'center', justifyContent: 'center' },
  resTitle: { fontFamily: fonts.display, fontSize: 18, color: colors.text },
  resSub: { fontSize: 12, color: colors.textFaint, marginBottom: 18, lineHeight: 18, fontFamily: fonts.sans },
  pair: { flexDirection: 'row', gap: 11 },
  fieldHead: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 6 },
  fieldLabel: { fontSize: 10.5, letterSpacing: 1.2, textTransform: 'uppercase', color: colors.textFaint, fontFamily: fonts.sans },
  fieldTag: { fontSize: 10.5, fontFamily: fonts.sans },
  input: {
    height: 46,
    backgroundColor: colors.surfaceInput,
    borderWidth: 1,
    borderColor: colors.borderInput,
    borderRadius: 10,
    paddingHorizontal: 13,
    color: colors.text,
    fontSize: 15,
    fontFamily: fonts.sans,
  },
  mono: { fontFamily: fonts.mono },
  warn: { backgroundColor: '#241f1a', borderColor: colors.orange },
  totalBox: {
    marginTop: 18,
    backgroundColor: '#1F2226',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 13,
    padding: 15,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  totalLabel: { fontSize: 12, color: colors.textFaint, letterSpacing: 1, fontFamily: fonts.sans },
  totalValue: { fontFamily: fonts.mono, fontSize: 24, color: colors.primary },
  actions: { flexDirection: 'row', gap: 11, marginTop: 18 },
  rescan: {
    flexBasis: '42%',
    height: 50,
    backgroundColor: colors.surfaceInput,
    borderWidth: 1,
    borderColor: colors.borderInput,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rescanText: { color: '#C9CDD2', fontSize: 14, fontFamily: fonts.sans },
  confirm: { flex: 1, height: 50, backgroundColor: colors.primary, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center' },
  confirmText: { fontFamily: fonts.display, fontSize: 15, letterSpacing: 1, textTransform: 'uppercase', color: colors.bgDeep },
  dropdown: {
    backgroundColor: '#1b1d20',
    borderWidth: 1,
    borderColor: '#33393f',
    borderRadius: 10,
    marginTop: 6,
    overflow: 'hidden',
  },
  dropdownItem: {
    padding: 14,
    borderBottomWidth: 1,
    borderBottomColor: '#2c2f33',
  },
});
