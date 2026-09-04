import { defineConfig, devices } from '@playwright/test';

const atlasE2E = /e2e[\\/]atlas[\\/]/;
const atlasSpecs = /e2e[\\/]atlas[\\/].*\.spec\.ts$/;

export default defineConfig({
  testDir: './e2e',
  testIgnore: ['**/visual.spec.ts'],
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  workers: process.env['CI'] ? 1 : undefined,
  reporter: process.env['CI'] ? [['html', { open: 'never' }], ['github']] : 'html',
  // The atlas mock garden needs no backend and no login, so Playwright owns the
  // dev server's lifecycle. PW_NO_SERVER=1 attaches to one that is already up.
  webServer: process.env['PW_NO_SERVER']
    ? undefined
    : {
        command: 'npm run start:atlas',
        url: 'http://localhost:4300',
        reuseExistingServer: !process.env['CI'],
        timeout: 180000,
      },
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    // Visual regression: ignore 2% pixel difference for anti-aliasing
    toHaveScreenshot: { maxDiffPixelRatio: 0.02 },
  },
  projects: [
    { name: 'chromium', testIgnore: atlasE2E, use: { ...devices['Desktop Chrome'] } },
    { name: 'webkit', testIgnore: atlasE2E, use: { ...devices['Desktop Safari'] } },
    {
      name: 'atlas',
      testMatch: atlasSpecs,
      use: { ...devices['Desktop Chrome'], baseURL: 'http://localhost:4300' },
    },
  ],
});
