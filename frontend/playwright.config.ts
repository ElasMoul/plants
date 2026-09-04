import { defineConfig, devices } from '@playwright/test';

// The atlas suite lives in playwright.atlas.config.ts (its own dev server); the
// classic suites must not select its specs.
const ignored = ['**/e2e/atlas/**', '**/visual.spec.ts'];

export default defineConfig({
  testDir: './e2e',
  testIgnore: ignored,
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  workers: process.env['CI'] ? 1 : undefined,
  reporter: process.env['CI'] ? [['html', { open: 'never' }], ['github']] : 'html',
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    // Visual regression: ignore 2% pixel difference for anti-aliasing
    toHaveScreenshot: { maxDiffPixelRatio: 0.02 },
  },
  projects: [
    { name: 'chromium', testIgnore: ignored, use: { ...devices['Desktop Chrome'] } },
    { name: 'webkit', testIgnore: ignored, use: { ...devices['Desktop Safari'] } },
  ],
});
