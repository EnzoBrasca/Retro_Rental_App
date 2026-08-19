/**
 * Design tokens de la app. Única fuente de color, tipografía y radios.
 *
 * Toda la app es oscura y se tematiza desde acá. Si un color hace falta y no
 * está, se agrega un token: escribirlo a mano en un StyleSheet lo vuelve
 * imposible de cambiar después sin buscarlo por todo el proyecto.
 */
export const colors = {
  bg: '#16181B',
  bgDeep: '#101215',
  bgBlack: '#0c0d0f',
  surface: '#1F2226',
  surfaceAlt: '#22262b',
  surfaceInput: '#22262b',
  card: '#1B1E22',
  border: '#2A2F35',
  borderSoft: '#2C3138',
  borderInput: '#2E343B',
  divider: '#262b30',

  primary: '#F5C518',
  primaryLight: '#ffd94d',
  primaryDark: '#c99a00',

  text: '#E8E6E1',
  textMuted: '#9AA0A6',
  textFaint: '#8A9099',
  textDim: '#6E747C',

  green: '#3FB65E',
  greenText: '#5fd07f',
  greenBg: '#173d24',
  orange: '#FF6A00',
  amberText: '#f0a94a',
  amberBg: '#3a2a15',
  danger: '#ff6b6b',
  dangerBg: '#241a1a',
  dangerBorder: '#4a2626',
} as const;

export const fonts = {
  display: 'Oswald_600SemiBold',
  displayBold: 'Oswald_700Bold',
  sans: 'IBMPlexSans_400Regular',
  sansMed: 'IBMPlexSans_500Medium',
  sansSemi: 'IBMPlexSans_600SemiBold',
  mono: 'IBMPlexMono_500Medium',
} as const;

export const radius = {
  sm: 8,
  md: 11,
  lg: 14,
  xl: 16,
} as const;
