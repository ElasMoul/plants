import { defineConfig } from 'vitest/config';
import angular from '@analogjs/vitest-angular';

export default defineConfig({
  plugins: [angular({ tsconfig: './tsconfig.spec.json' })],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['src/test-setup.ts'],
    include: ['src/**/*.spec.ts'],
    reporters: ['default'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'html'],
      include: ['src/app/**/*.ts'],
      exclude: [
        'src/app/**/*.module.ts',
        'src/app/**/*.model.ts',
        'src/app/**/*.routing.ts',
        'src/app/**/*-routing.module.ts',
        'src/environments/**',
        'src/main.ts',
        'src/app/app.component.ts',
      ],
      thresholds: { lines: 30 },
    },
  },
});
