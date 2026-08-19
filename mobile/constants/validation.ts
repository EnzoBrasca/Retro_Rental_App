/**
 * Reglas de validación de formularios, en un solo lugar.
 *
 * POR QUÉ EXISTE. Un empleado se puede crear por dos caminos —el alta del admin
 * y el registro público— y cada uno validaba distinto. El público, que es el que
 * usa cualquier persona con la URL de la API, era el MENOS exigente: aceptaba un
 * documento `"1"` y una contraseña de un carácter, y recién el backend los
 * rechazaba. Sobre la conexión de una obra, ese ida y vuelta se siente.
 *
 * No es un agujero de seguridad —el backend valida igual— sino dos verdades
 * sobre la misma regla en dos archivos, que ya divergieron una vez.
 *
 * Cada función devuelve `null` si el valor está bien, o el mensaje de error
 * listo para mostrar. Así el llamador queda en una línea y el texto del error no
 * se reescribe distinto en cada formulario.
 */

/** El backend acepta entre 7 y 9 dígitos, sin puntos. */
const DOCUMENTO_REGEX = /^\d{7,9}$/;

export const PASSWORD_MIN = 8;

export function documentoInvalido(documento: string): string | null {
  return DOCUMENTO_REGEX.test(documento.trim())
    ? null
    : 'El documento debe tener entre 7 y 9 dígitos, sin puntos.';
}

export function passwordInvalida(password: string): string | null {
  // Sin trim a propósito: un espacio al principio o al final es parte de la
  // contraseña, no ruido de tipeo.
  return password.length >= PASSWORD_MIN
    ? null
    : `La contraseña debe tener al menos ${PASSWORD_MIN} caracteres.`;
}

/**
 * Valida una fecha `AAAA-MM-DD` y —lo que importa— que la fecha EXISTA.
 *
 * El formato solo no alcanza: `2026-13-45` lo cumple y no es una fecha. Se
 * reconstruye con `Date` y se comparan los componentes, porque `new Date` no
 * falla con valores fuera de rango: los desborda (el 30 de febrero se convierte
 * en marzo). Si lo que sale no es lo que entró, la fecha no existía.
 */
export function fechaISOInvalida(iso: string): string | null {
  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso.trim());
  if (!partes) return 'Usá el formato AAAA-MM-DD (por ejemplo, 2026-08-19).';

  const [anio, mes, dia] = [Number(partes[1]), Number(partes[2]), Number(partes[3])];
  const d = new Date(anio, mes - 1, dia);
  const existe = d.getFullYear() === anio && d.getMonth() === mes - 1 && d.getDate() === dia;
  return existe ? null : 'Esa fecha no existe.';
}
