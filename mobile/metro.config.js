// Metro config extendido para importar archivos .svg como componentes React
// (react-native-svg-transformer). Sin esto, un `import Icon from './x.svg'`
// devolvería la ruta del asset en vez de un componente.
const { getDefaultConfig } = require('expo/metro-config');

const config = getDefaultConfig(__dirname);

config.transformer.babelTransformerPath = require.resolve('react-native-svg-transformer/expo');
config.resolver.assetExts = config.resolver.assetExts.filter((ext) => ext !== 'svg');
config.resolver.sourceExts = [...config.resolver.sourceExts, 'svg'];

module.exports = config;
