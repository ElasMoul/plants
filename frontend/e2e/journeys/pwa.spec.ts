import { test, expect } from '@playwright/test';
import { stubAuth, stubPlants } from '../fixtures/api-stubs';

test.describe('PWA / App Shell', () => {
  test('app shell loads without errors', async ({ page }) => {
    await stubAuth(page);
    await stubPlants(page);

    const errors: string[] = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(msg.text());
    });

    await page.goto('/');
    await expect(page.locator('app-root')).toBeVisible({ timeout: 10000 });

    // Filter out known non-critical errors (e.g. service worker not available in test env)
    const criticalErrors = errors.filter(
      (e) => !e.includes('service-worker') && !e.includes('ngsw')
    );
    expect(criticalErrors).toHaveLength(0);
  });

  test('bottom navigation is present', async ({ page }) => {
    await stubAuth(page);
    await stubPlants(page);
    await page.goto('/login');

    // After login the nav appears
    const auth = (await import('../pages/auth.page')).AuthPage;
    const authPage = new auth(page);
    await authPage.login('test@plantpal.test', 'password123');

    await expect(page.locator('nav, mat-bottom-sheet, [data-testid="bottom-nav"]').first()).toBeVisible();
  });
});
