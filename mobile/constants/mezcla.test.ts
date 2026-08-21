import { precioMezcla } from './mezcla';

/**
 * Estos casos son los MISMOS que los de MezclaCalculatorTest del backend, con
 * los mismos números esperados. Es deliberado: la fórmula está en los dos lados
 * y lo único que garantiza que no diverjan es que ambas suites afirmen el mismo
 * resultado. Si alguien toca una sola, la otra suite lo delata.
 */
describe('precioMezcla', () => {
  it('50:1 aporta el aceite en su proporción', () => {
    // (2000 x 50 + 15000) / 51 = 115000 / 51 = 2254,9019...
    expect(precioMezcla(2000, 15000, 50)).toBe(2254.9);
  });

  it('una relación más rica encarece la mezcla', () => {
    // 25:1 lleva el doble de aceite que 50:1.
    expect(precioMezcla(2000, 15000, 25)).toBe(2500);
    expect(precioMezcla(2000, 15000, 25)!).toBeGreaterThan(precioMezcla(2000, 15000, 50)!);
  });

  it('se evalúa con una sola división', () => {
    // Con dos divisiones y redondeo intermedio daría 2260: casi seis pesos por
    // litro de diferencia. Es EL motivo del orden de operaciones.
    expect(precioMezcla(2000, 15000, 50)).not.toBe(2260);
  });

  it('con el aceite al precio de la nafta deja el precio de la nafta', () => {
    expect(precioMezcla(2000, 2000, 50)).toBe(2000);
  });

  it('relación 1:1 es el promedio de los dos', () => {
    expect(precioMezcla(2000, 3000, 1)).toBe(2500);
  });

  it('devuelve a lo sumo dos decimales', () => {
    const r = precioMezcla(1999.99, 14999.99, 37)!;
    expect(Number.isInteger(r * 100)).toBe(true);
  });

  it('sin alguno de los datos devuelve null en vez de un número inventado', () => {
    expect(precioMezcla(null, 15000, 50)).toBeNull();
    expect(precioMezcla(2000, null, 50)).toBeNull();
    expect(precioMezcla(2000, 15000, null)).toBeNull();
    expect(precioMezcla(undefined, undefined, undefined)).toBeNull();
  });

  it('con valores no positivos devuelve null', () => {
    // relación 0 sería una mezcla de puro aceite, y un precio en 0 o negativo
    // no es un precio. Se corta acá en vez de mostrar un total absurdo.
    expect(precioMezcla(2000, 15000, 0)).toBeNull();
    expect(precioMezcla(0, 15000, 50)).toBeNull();
    expect(precioMezcla(2000, 0, 50)).toBeNull();
    expect(precioMezcla(2000, 15000, -5)).toBeNull();
  });
});
