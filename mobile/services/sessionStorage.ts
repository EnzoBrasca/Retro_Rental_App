import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import { AuthResponse, Rol } from './auth';

/**
 * Persistencia de la sesión, con el secreto separado del resto.
 *
 * Por qué existe este módulo: antes la sesión entera —token incluido— vivía en
 * AsyncStorage bajo la clave 'user'. En Android, AsyncStorage es un archivo
 * SQLite SIN CIFRAR dentro del sandbox de la app
 * (/data/data/<paquete>/databases/RKStorage). El sandbox protege mientras el
 * sistema operativo lo sostenga, y deja de hacerlo en tres casos concretos:
 * dispositivo rooteado, backup de Android (que copia el almacenamiento de la
 * app afuera del teléfono), y análisis del equipo si se pierde, se roba o se
 * devuelve al terminar el contrato.
 *
 * Importa porque son teléfonos de empleados en obra, no equipos administrados
 * por la empresa: no controlamos si están rooteados ni qué pasa con el equipo
 * cuando la persona se va. Y el JWT es un token portador: quien lo tiene, ES el
 * usuario, hasta que venza. Si es el de un administrador, eso incluye /admin.
 *
 * El reparto:
 *   - token   -> SecureStore (Keystore en Android, Keychain en iOS). La clave
 *                que lo cifra vive en hardware y no sale de ahí ni con root.
 *                Tampoco se incluye en los backups del sistema.
 *   - perfil  -> AsyncStorage. Nombre, apellido, username, rol, teléfono y
 *                vencimiento no son secretos: no hace falta pagar el costo de
 *                SecureStore por ellos.
 *
 * Por qué se separa en vez de mover el objeto entero: SecureStore tiene un
 * límite práctico de ~2048 bytes por valor en iOS. Hoy el objeto entra cómodo,
 * pero el día que alguien agregue un campo al AuthResponse la escritura falla
 * EN EL TELÉFONO DEL EMPLEADO, no en la máquina de quien lo programó, y falla
 * justo al guardar la sesión. Separando, el token nunca se acerca a ese límite.
 */

// Token: único dato secreto de la sesión.
const TOKEN_KEY = 'authToken';
// Perfil: el resto del AuthResponse, que no es secreto.
const PROFILE_KEY = 'userProfile';
/**
 * Clave vieja, donde la sesión entera (token incluido) vivía en claro. Se borra
 * al arrancar. NO se migra a propósito: mover un secreto de un almacén inseguro
 * a uno seguro no lo vuelve seguro, porque ya estuvo expuesto. El usuario
 * inicia sesión una vez más y a partir de ahí el token nace protegido.
 */
const LEGACY_KEY = 'user';

export interface Usuario {
  nombre: string;
  apellido: string;
  username: string;
  rol: Rol;
  token: string;
  // Vencimiento del token en epoch millis, tal como lo firmó el backend.
  // Puede faltar en sesiones viejas persistidas antes de este campo → esas se
  // tratan como vencidas, así el usuario vuelve a loguearse una única vez.
  expiresAt?: number;
  // Teléfono formateado que llega en el AuthResponse. Puede faltar en sesiones
  // viejas persistidas antes de este campo → tratar como opcional.
  telefono?: string | null;
}

// Lo que se guarda en AsyncStorage: el Usuario sin el token.
type Perfil = Omit<Usuario, 'token'>;

/**
 * SecureStore no existe en web, y app.json declara un bundler web. Sin este
 * guard, abrir la app en un navegador reventaría al arrancar.
 *
 * En web se cae a AsyncStorage (localStorage), que NO da la garantía de
 * hardware. Es aceptable porque web no es un destino de producción de esta app;
 * si algún día lo fuera, esta decisión hay que rediscutirla.
 */
const usaSecureStore = Platform.OS !== 'web';

async function guardarToken(token: string): Promise<void> {
  if (usaSecureStore) {
    await SecureStore.setItemAsync(TOKEN_KEY, token);
    return;
  }
  await AsyncStorage.setItem(TOKEN_KEY, token);
}

async function leerTokenCrudo(): Promise<string | null> {
  if (usaSecureStore) {
    return SecureStore.getItemAsync(TOKEN_KEY);
  }
  return AsyncStorage.getItem(TOKEN_KEY);
}

async function borrarToken(): Promise<void> {
  if (usaSecureStore) {
    await SecureStore.deleteItemAsync(TOKEN_KEY);
    return;
  }
  await AsyncStorage.removeItem(TOKEN_KEY);
}

/** Persiste la sesión recién iniciada, con el token aparte del perfil. */
export async function saveSession(data: AuthResponse): Promise<void> {
  const { token, ...perfil } = data;
  await guardarToken(token);
  await AsyncStorage.setItem(PROFILE_KEY, JSON.stringify(perfil));
}

/**
 * Rehidrata la sesión. Devuelve null si falta cualquiera de las dos partes: un
 * perfil sin token no sirve para nada (todo request moriría con 401) y un token
 * sin perfil no permite ni siquiera decidir a qué pantalla ir según el rol.
 */
export async function readSession(): Promise<Usuario | null> {
  const [token, perfilCrudo] = await Promise.all([leerTokenCrudo(), AsyncStorage.getItem(PROFILE_KEY)]);

  if (!token || !perfilCrudo) {
    // Estado inconsistente (por ejemplo, una de las dos escrituras falló):
    // se limpia todo para no dejar mitades dando vueltas.
    if (token || perfilCrudo) await clearSession();
    return null;
  }

  try {
    const perfil = JSON.parse(perfilCrudo) as Perfil;
    return { ...perfil, token };
  } catch {
    await clearSession();
    return null;
  }
}

/** Solo el token, para el header Authorization. */
export function readToken(): Promise<string | null> {
  return leerTokenCrudo();
}

/**
 * Cierra la sesión. `lastUsername` NO se toca: vive bajo otra clave a propósito
 * para sobrevivir al logout y prellenar el login la próxima vez.
 */
export async function clearSession(): Promise<void> {
  await Promise.all([borrarToken(), AsyncStorage.removeItem(PROFILE_KEY)]);
}

/**
 * Borra la sesión en formato viejo (token en claro bajo 'user'). Se llama una
 * vez al arrancar la app. Es idempotente y barato: si la clave no existe,
 * removeItem no hace nada.
 */
export function purgeLegacySession(): Promise<void> {
  return AsyncStorage.removeItem(LEGACY_KEY);
}
