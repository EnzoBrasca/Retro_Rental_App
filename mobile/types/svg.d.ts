// Permite importar archivos .svg como componentes React tipados.
// Lo consume react-native-svg-transformer (ver metro.config.js).
declare module '*.svg' {
  import type { FC } from 'react';
  import type { SvgProps } from 'react-native-svg';
  const content: FC<SvgProps>;
  export default content;
}
