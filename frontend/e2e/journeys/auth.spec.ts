import { test, expect } from '@playwright/test';
import { AuthPage } from '../pages/auth.page';
import { stubAuth, stubPlants } from '../fixtures/api-stubs';

test.describe('Authentication', () => {
  test('register → login → land on Home', async ({ page }) => {
    await stubAuth(page);
    await stubPlants(page);

    const auth = new AuthPage(page);
    await auth.navigateToRegister();
    await auth.register('test@plantpal.test', 'password123', 'Test User');

    // After register, should redirect to home or garden
    await expect(page).toHaveURL(/\/(home|garden|dashboard)/);
  });

  test('login with valid credentials', async ({ page }) => {
    await stubAuth(page);
    await stubPlants(page);

    const auth = new AuthPage(page);
    await auth.navigateToLogin();
    await auth.login('test@plantpal.test', 'password123');

    await expect(page).not.toHaveURL('/login');
  });

  test('unauthenticated user is redirected to login', async ({ page }) => {
    // No auth stub — JWT guard should redirect
    await page.goto('/garden');
    await expect(page).toHaveURL(/\/login/);
  });
});
