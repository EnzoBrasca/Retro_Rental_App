import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Hook genérico de fetch-on-mount con estados de carga/error y refetch.
 *
 * Recibe una función async que devuelve T (puede componer varias llamadas con
 * Promise.all). Se re-ejecuta cuando cambia alguna dependencia de `deps`.
 *
 * CONTRATO DE `deps`: `fn` se re-captura SOLO cuando cambia `deps`, así que todo
 * lo que `fn` lea de afuera tiene que estar listado ahí. Si falta algo, el hook
 * sigue ejecutando una versión vieja de la función y los datos quedan atrasados
 * sin que nada avise. Hoy no hay linter que verifique esa regla (por eso el
 * eslint-disable de más abajo no lo corre nadie): lo sostiene esta nota.
 */
export function useFetch<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Cada ejecución se numera, y solo la MÁS RECIENTE puede escribir estado.
  //
  // Sin esto gana la que conteste última, que no es la última pedida: al tildar
  // cuatro operarios en Analítica salen cuatro requests, y si el de tres vuelve
  // después del de cuatro, la pantalla muestra los números de tres mientras el
  // desplegable dice cuatro. No hay error ni spinner: hay un número de gasto
  // equivocado presentado como correcto. Sobre buena conexión casi no pasa;
  // sobre la de un yacimiento, pasa.
  //
  // Descarta la respuesta, no corta la conexión. Cortarla de verdad exige pasar
  // un AbortSignal por api.ts y por cada función de services/ (ver
  // docs/FRONTEND-AUDIT.md, STATE-01).
  const generacion = useRef(0);
  // También se descarta si el componente ya se desmontó — el operario cambió de
  // pestaña con un request en vuelo. React 19 no se queja de eso, pero pintar
  // estado de una pantalla que ya no existe es trabajo al pedo.
  const montado = useRef(true);
  useEffect(() => {
    montado.current = true;
    return () => {
      montado.current = false;
    };
  }, []);

  const run = useCallback(async () => {
    const propia = ++generacion.current;
    const vigente = () => montado.current && generacion.current === propia;
    setLoading(true);
    setError(null);
    try {
      const resultado = await fn();
      if (vigente()) setData(resultado);
    } catch (e) {
      if (vigente()) {
        setError(e instanceof Error ? e.message : 'No se pudo cargar la información.');
      }
    } finally {
      // Si esta ejecución quedó vieja, el loading lo apaga la que la reemplazó:
      // apagarlo acá dejaría la pantalla sin spinner con un request en vuelo.
      if (vigente()) setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    run();
  }, [run]);

  return { data, loading, error, refetch: run };
}
