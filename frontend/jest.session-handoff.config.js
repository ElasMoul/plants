const path = require('path');
const baseConfig = require('./jest.config');

/** @type {import('jest').Config} */
module.exports = {
  ...baseConfig,
  testMatch: ['<rootDir>/projects/shared-core/src/lib/session-handoff.spec.ts'],
  reporters: [
    'default',
    [
      'jest-junit',
      {
        outputDirectory: path.join(__dirname, 'test-results'),
        outputName: 'session-handoff.xml',
        suiteNameTemplate: 'session-handoff.spec.ts',
        classNameTemplate: '{classname}',
        titleTemplate: '{title}',
      },
    ],
  ],
};
