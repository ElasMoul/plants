import { defineConfig, devices } from '@playwright/test';

/**
 * The atlas e2e run, kept in its own config so the classic chromium/webkit
 * suites never boot (or wait on) the atlas dev server: Playwright starts every
 * `webServer` a config declares, regardless of which project you filter to.
 *
 * The atlas mock garden needs no backend and no login, so Playwright owns the
 * dev server's lifecycle. PW_NO_SERVER=1 attaches to one that is already up.
 */
export default defineConfig({
  testDir: './e2e/atlas',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  workers: process.env['CI'] ? 1 : undefined,
  reporter: process.env['CI'] ? [['html', { open: 'never' }], ['github']] : 'html',
  webServer: process.env['PW_NO_SERVER']
    ? undefined
    : {
        command: 'npm run start:atlas',
        url: 'http://localhost:4300',
        reuseExistingServer: !process.env['CI'],
        timeout: 180000,
      },
  use: {
    baseURL: 'http://localhost:4300',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'atlas', use: { ...devices['Desktop Chrome'] } }],
});
