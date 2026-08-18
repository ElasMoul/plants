/** @type {import('jest').Config} */
module.exports = {
  preset: 'jest-preset-angular',
  setupFilesAfterEnv: ['<rootDir>/src/test-setup.ts'],
  testPathIgnorePatterns: ['/node_modules/', '/e2e/'],
  // tsconfig `paths` aliases are not read by jest — map workspace libraries to source.
  moduleNameMapper: {
    '^@plantpal/shared-core$': '<rootDir>/projects/shared-core/src/public-api.ts',
  },
  collectCoverageFrom: [
    'src/app/**/*.ts',
    '!src/app/**/*.module.ts',
    '!src/app/**/*.model.ts',
    '!src/app/**/*.routing.ts',
    '!src/app/**/*-routing.module.ts',
    '!src/main.ts',
    '!src/app/app.component.ts',
    '!src/environments/**',
  ],
  coverageThreshold: {
    global: { lines: 30 },
  },
};
