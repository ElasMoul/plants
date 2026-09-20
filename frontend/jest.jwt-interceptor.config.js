const path = require('path');
const baseConfig = require('./jest.config');

/** @type {import('jest').Config} */
module.exports = {
  ...baseConfig,
  testMatch: ['<rootDir>/src/app/core/interceptors/jwt.interceptor.spec.ts'],
  reporters: [
    'default',
    [
      'jest-junit',
      {
        outputDirectory: path.join(__dirname, 'test-results'),
        outputName: 'jwt-interceptor.xml',
        suiteNameTemplate: 'jwt.interceptor.spec.ts',
        classNameTemplate: '{classname}',
        titleTemplate: '{title}',
      },
    ],
  ],
};
