/**
 * Precio por litro de la mezcla (nafta con aceite, motores 2 tiempos).
 *
 * ESPEJO DE `MezclaCalculator` DEL BACKEND. La cuenta se hace en los dos lados
 * a propósito: el backend calcula el precio que se guarda, y la app calcula el
 * que le muestra al operario ANTES de confirmar. Si las dos fórmulas divergen,
 * el operario ve un número y se registra otro — por eso esto vive en su propio
 * archivo con tests, y no suelto adentro de la pantalla.
 *
 * La relación se expresa como su primer término: 50 = 50:1, o sea 50 partes de
 * nafta por 1 de aceite.
 */

/**
 * @param precioNafta  precio por litro de la nafta base
 * @param precioAceite precio por litro del aceite 2 tiempos
 * @param relacion     partes de nafta por parte de aceite (50 = 50:1)
 * @returns precio por litro con 2 decimales, o null si falta algún dato
 */
export function precioMezcla(
  precioNafta: number | null | undefined,
  precioAceite: number | null | undefined,
  relacion: number | null | undefined,
): number | null {
  if (precioNafta == null || precioAceite == null || relacion == null) return null;
  if (!(precioNafta > 0) || !(precioAceite > 0) || !(relacion > 0)) return null;

  // (nafta × r + aceite) / (r + 1), NO nafta × r/(r+1) + aceite × 1/(r+1).
  // Son la misma identidad algebraica, pero la segunda divide dos veces y
  // arrastra el error de redondeo amplificado por precios de miles de pesos.
  // Mismo orden de operaciones que el backend, que es lo que garantiza que los
  // dos lados den el mismo número.
  const bruto = (precioNafta * relacion + precioAceite) / (relacion + 1);

  // La escala de precios.precio_unitario es numeric(10,2): más decimales no
  // entran, y mostrar más de los que se guardan sería mentirle al operario.
  return Math.round(bruto * 100) / 100;
}
