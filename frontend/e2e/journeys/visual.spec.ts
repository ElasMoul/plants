import { test, expect } from '@playwright/test';
import { AuthPage } from '../pages/auth.page';
import { stubAuth, stubPlants } from '../fixtures/api-stubs';

// Visual regression: run `npx playwright test --update-snapshots` to regenerate baselines.
// Snapshots live in e2e/journeys/visual.spec.ts-snapshots/ (committed to git).

test.describe('Visual regression', () => {
  test('login page matches snapshot', async ({ page }) => {
    await stubAuth(page);
    await page.goto('/login');
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveScreenshot('login.png', { maxDiffPixelRatio: 0.02 });
  });

  test('garden page matches snapshot', async ({ page }) => {
    await stubAuth(page);
    await stubPlants(page);
    const auth = new AuthPage(page);
    await auth.navigateToLogin();
    await auth.login('test@plantpal.test', 'password123');
    await page.goto('/garden');
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveScreenshot('garden.png', { maxDiffPixelRatio: 0.02 });
  });

  test('home page matches snapshot', async ({ page }) => {
    await stubAuth(page);
    await stubPlants(page);
    // Stub the dashboard endpoint
    await page.route('**/api/v1/dashboard**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: { plantCount: 1, upcomingReminders: [], recentIdentifications: [] } }),
      });
    });
    const auth = new AuthPage(page);
    await auth.navigateToLogin();
    await auth.login('test@plantpal.test', 'password123');
    await page.goto('/home');
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveScreenshot('home.png', { maxDiffPixelRatio: 0.02 });
  });
});
