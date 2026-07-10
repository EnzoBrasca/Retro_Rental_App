import { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
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
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { CameraView, useCameraPermissions } from 'expo-camera';
import * as ImagePicker from 'expo-image-picker';
import { useRef } from 'react';
import { colors, fonts, radius } from '../../constants/theme';
import { Loading, ErrorState, EmptyState } from '../../components/fuel/ScreenState';
import { OptionChips } from '../../components/fuel/OptionChips';
import { useFetch } from '../../hooks/useFetch';
import { getVehiculos } from '../../services/vehiculos';
import { getProveedores, getPrecios } from '../../services/catalogos';
import { analyzeTicket, createTicket } from '../../services/tickets';
import { combustibleLabel, formatMoney } from '../../constants/labels';

type Stage = 'capture' | 'analyzing' | 'form';

export default function EscanearScreen() {
  const router = useRouter();
  // El vehículo llega fijado desde la card de la flota (index.tsx). La pantalla
  // ya no elige vehículo: lo recibe por parámetro y lo muestra bloqueado.
  const params = useLocalSearchParams<{ idVehiculo?: string }>();
  const paramVehiculoId = params.idVehiculo ? Number(params.idVehiculo) : null;
  const [permission, requestPermission] = useCameraPermissions();
  const cameraRef = useRef<CameraView>(null);

  const [stage, setStage] = useState<Stage>('capture');
  const [fotoUri, setFotoUri] = useState<string | null>(null);
  const [flash, setFlash] = useState(false);

  // Campos del formulario.
  const [idVehiculo, setIdVehiculo] = useState<number | null>(null);
  const [idProveedor, setIdProveedor] = useState<number | null>(null);
  // idPrecio NO es estado: se deriva de (proveedor + combustible del vehículo).
  const [litros, setLitros] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // OCR: fecha real leída del ticket (se envía al crear en vez de "ahora") y
  // aviso de resultado para el empleado una vez en el formulario.
  const [fechaCarga, setFechaCarga] = useState<string | null>(null);
  const [analysisNote, setAnalysisNote] = useState<string | null>(null);

  // Pool compartido: cualquier vehículo activo se puede cargar, así que traemos
  // el catálogo completo (no solo los "míos"). El vehículo viene fijado por
  // parámetro desde la card, y acá se resuelve contra este catálogo.
  const { data, loading, error: loadError, refetch } = useFetch(async () => {
    const [vehiculos, proveedores, precios] = await Promise.all([
      getVehiculos(),
      getProveedores(),
      getPrecios(),
    ]);
    return { vehiculos, proveedores, precios };
  });

  // Al entrar a la pantalla, arrancamos siempre desde la cámara y limpiamos.
  // El vehículo queda fijado al que llegó por parámetro; el resto del formulario
  // se resetea para no arrastrar una carga previa entre entradas.
  useFocusEffect(
    useCallback(() => {
      setStage('capture');
      setFotoUri(null);
      setError(null);
      setFechaCarga(null);
      setAnalysisNote(null);
      setIdVehiculo(paramVehiculoId);
      setIdProveedor(null);
      setLitros('');
    }, [paramVehiculoId]),
  );

  // Al capturar/elegir la foto NO vamos directo al formulario: pasamos por la
  // etapa 'analyzing', que corre el OCR con la pantalla de verificación aún sin
  // montar. Así el formulario recién aparece con los datos ya resueltos y no
  // existe ventana en la que el empleado pueda tocar campos mientras el OCR está
  // en vuelo (elimina el problema de concurrencia de raíz).
  const handlePhoto = (uri: string) => {
    setFotoUri(uri);
    setStage('analyzing');
    runAnalysis(uri);
  };

  // Corre el OCR sobre la foto y pre-carga lo que se pueda, y SOLO al terminar
  // pasa al formulario. NUNCA bloquea el flujo: si falla (OCR no configurado,
  // error de red, ticket ilegible) igual se avanza al formulario para completar
  // a mano (ver el finally).
  const runAnalysis = async (uri: string) => {
    setAnalysisNote(null);
    try {
      const r = await analyzeTicket(uri);

      // El backend puede haber dado de alta un proveedor/precio nuevo a partir
      // del ticket: refrescamos los catálogos para que aparezcan como opción
      // antes de seleccionarlos.
      if (r.idProveedor != null || r.idPrecio != null) {
        await refetch();
      }

      if (r.litros != null) setLitros(String(r.litros));
      if (r.idProveedor != null) setIdProveedor(r.idProveedor);
      // El precio no se setea: se deriva del proveedor + el combustible del
      // vehículo (que ya viene fijado desde la card). El backend ya dejó cargado
      // el precio de esa combinación.
      setFechaCarga(r.fechaCarga);

      // El vehículo viene pre-seleccionado desde la card, así que la discrepancia
      // de combustible se evalúa acá, una vez que el OCR clasificó el ticket (antes
      // se disparaba al elegir el vehículo en el formulario).
      const vSel = data?.vehiculos.find((x) => x.id === idVehiculo);
      if (vSel && r.tipoCombustible != null && r.tipoCombustible !== vSel.tipoCombustible) {
        Alert.alert(
          'Revisá el combustible y el precio',
          `El ticket parece ser de ${combustibleLabel[r.tipoCombustible]}, pero el vehículo ` +
            `carga ${combustibleLabel[vSel.tipoCombustible]}. Verificá que el vehículo y el precio ` +
            'sean correctos antes de confirmar.',
        );
      }

      const prellenado = r.litros != null || r.idProveedor != null || r.idPrecio != null;
      setAnalysisNote(
        prellenado
          ? 'Datos pre-cargados desde el ticket. Revisalos antes de confirmar.'
          : 'No se reconocieron datos del ticket. Completá el formulario a mano.',
      );
    } catch {
      setAnalysisNote('No se pudo analizar el ticket automáticamente. Completá los datos a mano.');
    } finally {
      setStage('form');
    }
  };

  const capture = async () => {
    try {
      const photo = await cameraRef.current?.takePictureAsync({ quality: 0.6, skipProcessing: true });
      if (photo?.uri) {
        handlePhoto(photo.uri);
        return;
      }
    } catch {
      // en simulador la captura puede fallar; ofrecemos la galería.
    }
    Alert.alert('No se pudo tomar la foto', 'Probá elegir una imagen de la galería.');
  };

  const pickImage = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({ quality: 0.6 });
    if (!result.canceled && result.assets?.length) {
      handlePhoto(result.assets[0].uri);
    }
  };

  // El precio se deriva de la intersección proveedor × combustible del vehículo.
  // Se exige idProveedor != null y un vehículo elegido: si no, no se busca (un
  // idProveedor null matchearía por error los precios legacy sin proveedor).
  const vehiculoSel = data?.vehiculos.find((v) => v.id === idVehiculo);
  const precioSel =
    idProveedor != null && vehiculoSel
      ? data?.precios.find(
          (p) => p.idProveedor === idProveedor && p.tipoCombustible === vehiculoSel.tipoCombustible,
        )
      : undefined;
  const litrosNum = parseFloat(litros.replace(',', '.')) || 0;
  const total = precioSel ? litrosNum * precioSel.precioUnitario : 0;

  const submit = async () => {
    setError(null);
    if (!fotoUri) return setError('Falta la foto del ticket.');
    if (!idVehiculo) return setError('Elegí el vehículo.');
    if (!idProveedor) return setError('Elegí el proveedor.');
    if (!precioSel) return setError('No hay un precio cargado para ese proveedor y combustible.');
    if (litrosNum <= 0) return setError('Ingresá los litros cargados.');

    try {
      setSubmitting(true);
      await createTicket(
        { litros: litrosNum, idPrecio: precioSel.id, idProveedor, idVehiculo, fechaCarga: fechaCarga ?? undefined },
        fotoUri,
      );
      Alert.alert('Carga registrada', 'El ticket se guardó correctamente.');
      router.navigate('/(empleado)/historial');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo registrar la carga.');
    } finally {
      setSubmitting(false);
    }
  };

  // ---- etapa cámara ----
  if (stage === 'capture') {
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
          <Text style={styles.permText}>Para fotografiar el ticket de carga hay que permitir el acceso a la cámara.</Text>
          <Pressable style={styles.permBtn} onPress={requestPermission}>
            <Text style={styles.permBtnText}>Permitir cámara</Text>
          </Pressable>
          <Pressable style={[styles.permBtn, styles.permBtnAlt]} onPress={pickImage}>
            <Text style={styles.permBtnAltText}>Elegir de la galería</Text>
          </Pressable>
        </SafeAreaView>
      );
    }
    return (
      <View style={styles.cameraWrap}>
        <SafeAreaView style={styles.cameraHeader} edges={['top']}>
          <Text style={styles.cameraTitle}>FOTOGRAFIAR TICKET</Text>
        </SafeAreaView>
        <View style={styles.viewport}>
          <CameraView ref={cameraRef} style={StyleSheet.absoluteFill} facing="back" enableTorch={flash} />
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

  // ---- etapa análisis (OCR en curso) ----
  // Pantalla puente: el formulario todavía NO existe, así que no hay nada que el
  // empleado pueda tocar mientras el OCR resuelve. Al terminar, runAnalysis pasa
  // a 'form' con los datos ya cargados.
  if (stage === 'analyzing') {
    return (
      <SafeAreaView style={styles.center}>
        {fotoUri && <Image source={{ uri: fotoUri }} style={styles.analyzingPhoto} />}
        <ActivityIndicator color={colors.primary} style={{ marginBottom: 16 }} />
        <Text style={styles.permTitle}>Analizando ticket…</Text>
        <Text style={styles.permText}>Estamos leyendo los datos de la foto para pre-cargar el formulario.</Text>
      </SafeAreaView>
    );
  }

  // ---- etapa formulario ----
  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 30 }} keyboardShouldPersistTaps="handled">
          <Text style={styles.h1}>REGISTRAR CARGA</Text>

          {fotoUri && (
            <View style={styles.photoRow}>
              <Image source={{ uri: fotoUri }} style={styles.thumb} />
              <Pressable onPress={() => setStage('capture')}>
                <Text style={styles.changePhoto}>Cambiar foto</Text>
              </Pressable>
            </View>
          )}

          {analysisNote && (
            <View style={styles.ocrBox}>
              <Text style={styles.ocrText}>{analysisNote}</Text>
            </View>
          )}

          {loading ? (
            <View style={{ height: 200 }}>
              <Loading />
            </View>
          ) : loadError || !data ? (
            <View style={{ height: 160 }}>
              <ErrorState message={loadError ?? 'Error'} onRetry={refetch} />
            </View>
          ) : !vehiculoSel ? (
            <View style={styles.blocked}>
              <EmptyState message="Este vehículo ya no está disponible. Volvé a la flota y elegí otro." />
            </View>
          ) : (
            <>
              <Text style={styles.label}>Vehículo</Text>
              <View style={styles.vehiculoFijo}>
                <Text style={styles.vehiculoFijoName}>{vehiculoSel.patente}</Text>
                <Text style={styles.vehiculoFijoSub}>{combustibleLabel[vehiculoSel.tipoCombustible]}</Text>
              </View>

              <Text style={styles.label}>Proveedor</Text>
              <OptionChips
                options={data.proveedores.map((p) => ({ key: p.id, label: p.nombre }))}
                value={idProveedor}
                onChange={setIdProveedor}
              />

              <Text style={styles.label}>Precio</Text>
              {!idProveedor ? (
                <Text style={styles.precioHint}>Elegí el proveedor para ver el precio.</Text>
              ) : precioSel ? (
                <View style={styles.precioBox}>
                  <Text style={styles.precioProduct}>
                    {vehiculoSel ? combustibleLabel[vehiculoSel.tipoCombustible] : ''}
                  </Text>
                  <Text style={styles.precioValue}>{formatMoney(precioSel.precioUnitario)} / L</Text>
                </View>
              ) : (
                <Text style={styles.precioWarn}>
                  No hay un precio cargado para ese proveedor y combustible. Escaneá un ticket de esa
                  combinación o pedile al administrador que lo cargue.
                </Text>
              )}

              <Text style={styles.label}>Litros</Text>
              <TextInput
                style={styles.input}
                value={litros}
                onChangeText={setLitros}
                keyboardType="numeric"
                placeholder="0"
                placeholderTextColor={colors.textDim}
              />

              <View style={styles.totalBox}>
                <Text style={styles.totalLabel}>TOTAL ESTIMADO</Text>
                <Text style={styles.totalValue}>{formatMoney(total)}</Text>
              </View>

              {error && <Text style={styles.error}>{error}</Text>}

              <Pressable style={[styles.confirm, submitting && styles.confirmDisabled]} onPress={submit} disabled={submitting}>
                {submitting ? (
                  <ActivityIndicator color={colors.bgDeep} />
                ) : (
                  <Text style={styles.confirmText}>Confirmar carga</Text>
                )}
              </Pressable>
            </>
          )}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  center: { flex: 1, backgroundColor: colors.bgBlack, alignItems: 'center', justifyContent: 'center', padding: 30 },
  permTitle: { fontFamily: fonts.display, fontSize: 18, color: colors.text, marginBottom: 8 },
  permText: { fontSize: 13, color: colors.textFaint, textAlign: 'center', lineHeight: 20, marginBottom: 22, fontFamily: fonts.sans },
  permBtn: { backgroundColor: colors.primary, borderRadius: radius.md, paddingHorizontal: 24, paddingVertical: 14, marginBottom: 10 },
  permBtnText: { color: colors.bgDeep, fontFamily: fonts.display, fontSize: 15, letterSpacing: 1, textTransform: 'uppercase' },
  permBtnAlt: { backgroundColor: colors.surfaceInput, borderWidth: 1, borderColor: colors.borderInput },
  permBtnAltText: { color: colors.text, fontFamily: fonts.sansSemi, fontSize: 14, textAlign: 'center' },

  cameraWrap: { flex: 1, backgroundColor: colors.bgBlack },
  cameraHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 16, paddingVertical: 12 },
  cameraTitle: { fontFamily: fonts.display, fontSize: 16, letterSpacing: 1, color: colors.text },
  viewport: {
    flex: 1,
    marginHorizontal: 16,
    borderRadius: radius.xl,
    overflow: 'hidden',
    backgroundColor: '#141517',
    borderWidth: 1,
    borderColor: '#2c2f33',
  },
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

  h1: { fontFamily: fonts.displayBold, fontSize: 24, color: colors.text, marginBottom: 16 },
  photoRow: { flexDirection: 'row', alignItems: 'center', gap: 14, marginBottom: 18 },
  thumb: { width: 64, height: 64, borderRadius: 10, backgroundColor: colors.surfaceInput },
  changePhoto: { color: colors.primary, fontFamily: fonts.sansSemi, fontSize: 13 },
  ocrBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: colors.surfaceInput,
    borderWidth: 1,
    borderColor: colors.borderInput,
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginBottom: 4,
  },
  ocrText: { flex: 1, color: colors.textFaint, fontSize: 13, fontFamily: fonts.sans, lineHeight: 18 },
  analyzingPhoto: { width: 120, height: 120, borderRadius: radius.md, marginBottom: 22, backgroundColor: colors.surfaceInput },
  precioHint: { color: colors.textDim, fontSize: 13, fontFamily: fonts.sans, paddingVertical: 4 },
  precioBox: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: colors.surfaceInput,
    borderWidth: 1,
    borderColor: colors.borderInput,
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 13,
  },
  precioProduct: { color: colors.textFaint, fontSize: 13, fontFamily: fonts.sans },
  precioValue: { color: colors.text, fontSize: 16, fontFamily: fonts.mono },
  precioWarn: {
    color: colors.danger,
    fontSize: 13,
    fontFamily: fonts.sans,
    lineHeight: 18,
    backgroundColor: colors.surfaceInput,
    borderWidth: 1,
    borderColor: colors.danger,
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  vehiculoFijo: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: colors.surfaceInput,
    borderWidth: 1,
    borderColor: colors.borderInput,
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 13,
  },
  vehiculoFijoName: { color: colors.text, fontSize: 15, fontFamily: fonts.displayBold },
  vehiculoFijoSub: { color: colors.textFaint, fontSize: 13, fontFamily: fonts.sans },
  blocked: { paddingVertical: 20 },
  label: {
    fontSize: 11,
    letterSpacing: 1.2,
    textTransform: 'uppercase',
    color: colors.textFaint,
    marginTop: 16,
    marginBottom: 9,
    fontFamily: fonts.sans,
  },
  input: {
    height: 48,
    backgroundColor: colors.surfaceInput,
    borderWidth: 1,
    borderColor: colors.borderInput,
    borderRadius: 10,
    paddingHorizontal: 14,
    color: colors.text,
    fontSize: 16,
    fontFamily: fonts.mono,
  },
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
  error: { color: colors.danger, fontSize: 13, marginTop: 14, fontFamily: fonts.sans },
  confirm: { height: 52, backgroundColor: colors.primary, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center', marginTop: 18 },
  confirmDisabled: { opacity: 0.6 },
  confirmText: { fontFamily: fonts.display, fontSize: 15, letterSpacing: 1, textTransform: 'uppercase', color: colors.bgDeep },
});
