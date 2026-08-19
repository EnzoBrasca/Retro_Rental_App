import { act, renderHook, waitFor } from '@testing-library/react-native';
import { useFetch } from './useFetch';

/**
 * Condicion de carrera de useFetch (STATE-01 de docs/FRONTEND-AUDIT.md).
 *
 * POR QUE ESTE ARCHIVO. El bug que arregla el hook no se ve leyendo el codigo ni
 * lo agarra `tsc`: aparece solo cuando dos respuestas vuelven en orden distinto
 * al que se pidieron, que sobre buena conexion casi nunca pasa y sobre la de un
 * yacimiento pasa. Un test es la unica forma de que la guarda no se caiga sin
 * que nadie se entere.
 *
 * OJO CON LA API: en @testing-library/react-native 14, `renderHook` y `rerender`
 * son ASINCRONOS (se alinearon con el act asincrono de React 19). Sin el await,
 * `result` queda undefined y todo el archivo falla sin decir por que.
 */

/** Promesa que se resuelve a mano, para controlar el ORDEN de las respuestas. */
function diferida<T>() {
  let resolver!: (v: T) => void;
  let rechazar!: (e: unknown) => void;
  const promesa = new Promise<T>((res, rej) => {
    resolver = res;
    rechazar = rej;
  });
  // El rechazo se maneja dentro del hook; sin este catch, Node avisa por un
  // unhandled rejection cuando el test resuelve la promesa vieja a proposito.
  promesa.catch(() => {});
  return { promesa, resolver, rechazar };
}

describe('useFetch', () => {
  it('trae los datos al montar y apaga el loading', async () => {
    const { result } = await renderHook(() => useFetch(async () => 'listo', []));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toBe('listo');
    expect(result.current.error).toBeNull();
  });

  it('expone el mensaje de error cuando la promesa falla', async () => {
    const { result } = await renderHook(() =>
      useFetch(async () => {
        throw new Error('se cayo la red');
      }, []),
    );

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBe('se cayo la red');
    expect(result.current.data).toBeNull();
  });

  /**
   * EL TEST QUE JUSTIFICA EL HOOK.
   *
   * Es el escenario de Analitica: se cambia el filtro y sale un pedido nuevo sin
   * que haya vuelto el anterior. Si gana el que contesta ultimo, la pantalla
   * muestra los numeros de un filtro y el desplegable dice otro. Sin error y sin
   * spinner: un numero de gasto equivocado presentado como correcto.
   */
  it('NO pisa la respuesta nueva con la de un pedido viejo que llega tarde', async () => {
    const primera = diferida<string>();
    const segunda = diferida<string>();
    const fn = jest
      .fn<Promise<string>, []>()
      .mockReturnValueOnce(primera.promesa)
      .mockReturnValueOnce(segunda.promesa);

    const { result, rerender } = await renderHook(
      ({ filtro }: { filtro: string }) => useFetch(() => fn(), [filtro]),
      { initialProps: { filtro: 'tres-operarios' } },
    );

    // Cambia el filtro: sale el segundo pedido con el primero todavia en vuelo.
    await rerender({ filtro: 'cuatro-operarios' });
    expect(fn).toHaveBeenCalledTimes(2);

    // El SEGUNDO contesta primero.
    await act(async () => {
      segunda.resolver('datos de cuatro operarios');
    });
    await waitFor(() => expect(result.current.data).toBe('datos de cuatro operarios'));

    // Y recien ahi vuelve el PRIMERO, con datos ya viejos.
    await act(async () => {
      primera.resolver('datos de tres operarios');
    });

    // Lo que se ve sigue siendo lo ultimo pedido.
    expect(result.current.data).toBe('datos de cuatro operarios');
    expect(result.current.loading).toBe(false);
  });

  it('tampoco deja que el error de un pedido viejo tape los datos nuevos', async () => {
    const primera = diferida<string>();
    const segunda = diferida<string>();
    const fn = jest
      .fn<Promise<string>, []>()
      .mockReturnValueOnce(primera.promesa)
      .mockReturnValueOnce(segunda.promesa);

    const { result, rerender } = await renderHook(
      ({ filtro }: { filtro: string }) => useFetch(() => fn(), [filtro]),
      { initialProps: { filtro: 'a' } },
    );
    await rerender({ filtro: 'b' });

    await act(async () => {
      segunda.resolver('datos buenos');
    });
    await waitFor(() => expect(result.current.data).toBe('datos buenos'));

    // El pedido viejo falla DESPUES de que el nuevo trajo datos validos.
    await act(async () => {
      primera.rechazar(new Error('timeout del pedido viejo'));
    });

    expect(result.current.error).toBeNull();
    expect(result.current.data).toBe('datos buenos');
  });

  it('refetch vuelve a ejecutar la funcion', async () => {
    let n = 0;
    const { result } = await renderHook(() => useFetch(async () => ++n, []));

    await waitFor(() => expect(result.current.data).toBe(1));

    await act(async () => {
      await result.current.refetch();
    });

    expect(result.current.data).toBe(2);
  });

  it('vuelve a pedir cuando cambian las deps', async () => {
    const fn = jest.fn(async (filtro: string) => `datos de ${filtro}`);
    const { result, rerender } = await renderHook(
      ({ filtro }: { filtro: string }) => useFetch(() => fn(filtro), [filtro]),
      { initialProps: { filtro: 'a' } },
    );

    await waitFor(() => expect(result.current.data).toBe('datos de a'));

    await rerender({ filtro: 'b' });
    await waitFor(() => expect(result.current.data).toBe('datos de b'));
    expect(fn).toHaveBeenCalledTimes(2);
  });
});
