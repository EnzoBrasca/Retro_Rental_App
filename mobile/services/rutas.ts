import type { Usuario } from './sessionStorage';

/**
 * A qué ruta corresponde una sesión. ÚNICA fuente de la regla de roles.
 *
 * POR QUÉ EXISTE. La decisión se toma en dos lugares —`app/index.tsx` con un
 * `<Redirect>` para que el primer frame ya apunte al destino correcto, y
 * `app/_layout.tsx` con un `router.replace()` que guarda toda la navegación—
 * y sobre la ruta `/` se disparan las dos. Que se evalúe dos veces es
 * deliberado; que la regla estuviera ESCRITA dos veces no: si mañana entra un
 * tercer rol, hay que acordarse de los dos archivos.
 */
export function rutaSegunSesion(user: Usuario | null): string {
  if (!user) return '/(auth)/login';
  return user.rol === 'EMPLEADO' ? '/(empleado)' : '/(administrador)';
}

/**
 * Si un usuario puede quedarse donde está, según el primer segmento de la ruta.
 *
 * Un administrador SÍ puede entrar a `(empleado)`: es el "modo operario" del
 * panel. Al revés no.
 */
export function puedeQuedarse(user: Usuario | null, grupo: string | undefined): boolean {
  if (!user) return grupo === '(auth)';
  if (user.rol === 'EMPLEADO') return grupo === '(empleado)';
  return grupo === '(administrador)' || grupo === '(empleado)';
}
