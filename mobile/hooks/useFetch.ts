import { useCallback, useEffect, useState } from 'react';

/**
 * Hook genérico de fetch-on-mount con estados de carga/error y refetch.
 *
 * Recibe una función async que devuelve T (puede componer varias llamadas con
 * Promise.all). Se re-ejecuta cuando cambia alguna dependencia de `deps`.
 */
export function useFetch<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await fn());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo cargar la información.');
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    run();
  }, [run]);

  return { data, loading, error, refetch: run };
}
