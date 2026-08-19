// Metro transforma los .svg con react-native-svg-transformer, pero Jest no usa
// Metro: sin este mock, cualquier test que toque un modulo que importa un icono
// falla al parsear el XML del SVG como JavaScript.
module.exports = 'SvgMock';
module.exports.ReactComponent = 'SvgMock';
