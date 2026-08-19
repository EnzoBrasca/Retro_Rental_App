import { formatDay, formatFecha, formatMoney, parseEntero, parseNumero } from './labels';

/**
 * Formateo de fechas y montos.
 *
 * POR QUE ESTE ARCHIVO. `formatDay` resuelve un bug que no se ve leyendo el
 * resultado: la fecha sale un dia corrida, y un dia de diferencia en la fecha de
 * un mantenimiento o de una carga no llama la atencion de nadie. Es exactamente
 * la clase de error que `tsc` no puede ver — el codigo compila perfecto y el
 * numero esta mal.
 *
 * Los tests corren con TZ=America/Argentina/Buenos_Aires (ver jest.setup.js).
 * Sin esa fijacion, en un runner en UTC pasarian aun con la implementacion rota.
 */
describe('formatDay', () => {
  // GUARDA DE LA PREMISA. Si este test falla, la zona horaria del runner no es
  // la esperada y TODOS los de abajo pasarian aunque formatDay estuviera roto.
  // Se afirma la premisa para que el archivo no pueda volverse decorativo en
  // silencio: acá el bug tiene que EXISTIR en la via ingenua.
  it('la via ingenua esta rota en esta zona horaria (si no, el resto no prueba nada)', () => {
    expect(new Date('2026-07-06').getDate()).toBe(5);
  });

  // EL TEST QUE IMPORTA. new Date("2026-07-06") es medianoche UTC, que en
  // UTC-3 es el 5 de julio a las 21:00. Leer los componentes locales de ahi
  // devuelve el dia ANTERIOR. formatDay parsea las partes a mano para evitarlo.
  it('no corre el dia hacia atras en una fecha date-only bajo UTC-3', () => {
    expect(formatDay('2026-07-06')).toBe('06 Jul 2026');
  });

  it('mantiene el dia en el primero de mes, que es donde el off-by-one cambia el mes', () => {
    expect(formatDay('2026-07-01')).toBe('01 Jul 2026');
  });

  it('mantiene el dia en el primero de enero, que ademas cambiaria el ano', () => {
    expect(formatDay('2026-01-01')).toBe('01 Ene 2026');
  });

  it('rellena el dia con cero a la izquierda', () => {
    expect(formatDay('2026-03-05')).toBe('05 Mar 2026');
  });

  it('formatea un datetime completo por la via normal', () => {
    expect(formatDay('2026-07-06T14:20:00')).toBe('06 Jul 2026');
  });

  // Un ISO invalido no debe romper la pantalla: se devuelve tal cual.
  it('devuelve la entrada sin tocar cuando no es una fecha valida', () => {
    expect(formatDay('no-es-fecha')).toBe('no-es-fecha');
  });
});

describe('formatFecha', () => {
  it('formatea fecha y hora local', () => {
    expect(formatFecha('2026-07-05T14:20:00')).toBe('05 Jul 2026 · 14:20');
  });

  it('rellena hora y minutos con cero a la izquierda', () => {
    expect(formatFecha('2026-07-05T09:05:00')).toBe('05 Jul 2026 · 09:05');
  });

  it('devuelve la entrada sin tocar cuando no es una fecha valida', () => {
    expect(formatFecha('')).toBe('');
  });
});

describe('formatMoney', () => {
  it('usa el separador de miles argentino', () => {
    expect(formatMoney(53070)).toBe('$53.070');
  });

  it('redondea a peso entero', () => {
    expect(formatMoney(1234.56)).toBe('$1.235');
  });

  it('formatea el cero', () => {
    expect(formatMoney(0)).toBe('$0');
  });

  it('no rompe con un monto de millones', () => {
    expect(formatMoney(1234567)).toBe('$1.234.567');
  });
});

/**
 * Parseo de numeros tipeados por una persona (DATA-02).
 *
 * POR QUE ESTE ARCHIVO IMPORTA ACA. La app corre en telefonos con teclado
 * numerico argentino: punto para miles, coma para decimales. El codigo viejo
 * hacia `parseFloat(texto.replace(',', '.'))`, que reemplaza SOLO la primera
 * ocurrencia y no toca los puntos de miles: "12.500,75" daba 12,5 — un error de
 * mil veces sobre un precio por litro — y `parseInt("1.523")` daba 1.
 *
 * Ninguno de esos casos falla con un error: guardan un numero valido y absurdo.
 */
describe('parseNumero', () => {
  it('lee la coma como separador decimal', () => {
    expect(parseNumero('58,5')).toBe(58.5);
  });

  // EL CASO QUE ROMPIA. Un precio por litro de $12.500,75 se registraba como 12,5.
  it('lee punto de miles junto con coma decimal', () => {
    expect(parseNumero('12.500,75')).toBe(12500.75);
    expect(parseNumero('1.234,5')).toBe(1234.5);
  });

  it('lee varios puntos de miles', () => {
    expect(parseNumero('1.234.567')).toBe(1234567);
  });

  // Un punto solo es AMBIGUO: "1.234" son 1234 litros y "58.5" son 58,5 litros.
  // Se resuelve por la forma del grupo: exactamente 3 digitos despues, 1 a 3
  // antes y sin cero adelante = separador de miles. Cualquier otra cosa, decimal.
  it('resuelve el punto solo por la forma del grupo de miles', () => {
    expect(parseNumero('1.234')).toBe(1234);
    expect(parseNumero('12.500')).toBe(12500);
    expect(parseNumero('123.456')).toBe(123456);
  });

  it('trata como decimal el punto que no forma un grupo de miles', () => {
    expect(parseNumero('58.5')).toBe(58.5);
    expect(parseNumero('1.25')).toBe(1.25);
    expect(parseNumero('1.2345')).toBe(1.2345);
    expect(parseNumero('1234.567')).toBe(1234.567);
  });

  // "0.500" no puede ser un grupo de miles: nadie escribe cero como millar.
  it('no confunde un cero adelante con un grupo de miles', () => {
    expect(parseNumero('0.500')).toBe(0.5);
  });

  // Convencion inglesa: manda el ULTIMO separador como decimal.
  it('tambien entiende la convencion inglesa', () => {
    expect(parseNumero('1,234.5')).toBe(1234.5);
  });

  it('acepta enteros pelados y el cero', () => {
    expect(parseNumero('1234')).toBe(1234);
    expect(parseNumero('0')).toBe(0);
  });

  it('ignora los espacios de los costados', () => {
    expect(parseNumero(' 1.500 ')).toBe(1500);
  });

  it('acepta negativos', () => {
    expect(parseNumero('-5,5')).toBe(-5.5);
  });

  // RECHAZA la basura en vez de comersela. parseFloat("152340km") devolvia
  // 152340 sin chistar; una lectura de contador con unidad pegada tiene que
  // frenar la validacion, no colarse.
  it('devuelve NaN con basura, en vez de recortarla', () => {
    expect(parseNumero('152340km')).toBeNaN();
    expect(parseNumero('abc')).toBeNaN();
    expect(parseNumero('12,5,3')).toBeNaN();
  });

  it('devuelve NaN con vacio o solo espacios', () => {
    expect(parseNumero('')).toBeNaN();
    expect(parseNumero('   ')).toBeNaN();
  });
});

describe('parseEntero', () => {
  // EL CASO QUE ROMPIA. parseInt("1.523") devolvia 1: una lectura de odometro
  // de 1.523 km entraba como 1 km y le destruia el calculo de consumo real al
  // vehiculo.
  it('lee el punto de miles en una lectura de contador', () => {
    expect(parseEntero('1.523')).toBe(1523);
    expect(parseEntero('152.340')).toBe(152340);
  });

  it('lee un entero pelado', () => {
    expect(parseEntero('152340')).toBe(152340);
    expect(parseEntero('0')).toBe(0);
  });

  // El backend guarda usoAcumulado como Integer, asi que un decimal no se puede
  // persistir. Se REDONDEA (no se trunca): 1520,5 -> 1521.
  it('redondea los decimales en vez de truncarlos', () => {
    expect(parseEntero('1520,5')).toBe(1521);
    expect(parseEntero('1520,4')).toBe(1520);
  });

  it('devuelve NaN con basura', () => {
    expect(parseEntero('152340km')).toBeNaN();
    expect(parseEntero('')).toBeNaN();
  });
});
