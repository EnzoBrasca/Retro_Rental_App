import { puedeQuedarse, rutaSegunSesion } from './rutas';
import type { Usuario } from './sessionStorage';

/**
 * Regla de navegación por rol (NAV-01).
 *
 * POR QUE ESTE ARCHIVO. La regla se evalúa en DOS lugares —`app/index.tsx` y
 * `app/_layout.tsx`— y antes estaba escrita dos veces. Ahora está una sola vez y
 * probada acá: si mañana entra un tercer rol, estos tests dicen exactamente qué
 * falta contemplar.
 */
function usuario(rol: Usuario['rol']): Usuario {
  return {
    username: 'jperez',
    nombre: 'Juan',
    apellido: 'Pérez',
    rol,
  } as Usuario;
}

describe('rutaSegunSesion', () => {
  it('sin sesión manda al login', () => {
    expect(rutaSegunSesion(null)).toBe('/(auth)/login');
  });

  it('un empleado va a su sección', () => {
    expect(rutaSegunSesion(usuario('EMPLEADO'))).toBe('/(empleado)');
  });

  it('un administrador va a la suya', () => {
    expect(rutaSegunSesion(usuario('ADMINISTRADOR'))).toBe('/(administrador)');
  });
});

describe('puedeQuedarse', () => {
  it('sin sesión solo se queda en el grupo de auth', () => {
    expect(puedeQuedarse(null, '(auth)')).toBe(true);
    expect(puedeQuedarse(null, '(empleado)')).toBe(false);
    expect(puedeQuedarse(null, '(administrador)')).toBe(false);
    expect(puedeQuedarse(null, undefined)).toBe(false);
  });

  it('un empleado solo se queda en su sección', () => {
    const e = usuario('EMPLEADO');
    expect(puedeQuedarse(e, '(empleado)')).toBe(true);
    expect(puedeQuedarse(e, '(administrador)')).toBe(false);
    expect(puedeQuedarse(e, '(auth)')).toBe(false);
  });

  // EL CASO QUE SE PIERDE AL REESCRIBIR LA REGLA: el admin tiene "modo
  // operario". Entrar a (empleado) no lo expulsa.
  it('un administrador puede entrar al área de operario sin ser expulsado', () => {
    const a = usuario('ADMINISTRADOR');
    expect(puedeQuedarse(a, '(administrador)')).toBe(true);
    expect(puedeQuedarse(a, '(empleado)')).toBe(true);
    expect(puedeQuedarse(a, '(auth)')).toBe(false);
  });

  // Sobre la ruta '/', segments[0] es undefined: nadie se queda ahí, y por eso
  // el layout redirige igual que el <Redirect> de index.tsx.
  it('nadie se queda en la raíz', () => {
    expect(puedeQuedarse(usuario('EMPLEADO'), undefined)).toBe(false);
    expect(puedeQuedarse(usuario('ADMINISTRADOR'), undefined)).toBe(false);
  });
});
