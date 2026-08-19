import { formatDay, formatFecha, formatMoney } from './labels';

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
