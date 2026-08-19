import { PASSWORD_MIN, documentoInvalido, fechaISOInvalida, passwordInvalida } from './validation';

/**
 * Reglas de validación compartidas (UI-03, UI-04).
 *
 * POR QUE ESTE ARCHIVO. Un empleado se puede crear por dos caminos —el alta del
 * admin y el registro público— y cada uno validaba distinto: el público, que es
 * el que usa cualquiera con la URL de la API, era el MENOS exigente. Un
 * documento "1" o una contraseña de un caracter pasaban el cliente y se comían
 * el round-trip para que el backend los rechazara, sobre la conexión de una obra.
 *
 * Las reglas viven acá para que haya una sola verdad, y se prueban acá para que
 * no vuelvan a divergir.
 */
describe('documentoInvalido', () => {
  it('acepta documentos de 7 a 9 dígitos', () => {
    expect(documentoInvalido('1234567')).toBeNull();
    expect(documentoInvalido('30123456')).toBeNull();
    expect(documentoInvalido('123456789')).toBeNull();
  });

  it('rechaza los que tienen menos de 7 o más de 9 dígitos', () => {
    expect(documentoInvalido('1')).not.toBeNull();
    expect(documentoInvalido('123456')).not.toBeNull();
    expect(documentoInvalido('1234567890')).not.toBeNull();
  });

  it('rechaza el que no es solo dígitos', () => {
    expect(documentoInvalido('30.123.456')).not.toBeNull();
    expect(documentoInvalido('30123456X')).not.toBeNull();
  });

  it('ignora los espacios de los costados', () => {
    expect(documentoInvalido('  30123456  ')).toBeNull();
  });

  it('rechaza el vacío', () => {
    expect(documentoInvalido('')).not.toBeNull();
    expect(documentoInvalido('   ')).not.toBeNull();
  });
});

describe('passwordInvalida', () => {
  it('acepta desde el mínimo', () => {
    expect(passwordInvalida('a'.repeat(PASSWORD_MIN))).toBeNull();
  });

  it('rechaza por debajo del mínimo', () => {
    expect(passwordInvalida('a'.repeat(PASSWORD_MIN - 1))).not.toBeNull();
    expect(passwordInvalida('')).not.toBeNull();
  });

  // La contraseña NO se recorta: un espacio adelante o atrás es parte de ella.
  it('cuenta los espacios como caracteres válidos', () => {
    expect(passwordInvalida(' '.repeat(PASSWORD_MIN))).toBeNull();
  });
});

describe('fechaISOInvalida', () => {
  it('acepta una fecha ISO real', () => {
    expect(fechaISOInvalida('2026-08-19')).toBeNull();
    expect(fechaISOInvalida('2024-02-29')).toBeNull(); // bisiesto
  });

  it('rechaza el formato que un argentino escribe por reflejo', () => {
    expect(fechaISOInvalida('05/08/2026')).not.toBeNull();
    expect(fechaISOInvalida('5-8-2026')).not.toBeNull();
  });

  // EL CASO QUE IMPORTA: el formato es valido pero la fecha NO EXISTE. Sin este
  // chequeo, "2026-13-45" viajaba al backend tal cual.
  it('rechaza una fecha con formato válido que no existe', () => {
    expect(fechaISOInvalida('2026-13-45')).not.toBeNull();
    expect(fechaISOInvalida('2026-02-30')).not.toBeNull();
    expect(fechaISOInvalida('2025-02-29')).not.toBeNull(); // 2025 no es bisiesto
    expect(fechaISOInvalida('2026-00-10')).not.toBeNull();
  });

  it('rechaza el vacío', () => {
    expect(fechaISOInvalida('')).not.toBeNull();
  });
});
