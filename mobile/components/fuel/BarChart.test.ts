import { escalaMaxima } from './BarChart';

/**
 * Escala del grafico de barras (PERF-04).
 *
 * POR QUE ESTE ARCHIVO. El ancho de cada barra es `(v / max) * 100`. Cuando
 * todas valen cero, `max` daba 0 y el ancho quedaba en NaN: React Native recibe
 * `width: "NaN%"`, tira un warning y no dibuja la barra. Es el panel de
 * estadisticas mostrando un desglose vacio sin decir por que.
 */
describe('escalaMaxima', () => {
  it('devuelve el mayor valor de la lista', () => {
    expect(
      escalaMaxima([
        { l: 'a', v: 10, amount: '$10' },
        { l: 'b', v: 25, amount: '$25' },
      ]),
    ).toBe(25);
  });

  // EL CASO QUE ROMPIA: un periodo con cargas registradas pero de gasto cero.
  it('nunca devuelve cero cuando todas las barras valen cero', () => {
    const escala = escalaMaxima([
      { l: 'a', v: 0, amount: '$0' },
      { l: 'b', v: 0, amount: '$0' },
    ]);
    expect(escala).toBe(1);
    // Lo que realmente importa: el ancho resultante es un numero, no NaN.
    expect((0 / escala) * 100).not.toBeNaN();
  });

  // Math.max() sin argumentos devuelve -Infinity.
  it('no devuelve -Infinity con la lista vacia', () => {
    expect(escalaMaxima([])).toBe(1);
  });

  it('no se deja arrastrar por un cero entre valores reales', () => {
    expect(
      escalaMaxima([
        { l: 'a', v: 0, amount: '$0' },
        { l: 'b', v: 8, amount: '$8' },
      ]),
    ).toBe(8);
  });
});
