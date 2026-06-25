import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { AuthPage } from '../pages/auth.page';
import { stubAuth, stubPlants } from '../fixtures/api-stubs';

test.describe('Accessibility (WCAG 2.1 AA)', () => {
  test('login page has no critical a11y violations', async ({ page }) => {
    await stubAuth(page);
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    expect(results.violations).toHaveLength(0);
  });

  test('register page has no critical a11y violations', async ({ page }) => {
    await stubAuth(page);
    await page.goto('/register');
    await page.waitForLoadState('networkidle');

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    expect(results.violations).toHaveLength(0);
  });

  test('home page (authenticated) has no critical a11y violations', async ({ page }) => {
    await stubAuth(page);
    await stubPlants(page);
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

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    expect(results.violations).toHaveLength(0);
  });
});
