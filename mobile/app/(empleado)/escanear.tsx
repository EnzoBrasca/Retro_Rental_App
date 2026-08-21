import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  BackHandler,
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
import { colors, fonts, radius } from '../../constants/theme';
import { Loading, ErrorState, EmptyState } from '../../components/fuel/ScreenState';
import { OptionChips } from '../../components/fuel/OptionChips';
import { useAuth } from '../../context/AuthContext';
import { useFetch } from '../../hooks/useFetch';
import {
  getVehiculos,
  etiquetaLectura,
  tituloVehiculo,
  TipoCombustible,
} from '../../services/vehiculos';
import { getHerramientas } from '../../services/herramientas';
import { getProveedores, getPrecios } from '../../services/catalogos';
import { analyzeTicket, createTicket } from '../../services/tickets';
import { comprimirTicket } from '../../services/imagenes';
import { combustibleLabel, formatMoney, parseEntero, parseNumero } from '../../constants/labels';
import { precioMezcla } from '../../constants/mezcla';

// Una herramienta no tiene combustible fijo (a diferencia de un vehículo): se
// elige carga por carga. Quedan afuera GNC, que no aplica a una herramienta
// portátil, y ACEITE, que NO es un combustible: es el insumo con el que se
// prepara la mezcla, y ofrecerlo como carga sería ofrecer llenar la motosierra
// de aceite puro.
const COMBUSTIBLE_HERRAMIENTA_OPTS = (Object.keys(combustibleLabel) as TipoCombustible[])
  .filter((k) => k !== 'GNC' && k !== 'ACEITE')
  .map((k) => ({ key: k, label: combustibleLabel[k] }));

type Stage = 'capture' | 'analyzing' | 'form';

export default function EscanearScreen() {
  const router = useRouter();
  const { user } = useAuth();
  // El vehículo o la herramienta llegan fijados desde la card de la flota
  // (index.tsx). La pantalla ya no elige entre ellos: los recibe por parámetro
  // y los muestra bloqueados. Nunca llegan los dos juntos.
  const params = useLocalSearchParams<{ idVehiculo?: string; idHerramienta?: string }>();
  const paramVehiculoId = params.idVehiculo ? Number(params.idVehiculo) : null;
  const paramHerramientaId = params.idHerramienta ? Number(params.idHerramienta) : null;
  const [permission, requestPermission] = useCameraPermissions();
  const cameraRef = useRef<CameraView>(null);

  // Arranca en 'form': la carga es manual primero y la foto es OPCIONAL, se
  // adjunta después con un botón ('capture' pasa a ser un overlay bajo demanda).
  const [stage, setStage] = useState<Stage>('form');
  const [fotoUri, setFotoUri] = useState<string | null>(null);
  const [flash, setFlash] = useState(false);

  // Campos del formulario. Uno de los dos IDs está seteado, nunca los dos.
  const [idVehiculo, setIdVehiculo] = useState<number | null>(null);
  const [idHerramienta, setIdHerramienta] = useState<number | null>(null);
  const [idProveedor, setIdProveedor] = useState<number | null>(null);
  // idPrecio NO es estado: se deriva de (proveedor + combustible del ítem).
  const [litros, setLitros] = useState('');
  // Lectura del odómetro/horómetro del vehículo. Obligatoria para un vehículo;
  // una herramienta no tiene contador, así que este campo no aplica para ella.
  const [usoAcumulado, setUsoAcumulado] = useState('');
  // Combustible elegido para la carga. Solo se usa con una herramienta: un
  // vehículo ya tiene su tipoCombustible fijo.
  const [tipoCombustibleHerramienta, setTipoCombustibleHerramienta] =
    useState<TipoCombustible | null>(null);
  // Precio por litro editable. Arranca vacío y se prellena con el vigente en
  // cuanto se puede resolver; el empleado puede pisarlo si el surtidor cobró
  // otra cosa. Se guarda como texto para no pelear con comas y decimales
  // mientras se tipea.
  const [precioEditado, setPrecioEditado] = useState('');
  // Precio por litro del ACEITE, solo para una carga de mezcla. Es el dato que
  // el operario SÍ puede leer (está en la botella), a diferencia del precio de
  // la mezcla. Se prellena con el vigente del proveedor si ya está cargado.
  const [precioAceite, setPrecioAceite] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // OCR: fecha real leída del ticket (se envía al crear en vez de "ahora") y
  // aviso de resultado para el empleado una vez en el formulario.
  const [fechaCarga, setFechaCarga] = useState<string | null>(null);
  const [analysisNote, setAnalysisNote] = useState<string | null>(null);

  // Pool compartido: cualquier vehículo o herramienta activos se pueden cargar,
  // así que traemos el catálogo completo (no solo los "míos"). El ítem viene
  // fijado por parámetro desde la card, y acá se resuelve contra este catálogo.
  const {
    data,
    loading,
    error: loadError,
    refetch,
  } = useFetch(async () => {
    const [vehiculos, herramientas, proveedores, precios] = await Promise.all([
      getVehiculos(),
      getHerramientas(),
      getProveedores(),
      getPrecios(),
    ]);
    return { vehiculos, herramientas, proveedores, precios };
  });

  // Al entrar a la pantalla arrancamos en el FORMULARIO (carga manual) y limpiamos.
  // La foto es opcional: se adjunta después. El ítem queda fijado al que llegó
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
      setIdHerramienta(paramHerramientaId);
      setIdProveedor(null);
      setLitros('');
      setTipoCombustibleHerramienta(null);
      // Imprescindible: la pantalla es un Tabs.Screen con href:null, así que
      // queda MONTADA entre entradas. Sin este reset, la lectura del odómetro
      // del vehículo anterior se envía como la del nuevo y el backend calcula
      // el consumo real contra un delta inventado. El precio no necesita
      // limpiarse acá: setIdProveedor(null) deja precioBase en undefined y el
      // efecto de más abajo ya vacía el campo y su ref.
      setUsoAcumulado('');
    }, [paramVehiculoId, paramHerramientaId]),
  );

  // El botón atrás de Android tiene que cerrar la cámara o el análisis, NO la
  // pantalla entera.
  //
  // Cámara y análisis no son rutas: son estado (`stage`). Para el navegador no
  // existen, así que el atrás hacía pop de `escanear` completa y se llevaba
  // todo lo que el operario ya había tipeado — litros, lectura, proveedor. El
  // gesto es reflejo en Android: se abre la cámara, no gusta el encuadre, atrás.
  // Hay una ✕ para volver, pero compite contra quince años de costumbre.
  //
  // VA EN useFocusEffect, NO en useEffect: la pantalla es un Tabs.Screen con
  // href:null y queda MONTADA entre entradas, así que un listener registrado al
  // montar seguiría interceptando el botón atrás desde las otras pestañas.
  useFocusEffect(
    useCallback(() => {
      const sub = BackHandler.addEventListener('hardwareBackPress', () => {
        // En el formulario no se toca: que el sistema haga lo suyo y salga.
        if (stage === 'form') return false;
        setStage('form');
        return true; // consumido
      });
      return () => sub.remove();
    }, [stage]),
  );

  // Al capturar/elegir la foto pasamos por la etapa 'analyzing' (overlay) mientras
  // corre el OCR, y al terminar volvemos al formulario. Como ahora el empleado pudo
  // haber tipeado datos ANTES de sacar la foto, el OCR solo autocompleta los campos
  // que están vacíos: nunca pisa lo que el usuario ya cargó a mano (ver runAnalysis).
  const handlePhoto = async (uri: string) => {
    setStage('analyzing');
    // Se comprime UNA sola vez, acá, para que las dos subidas —el OCR de
    // /tickets/analyze y el alta de /tickets— manden el mismo archivo reducido.
    // Sin esto viajaban 3 a 6 MB por carga sobre la conexión del yacimiento.
    const comprimida = await comprimirTicket(uri);
    setFotoUri(comprimida);
    runAnalysis(comprimida);
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
      // se disparaba al elegir el vehículo en el formulario). Una herramienta no
      // tiene combustible fijo (lo elige el empleado en el formulario), así que
      // este aviso no aplica para ella.
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
      // El quality de la captura solo ajusta la compresion JPEG, NO la
      // resolucion: lo que baja el peso de verdad es el resize de
      // comprimirTicket, que corre despues en handlePhoto.
      const photo = await cameraRef.current?.takePictureAsync({
        quality: 0.6,
        skipProcessing: true,
      });
      if (photo?.uri) {
        void handlePhoto(photo.uri);
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
      void handlePhoto(result.assets[0].uri);
    }
  };

  // Ítem fijado desde la card: vehículo o herramienta, nunca los dos.
  const vehiculoSel = data?.vehiculos.find((v) => v.id === idVehiculo);
  const herramientaSel = data?.herramientas.find((h) => h.id === idHerramienta);
  // El combustible que gobierna el precio: el fijo del vehículo, o el que el
  // empleado eligió para la herramienta (no tiene uno propio).
  const tipoCombustibleSel: TipoCombustible | null = vehiculoSel
    ? vehiculoSel.tipoCombustible
    : tipoCombustibleHerramienta;

  // El precio se deriva de la intersección proveedor × combustible del ítem.
  // Se exige idProveedor != null y un combustible resuelto: si no, no se busca
  // (un idProveedor null matchearía por error los precios legacy sin proveedor).
  const precioSel =
    idProveedor != null && tipoCombustibleSel != null
      ? data?.precios.find(
          (p) => p.idProveedor === idProveedor && p.tipoCombustible === tipoCombustibleSel,
        )
      : undefined;

  // --------------------------------------------------------------- mezcla
  // El precio de la mezcla no se elige: se calcula. Nadie la vende en un
  // surtidor, así que pedirle al operario "el precio por litro de la mezcla"
  // era pedirle un número que no existe. Lo que sí puede leer es el precio del
  // aceite (está en la botella); el de la nafta ya está en el catálogo.
  const esMezcla = idHerramienta != null && tipoCombustibleSel === 'MEZCLA';

  const vigenteDelProveedor = (tipo: TipoCombustible) =>
    idProveedor != null
      ? data?.precios.find((p) => p.idProveedor === idProveedor && p.tipoCombustible === tipo)
      : undefined;

  const precioNaftaProveedor = esMezcla ? vigenteDelProveedor('NAFTA_SUPER') : undefined;
  const precioAceiteVigente = esMezcla ? vigenteDelProveedor('ACEITE') : undefined;

  const aceiteNum = parseNumero(precioAceite) || 0;
  // Relación nafta:aceite de ESTA máquina (50 = 50:1). Sin herramienta no aplica.
  const relacionMezcla = herramientaSel?.relacionMezcla ?? null;

  // El precio que se le muestra al operario ANTES de confirmar. El backend hace
  // la misma cuenta para el que se guarda; `precioMezcla` está aislado y testeado
  // con los mismos casos que MezclaCalculator justamente para que no diverjan.
  const mezclaCalculada = esMezcla
    ? precioMezcla(precioNaftaProveedor?.precioUnitario, aceiteNum, relacionMezcla)
    : null;

  // Base para prellenar/comparar el precio por litro: la mezcla calculada cuando
  // hay con qué calcularla, o el vigente de (proveedor, combustible).
  //
  // Es un NÚMERO y no el objeto Precio a propósito: la mezcla calculada no tiene
  // fila en el catálogo, así que envolverla en un objeto creaba una referencia
  // nueva en cada render y el efecto de prellenado de abajo se disparaba en
  // bucle, pisando lo que el operario estuviera tipeando.
  const precioBaseValor = mezclaCalculada ?? precioSel?.precioUnitario ?? null;

  const litrosNum = parseNumero(litros) || 0;

  // El precio base prellena el campo apenas se resuelve, y se vuelve a
  // prellenar si cambia el proveedor o el combustible. No se pisa lo que el
  // empleado ya tipeó dentro de la misma selección: para eso se compara contra
  // el último valor aplicado, no contra el valor actual del input.
  const ultimoPrecioAplicado = useRef<number | null>(null);
  useEffect(() => {
    if (precioBaseValor != null && ultimoPrecioAplicado.current !== precioBaseValor) {
      ultimoPrecioAplicado.current = precioBaseValor;
      setPrecioEditado(String(precioBaseValor));
    }
    if (precioBaseValor == null) {
      ultimoPrecioAplicado.current = null;
      setPrecioEditado('');
    }
  }, [precioBaseValor]);

  // El aceite del proveedor prellena su campo, con el mismo criterio que el
  // precio: no se pisa lo que el empleado ya tipeó dentro de la misma selección.
  const ultimoAceiteAplicado = useRef<number | null>(null);
  useEffect(() => {
    const vigente = precioAceiteVigente?.precioUnitario ?? null;
    if (vigente != null && ultimoAceiteAplicado.current !== vigente) {
      ultimoAceiteAplicado.current = vigente;
      setPrecioAceite(String(vigente));
    }
    if (vigente == null) {
      ultimoAceiteAplicado.current = null;
      setPrecioAceite('');
    }
  }, [precioAceiteVigente]);

  // Alta: hay proveedor y combustible resueltos pero el catálogo no tiene un
  // precio para esa combinación. Pasa sobre todo con un proveedor recién dado de
  // alta desde un ticket, que nace sin precios. Antes esto era un callejón sin
  // salida ("pedile al administrador que lo cargue"); ahora el empleado tipea el
  // precio y esa primera carga lo deja como vigente.
  const esAltaDePrecio =
    idProveedor != null && tipoCombustibleSel != null && precioBaseValor == null;

  const precioNum = parseNumero(precioEditado) || 0;
  // El total sigue al precio que el empleado ve, no al del catálogo.
  const total = precioNum > 0 ? litrosNum * precioNum : 0;
  // Solo se manda si difiere de la base: si es igual, que resuelva el backend.
  const precioFueCorregido =
    precioBaseValor != null && precioNum > 0 && Math.abs(precioNum - precioBaseValor) > 0.001;
  // En un alta no hay base contra la cual comparar: el valor tipeado ES el dato.
  //
  // En una mezcla CALCULADA no se manda nada salvo que el operario haya pisado
  // el resultado: si se mandara el valor calculado, el backend lo tomaría como
  // una corrección manual y guardaría ese número en vez de recalcularlo con los
  // insumos, que es justo lo que este cambio viene a evitar.
  const precioAEnviar =
    mezclaCalculada != null
      ? precioFueCorregido
        ? precioNum
        : undefined
      : esAltaDePrecio || precioFueCorregido
        ? precioNum
        : undefined;

  // El aceite viaja cuando el operario lo cargó o lo cambió: ahí actualiza el
  // catálogo del proveedor y la próxima carga en esa estación ya no lo pide.
  const aceiteAEnviar =
    esMezcla &&
    aceiteNum > 0 &&
    Math.abs(aceiteNum - (precioAceiteVigente?.precioUnitario ?? 0)) > 0.001
      ? aceiteNum
      : undefined;

  const submit = async () => {
    setError(null);
    // La foto NO se valida: es opcional. Se puede confirmar sin comprobante.
    if (!idVehiculo && !idHerramienta) return setError('Elegí el vehículo o la herramienta.');
    if (idHerramienta && !tipoCombustibleHerramienta)
      return setError('Elegí el combustible de esta carga.');
    if (!idProveedor) return setError('Elegí el proveedor.');
    // Ya no se exige precioBase: si el catálogo no tiene precio para esa
    // combinación, el valor tipeado alcanza y lo da de alta (esAltaDePrecio).
    // El único requisito sigue siendo que haya un precio, venga de donde venga.
    if (litrosNum <= 0) return setError('Ingresá los litros cargados.');
    if (precioNum <= 0) return setError('Ingresá el precio por litro.');

    // Una herramienta no tiene contador: la lectura solo se pide y se manda
    // para un vehículo.
    let usoNum: number | undefined;
    if (idVehiculo) {
      usoNum = parseEntero(usoAcumulado);
      if (!(usoNum >= 0)) {
        return setError(`Ingresá ${etiquetaLectura(vehiculoSel?.tipoVehiculo ?? null)}.`);
      }
    }

    try {
      setSubmitting(true);
      await createTicket(
        {
          litros: litrosNum,
          // Vehículo: manda idPrecio (el catálogo ya lo resolvió). Herramienta:
          // manda tipoCombustible en su lugar; el backend resuelve o crea el
          // precio (ver CreateTicketPayload).
          idPrecio: idVehiculo != null ? precioSel?.id : undefined,
          tipoCombustible:
            idHerramienta != null ? (tipoCombustibleHerramienta ?? undefined) : undefined,
          idProveedor,
          idVehiculo: idVehiculo ?? undefined,
          idHerramienta: idHerramienta ?? undefined,
          usoAcumulado: usoNum,
          precioUnitario: precioAEnviar,
          precioAceite: aceiteAEnviar,
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
          <Text style={styles.permText}>
            Para fotografiar el ticket de carga hay que permitir el acceso a la cámara.
          </Text>
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
          <Pressable
            onPress={() => setStage('form')}
            hitSlop={10}
            accessibilityRole="button"
            accessibilityLabel="Cerrar la cámara y volver al formulario"
          >
            <Text style={styles.cameraClose}>✕</Text>
          </Pressable>
        </SafeAreaView>
        <View style={styles.viewport}>
          <CameraView
            ref={cameraRef}
            style={StyleSheet.absoluteFill}
            facing="back"
            enableTorch={flash}
          />
          <Text style={styles.hint}>Alineá el ticket dentro del recuadro</Text>
        </View>
        <SafeAreaView style={styles.controls} edges={['bottom']}>
          <Pressable style={styles.sideBtn} onPress={pickImage}>
            <Text style={{ fontSize: 18 }}>🖼️</Text>
          </Pressable>
          <Pressable style={styles.shutter} onPress={capture} />
          <Pressable
            style={[styles.sideBtn, flash && { backgroundColor: colors.primary }]}
            onPress={() => setFlash(!flash)}
          >
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
        <Text style={styles.permText}>
          Estamos leyendo los datos de la foto para pre-cargar el formulario.
        </Text>
      </SafeAreaView>
    );
  }

  // ---- etapa formulario ----
  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView
          contentContainerStyle={{ padding: 16, paddingBottom: 30 }}
          keyboardShouldPersistTaps="handled"
        >
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
          ) : !vehiculoSel && !herramientaSel ? (
            <View style={styles.blocked}>
              <EmptyState message="Este ítem ya no está disponible. Volvé a la flota y elegí otro." />
            </View>
          ) : (
            <>
              <Text style={styles.label}>{vehiculoSel ? 'Vehículo' : 'Herramienta'}</Text>
              <View style={styles.vehiculoFijo}>
                <Text style={styles.vehiculoFijoName}>
                  {vehiculoSel ? tituloVehiculo(vehiculoSel) : herramientaSel!.nombre}
                </Text>
                <Text style={styles.vehiculoFijoSub}>
                  {vehiculoSel
                    ? combustibleLabel[vehiculoSel.tipoCombustible]
                    : `Capacidad ${herramientaSel!.capacidad} L`}
                </Text>
              </View>

              {/* Una herramienta no tiene combustible fijo (a diferencia de un
                  vehículo): se elige acá, carga por carga. GNC no aplica. */}
              {herramientaSel && (
                <>
                  <Text style={styles.label}>Combustible de esta carga</Text>
                  <OptionChips
                    options={COMBUSTIBLE_HERRAMIENTA_OPTS}
                    value={tipoCombustibleHerramienta}
                    onChange={setTipoCombustibleHerramienta}
                  />
                </>
              )}

              <Text style={styles.label}>Proveedor</Text>
              <OptionChips
                options={data.proveedores.map((p) => ({ key: p.id, label: p.nombre }))}
                value={idProveedor}
                onChange={setIdProveedor}
              />

              {/* La mezcla no se compra hecha: se prepara con nafta y aceite.
                  Por eso acá se pide el precio del aceite -- que el operario
                  puede leer en la botella -- y no el de la mezcla, que no
                  figura en ningún surtidor. */}
              {esMezcla && idProveedor != null && (
                <>
                  <Text style={styles.label}>Precio del aceite por litro</Text>
                  <TextInput
                    style={styles.input}
                    value={precioAceite}
                    onChangeText={setPrecioAceite}
                    keyboardType="numeric"
                    placeholder="0"
                    placeholderTextColor={colors.textDim}
                  />
                  {!precioNaftaProveedor ? (
                    <Text style={styles.precioWarn}>
                      Para calcular la mezcla falta el precio de nafta súper de esta estación.
                      Cargalo con un ticket de nafta, o ingresá abajo el precio de la mezcla a mano.
                    </Text>
                  ) : precioAceiteVigente ? (
                    <Text style={styles.precioHint}>
                      Precio de aceite cargado para esta estación. Cambialo si la botella salió otro
                      valor.
                    </Text>
                  ) : (
                    <Text style={styles.precioWarn}>
                      Esta estación todavía no tiene precio de aceite. Ingresá el de la botella:
                      queda cargado para las próximas cargas.
                    </Text>
                  )}
                </>
              )}

              <Text style={styles.label}>Precio por litro</Text>
              {!idProveedor ? (
                <Text style={styles.precioHint}>Elegí el proveedor para ver el precio.</Text>
              ) : !tipoCombustibleSel ? (
                <Text style={styles.precioHint}>
                  Elegí el combustible de esta carga para ver el precio.
                </Text>
              ) : (
                <>
                  <TextInput
                    style={styles.input}
                    value={precioEditado}
                    onChangeText={setPrecioEditado}
                    keyboardType="numeric"
                    placeholder="0"
                    placeholderTextColor={colors.textDim}
                  />
                  {/* Se avisa cuando el valor difiere de la base, para que una
                      corrección sea siempre deliberada y no un error de tipeo. */}
                  {precioBaseValor != null && precioFueCorregido ? (
                    <Text style={styles.precioHint}>
                      Corregís el precio de {combustibleLabel[tipoCombustibleSel]}:{' '}
                      {formatMoney(precioBaseValor)} → {formatMoney(precioNum)} / L
                    </Text>
                  ) : mezclaCalculada != null ? (
                    /* El resultado del cálculo, con la cuenta a la vista: sin
                       mostrarla, un número que se mueve solo al cambiar el aceite
                       parece un error de la app. Queda editable igual, por si la
                       relación está mal cargada o compraron mezcla ya preparada. */
                    <Text style={styles.precioHint}>
                      Calculado con nafta a {formatMoney(precioNaftaProveedor!.precioUnitario)},
                      aceite a {formatMoney(aceiteNum)} y relación {relacionMezcla}:1. Cambialo solo
                      si pagaste otra cosa.
                    </Text>
                  ) : esAltaDePrecio ? (
                    /* Sin precio en el catálogo el campo arranca vacío (lo deja
                       así el efecto de prellenado) y el empleado lo tipea. Se
                       avisa que queda como referencia para los demás: no es una
                       corrección puntual de esta carga. */
                    <Text style={styles.precioWarn}>
                      Todavía no hay precio de {combustibleLabel[tipoCombustibleSel]} para este
                      proveedor. Ingresá el del surtidor: queda cargado para las próximas cargas.
                    </Text>
                  ) : (
                    <Text style={styles.precioHint}>
                      Precio actual de {combustibleLabel[tipoCombustibleSel]}. Cambialo si el
                      surtidor cobró otro valor.
                    </Text>
                  )}
                </>
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

              {/* Una herramienta no tiene contador: no lleva horómetro ni
                  odómetro, así que este campo solo aparece para un vehículo. */}
              {vehiculoSel && (
                <>
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
                </>
              )}

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
                    <Text style={styles.attachSub}>
                      Opcional · podés registrar la carga sin foto
                    </Text>
                  </View>
                </Pressable>
              )}

              {error && <Text style={styles.error}>{error}</Text>}

              <Pressable
                style={[styles.confirm, submitting && styles.confirmDisabled]}
                onPress={submit}
                disabled={submitting}
              >
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
  center: {
    flex: 1,
    backgroundColor: colors.bgBlack,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 30,
  },
  permTitle: { fontFamily: fonts.display, fontSize: 18, color: colors.text, marginBottom: 8 },
  permText: {
    fontSize: 13,
    color: colors.textFaint,
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 22,
    fontFamily: fonts.sans,
  },
  permBtn: {
    backgroundColor: colors.primary,
    borderRadius: radius.md,
    paddingHorizontal: 24,
    paddingVertical: 14,
    marginBottom: 10,
  },
  permBtnText: {
    color: colors.bgDeep,
    fontFamily: fonts.display,
    fontSize: 15,
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  permBtnAlt: {
    backgroundColor: colors.surfaceInput,
    borderWidth: 1,
    borderColor: colors.borderInput,
  },
  permBtnAltText: {
    color: colors.text,
    fontFamily: fonts.sansSemi,
    fontSize: 14,
    textAlign: 'center',
  },
  permSkip: {
    color: colors.textFaint,
    fontFamily: fonts.sans,
    fontSize: 13,
    textAlign: 'center',
    textDecorationLine: 'underline',
  },

  cameraWrap: { flex: 1, backgroundColor: colors.bgBlack },
  cameraHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  cameraTitle: { fontFamily: fonts.display, fontSize: 16, letterSpacing: 1, color: colors.text },
  cameraClose: { color: colors.text, fontSize: 20, paddingHorizontal: 4 },
  viewport: {
    flex: 1,
    marginHorizontal: 16,
    borderRadius: radius.xl,
    overflow: 'hidden',
    backgroundColor: colors.panel,
    borderWidth: 1,
    borderColor: colors.panelBorder,
  },
  hint: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 14,
    textAlign: 'center',
    fontSize: 11,
    color: colors.textStrong,
    fontFamily: fonts.sans,
  },
  controls: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-around',
    paddingVertical: 22,
    paddingHorizontal: 16,
  },
  sideBtn: {
    width: 44,
    height: 44,
    borderRadius: 10,
    backgroundColor: colors.surfaceMuted,
    alignItems: 'center',
    justifyContent: 'center',
  },
  shutter: {
    width: 74,
    height: 74,
    borderRadius: 37,
    backgroundColor: colors.primary,
    borderWidth: 5,
    borderColor: colors.border,
  },

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
  ocrText: {
    flex: 1,
    color: colors.textFaint,
    fontSize: 13,
    fontFamily: fonts.sans,
    lineHeight: 18,
  },
  analyzingPhoto: {
    width: 120,
    height: 120,
    borderRadius: radius.md,
    marginBottom: 22,
    backgroundColor: colors.surfaceInput,
  },
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
    backgroundColor: colors.surface,
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
  confirm: {
    height: 52,
    backgroundColor: colors.primary,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 18,
  },
  confirmDisabled: { opacity: 0.6 },
  confirmText: {
    fontFamily: fonts.display,
    fontSize: 15,
    letterSpacing: 1,
    textTransform: 'uppercase',
    color: colors.bgDeep,
  },
});
