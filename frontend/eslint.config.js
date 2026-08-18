// @ts-check
// Flat config for ESLint 9 + angular-eslint 20 (migrated from .eslintrc.json).
// Mirrors the previous rule set: recommended TS + Angular rules, app-prefixed
// component/directive selectors, and the two custom rule tweaks.
const eslint = require('@eslint/js');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

module.exports = tseslint.config(
  {
    ignores: ['projects/**/*', 'dist/**/*'],
  },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'app', style: 'kebab-case' },
      ],
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'app', style: 'camelCase' },
      ],
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      // angular-eslint 20 added these to its recommended set. Both contradict this
      // app's established, documented conventions (CLAUDE.md: "constructor injection
      // only"; the existing app is deliberately NgModule-based). Converting to
      // inject()/standalone is a separate, opt-in refactor — not part of the version
      // upgrade — so they are turned off here for the classic app. The new Atlas app
      // will adopt inject()+standalone via its own config.
      '@angular-eslint/prefer-inject': 'off',
      '@angular-eslint/prefer-standalone': 'off',
    },
  },
  {
    files: ['**/*.html'],
    extends: [
      ...angular.configs.templateRecommended,
    ],
    rules: {},
  },
);
