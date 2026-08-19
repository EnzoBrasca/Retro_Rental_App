/**
 * Pasos del tutorial de bienvenida del operario.
 *
 * Vive acá y no en `data/mock.ts` porque NO es un mock: es copy de producto que
 * se muestra en producción. Estaba mezclado con datos simulados de pantallas que
 * hacía rato tenían servicios reales detrás, y eso hacía parecer que el tutorial
 * también era andamio pendiente de cablear.
 */
export const TUTORIAL_STEPS = [
  {
    label: 'Paso 1 de 4',
    title: 'Tu flota, de un vistazo',
    text: 'Acá ves tu vehículo asignado, su nivel de combustible y toda la maquinaria a tu cargo.',
  },
  {
    label: 'Paso 2 de 4',
    title: 'Escaneá el ticket',
    text: 'Tocá el botón central para fotografiar el ticket de carga. La app extrae los datos automáticamente.',
  },
  {
    label: 'Paso 3 de 4',
    title: 'Revisá el historial',
    text: 'Consultá todas las cargas registradas de tu flota, con su estado y monto.',
  },
  {
    // El texto ya no promete editar los datos ni cambiar el idioma: lo primero
    // nunca persistió y lo segundo no existe (no hay i18n en el proyecto).
    // Un tutorial que enseña funciones inexistentes manda al usuario a buscarlas.
    label: 'Paso 4 de 4',
    title: 'Gestioná tu cuenta',
    text: 'Desde tu perfil consultás tus datos y cerrás sesión.',
  },
];
