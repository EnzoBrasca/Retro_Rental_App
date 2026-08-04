import { useCallback, useEffect, useState } from 'react';
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
import { useAuth } from '../../context/AuthContext';
import { useFetch } from '../../hooks/useFetch';
import { getVehiculos, etiquetaLectura } from '../../services/vehiculos';
import { getProveedores, getPrecios } from '../../services/catalogos';
import { analyzeTicket, createTicket } from '../../services/tickets';
import { combustibleLabel, formatMoney } from '../../constants/labels';

type Stage = 'capture' | 'analyzing' | 'form';

export default function EscanearScreen() {
  const router = useRouter();
  const { user } = useAuth();
  // El vehículo llega fijado desde la card de la flota (index.tsx). La pantalla
  // ya no elige vehículo: lo recibe por parámetro y lo muestra bloqueado.
  const params = useLocalSearchParams<{ idVehiculo?: string }>();
  const paramVehiculoId = params.idVehiculo ? Number(params.idVehiculo) : null;
  const [permission, requestPermission] = useCameraPermissions();
  const cameraRef = useRef<CameraView>(null);

  // Arranca en 'form': la carga es manual primero y la foto es OPCIONAL, se
  // adjunta después con un botón ('capture' pasa a ser un overlay bajo demanda).
  const [stage, setStage] = useState<Stage>('form');
  const [fotoUri, setFotoUri] = useState<string | null>(null);
  const [flash, setFlash] = useState(false);

  // Campos del formulario.
  const [idVehiculo, setIdVehiculo] = useState<number | null>(null);
  const [idProveedor, setIdProveedor] = useState<number | null>(null);
  // idPrecio NO es estado: se deriva de (proveedor + combustible del vehículo).
  const [litros, setLitros] = useState('');
  // Lectura del odómetro/horómetro del vehículo. Obligatoria.
  const [usoAcumulado, setUsoAcumulado] = useState('');
  // Precio por litro editable. Arranca vacío y se prellena con el vigente en
  // cuanto se puede resolver; el empleado puede pisarlo si el surtidor cobró
  // otra cosa. Se guarda como texto para no pelear con comas y decimales
  // mientras se tipea.
  const [precioEditado, setPrecioEditado] = useState('');
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

  // Al entrar a la pantalla arrancamos en el FORMULARIO (carga manual) y limpiamos.
  // La foto es opcional: se adjunta después. El vehículo queda fijado al que llegó
  // por parámetro; el resto del formulario se resetea para no arrastrar una carga
  // previa entre entradas.
  useFocusEffect(
    useCallback(() => {
      setStage('form');
      setFotoUri(null);
      setError(null);
      setFechaCarga(null);
      setAnalysisNote(null);
      setIdVehiculo(paramVehiculoId);
      setIdProveedor(null);
      setLitros('');
    }, [paramVehiculoId]),
  );

  // Al capturar/elegir la foto pasamos por la etapa 'analyzing' (overlay) mientras
  // corre el OCR, y al terminar volvemos al formulario. Como ahora el empleado pudo
  // haber tipeado datos ANTES de sacar la foto, el OCR solo autocompleta los campos
  // que están vacíos: nunca pisa lo que el usuario ya cargó a mano (ver runAnalysis).
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

      // Solo autocompletamos lo que el empleado NO cargó a mano: si ya tipeó
      // litros o eligió proveedor, respetamos su dato y no lo pisamos con el OCR.
      if (r.litros != null && litros.trim() === '') setLitros(String(r.litros));
      if (r.idProveedor != null && idProveedor == null) setIdProveedor(r.idProveedor);
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
          ? 'Completamos los campos vacíos con lo leído del ticket (no pisamos lo que ya cargaste). Revisá antes de confirmar.'
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

  // El precio vigente prellena el campo apenas se resuelve, y se vuelve a
  // prellenar si cambia el proveedor. No se pisa lo que el empleado ya tipeó
  // dentro de la misma selección: para eso se compara contra el último vigente
  // aplicado, no contra el valor actual del input.
  const ultimoPrecioAplicado = useRef<number | null>(null);
  useEffect(() => {
    if (precioSel && ultimoPrecioAplicado.current !== precioSel.precioUnitario) {
      ultimoPrecioAplicado.current = precioSel.precioUnitario;
      setPrecioEditado(String(precioSel.precioUnitario));
    }
    if (!precioSel) {
      ultimoPrecioAplicado.current = null;
      setPrecioEditado('');
    }
  }, [precioSel]);

  const precioNum = parseFloat(precioEditado.replace(',', '.')) || 0;
  // El total sigue al precio que el empleado ve, no al del catálogo.
  const total = precioNum > 0 ? litrosNum * precioNum : 0;
  // Solo se manda si difiere del vigente: si es igual, que resuelva el backend.
  const precioFueCorregido = precioSel != null && precioNum > 0
    && Math.abs(precioNum - precioSel.precioUnitario) > 0.001;

  const submit = async () => {
    setError(null);
    // La foto NO se valida: es opcional. Se puede confirmar sin comprobante.
    if (!idVehiculo) return setError('Elegí el vehículo.');
    if (!idProveedor) return setError('Elegí el proveedor.');
    if (!precioSel) return setError('No hay un precio cargado para ese proveedor y combustible.');
    if (litrosNum <= 0) return setError('Ingresá los litros cargados.');
    if (precioNum <= 0) return setError('Ingresá el precio por litro.');
    const usoNum = parseInt(usoAcumulado, 10);
    if (!(usoNum >= 0)) {
      return setError(`Ingresá ${etiquetaLectura(vehiculoSel?.tipoVehiculo ?? null)}.`);
    }

    try {
      setSubmitting(true);
      await createTicket(
        {
          litros: litrosNum,
          idPrecio: precioSel.id,
          idProveedor,
          idVehiculo,
          usoAcumulado: usoNum,
          precioUnitario: precioFueCorregido ? precioNum : undefined,
          fechaCarga: fechaCarga ?? undefined,
        },
        fotoUri,
      );
      Alert.alert('Carga registrada', 'El ticket se guardó correctamente.');
      if (user?.rol === 'ADMINISTRADOR') {
        router.replace('/(administrador)');
      } else {
        router.navigate('/(empleado)/historial');
      }
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
          <Pressable onPress={() => setStage('form')} hitSlop={10} style={{ marginTop: 6 }}>
            <Text style={styles.permSkip}>Seguir sin foto</Text>
          </Pressable>
        </SafeAreaView>
      );
    }
    return (
      <View style={styles.cameraWrap}>
        <SafeAreaView style={styles.cameraHeader} edges={['top']}>
          <Text style={styles.cameraTitle}>FOTOGRAFIAR TICKET</Text>
          <Pressable onPress={() => setStage('form')} hitSlop={10}>
            <Text style={styles.cameraClose}>✕</Text>
          </Pressable>
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

              <Text style={styles.label}>Precio por litro</Text>
              {!idProveedor ? (
                <Text style={styles.precioHint}>Elegí el proveedor para ver el precio.</Text>
              ) : precioSel ? (
                <>
                  <TextInput
                    style={styles.input}
                    value={precioEditado}
                    onChangeText={setPrecioEditado}
                    keyboardType="numeric"
                    placeholder="0"
                    placeholderTextColor={colors.textDim}
                  />
                  {/* Se avisa cuando el valor difiere del catálogo, para que una
                      corrección sea siempre deliberada y no un error de tipeo. */}
                  {precioFueCorregido ? (
                    <Text style={styles.precioHint}>
                      Corregís el precio de {combustibleLabel[vehiculoSel.tipoCombustible]}:{' '}
                      {formatMoney(precioSel.precioUnitario)} → {formatMoney(precioNum)} / L
                    </Text>
                  ) : (
                    <Text style={styles.precioHint}>
                      Precio actual de {combustibleLabel[vehiculoSel.tipoCombustible]}. Cambialo si el
                      surtidor cobró otro valor.
                    </Text>
                  )}
                </>
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

              {/* La etiqueta sigue al vehículo: una máquina vial marca horas de
                  horómetro, un camión kilómetros de odómetro. */}
              <Text style={styles.label}>{etiquetaLectura(vehiculoSel.tipoVehiculo)}</Text>
              <TextInput
                style={styles.input}
                value={usoAcumulado}
                onChangeText={setUsoAcumulado}
                keyboardType="number-pad"
                placeholder="0"
                placeholderTextColor={colors.textDim}
              />
              <Text style={styles.precioHint}>
                Última lectura registrada: {vehiculoSel.usoAcumulado}
                {vehiculoSel.unidadUso === 'HORAS' ? ' h' : ' km'}
              </Text>

              <View style={styles.totalBox}>
                <Text style={styles.totalLabel}>TOTAL ESTIMADO</Text>
                <Text style={styles.totalValue}>{formatMoney(total)}</Text>
              </View>

              <Text style={styles.label}>Foto del ticket</Text>
              {fotoUri ? (
                <View style={styles.photoRow}>
                  <Image source={{ uri: fotoUri }} style={styles.thumb} />
                  <View style={{ gap: 8 }}>
                    <Pressable onPress={() => setStage('capture')} hitSlop={6}>
                      <Text style={styles.changePhoto}>Cambiar foto</Text>
                    </Pressable>
                    <Pressable onPress={() => setFotoUri(null)} hitSlop={6}>
                      <Text style={styles.removePhoto}>Quitar foto</Text>
                    </Pressable>
                  </View>
                </View>
              ) : (
                <Pressable style={styles.attachBtn} onPress={() => setStage('capture')}>
                  <Text style={styles.attachIcon}>📷</Text>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.attachTitle}>Adjuntar foto del ticket</Text>
                    <Text style={styles.attachSub}>Opcional · podés registrar la carga sin foto</Text>
                  </View>
                </Pressable>
              )}

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
  permSkip: { color: colors.textFaint, fontFamily: fonts.sans, fontSize: 13, textAlign: 'center', textDecorationLine: 'underline' },

  cameraWrap: { flex: 1, backgroundColor: colors.bgBlack },
  cameraHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 16, paddingVertical: 12 },
  cameraTitle: { fontFamily: fonts.display, fontSize: 16, letterSpacing: 1, color: colors.text },
  cameraClose: { color: colors.text, fontSize: 20, paddingHorizontal: 4 },
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
  removePhoto: { color: colors.danger, fontFamily: fonts.sansSemi, fontSize: 13 },
  attachBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: colors.surfaceInput,
    borderWidth: 1,
    borderColor: colors.borderInput,
    borderStyle: 'dashed',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 14,
  },
  attachIcon: { fontSize: 22 },
  attachTitle: { color: colors.text, fontFamily: fonts.sansSemi, fontSize: 14 },
  attachSub: { color: colors.textFaint, fontFamily: fonts.sans, fontSize: 11.5, marginTop: 2 },
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
