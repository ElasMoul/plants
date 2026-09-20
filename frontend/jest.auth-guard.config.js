const path = require('path');
const baseConfig = require('./jest.config');

/** @type {import('jest').Config} */
module.exports = {
  ...baseConfig,
  testMatch: ['<rootDir>/src/app/core/guards/auth.guard.spec.ts'],
  reporters: [
    'default',
    [
      'jest-junit',
      {
        outputDirectory: path.join(__dirname, 'test-results'),
        outputName: 'auth-guard.xml',
        suiteNameTemplate: 'auth.guard.spec.ts',
        classNameTemplate: '{classname}',
        titleTemplate: '{title}',
      },
    ],
  ],
};
