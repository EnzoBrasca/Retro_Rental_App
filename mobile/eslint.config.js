const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');
const prettierConfig = require('eslint-config-prettier');
// Dependencia DIRECTA de eslint-config-expo, no una transitiva de casualidad:
// en config plana hay que declarar el plugin en el mismo objeto donde se
// configuran sus reglas, y el preset de Expo lo declara en el suyo.
const tsPlugin = require('@typescript-eslint/eslint-plugin');

/**
 * Config de ESLint del cliente móvil.
 *
 * Prettier NO corre a través de ESLint (el doc de Expo sugiere
 * eslint-plugin-prettier, que reporta cada diferencia de formato como un error
 * de lint). Acá el formato lo revisa `npm run format:check` y las reglas las
 * revisa `npm run lint`: son dos problemas distintos y conviene leerlos por
 * separado. `eslint-config-prettier` va igual, para apagar las reglas de estilo
 * de ESLint que pelearían con Prettier.
 *
 * Todo junto corre con `npm run verify`.
 */
module.exports = defineConfig([
  expoConfig,
  prettierConfig,
  {
    ignores: ['dist/*', '.expo/*', 'node_modules/*'],
  },
  {
    files: ['**/*.ts', '**/*.tsx'],
    plugins: { '@typescript-eslint': tsPlugin },
    rules: {
      // En error, no en warn. Es la regla que habría marcado STATE-01 y buena
      // parte de STATE-02 (ver docs/FRONTEND-AUDIT.md): como warning se
      // convierte en ruido que nadie mira.
      'react-hooks/exhaustive-deps': 'error',
      // Las variables sin usar se toleran solo con guión bajo adelante, que es
      // la forma de decir "esto se descarta a propósito".
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
    },
  },
  {
    // Los tests corren en Node con los globals de Jest, no en el runtime de la app.
    files: ['**/*.test.ts', '**/*.test.tsx', '**/__tests__/**'],
    languageOptions: {
      globals: {
        jest: 'readonly',
        describe: 'readonly',
        it: 'readonly',
        expect: 'readonly',
        beforeEach: 'readonly',
        afterEach: 'readonly',
      },
    },
  },
]);
