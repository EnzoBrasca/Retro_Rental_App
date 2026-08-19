// Los tests corren SIEMPRE en horario argentino, sin importar la zona de la
// maquina o del runner de CI.
//
// No es cosmetico: formatDay existe justamente para resolver el off-by-one de
// las fechas date-only, que solo se manifiesta en zonas UTC-. Con la TZ del
// runner en UTC, ese test pasaria incluso con la implementacion rota.
process.env.TZ = 'America/Argentina/Buenos_Aires';

// AsyncStorage es un modulo nativo: sin mock, cualquier test que importe algo de
// services/ falla al cargar (services/api.ts -> session.ts -> AsyncStorage).
// El mock lo publica el propio paquete.
jest.mock('@react-native-async-storage/async-storage', () =>
  require('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);

// Idem SecureStore, donde vive el token. En los tests no hay Keychain/Keystore.
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async () => null),
  setItemAsync: jest.fn(async () => undefined),
  deleteItemAsync: jest.fn(async () => undefined),
  isAvailableAsync: jest.fn(async () => true),
}));
