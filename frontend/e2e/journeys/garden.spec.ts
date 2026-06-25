import { test, expect } from '@playwright/test';
import { AuthPage } from '../pages/auth.page';
import { GardenPage } from '../pages/garden.page';
import { stubAuth, stubPlants } from '../fixtures/api-stubs';

test.describe('Garden', () => {
  test.beforeEach(async ({ page }) => {
    await stubAuth(page);
    await stubPlants(page);
    const auth = new AuthPage(page);
    await auth.navigateToLogin();
    await auth.login('test@plantpal.test', 'password123');
  });

  test('garden page loads and shows plant cards', async ({ page }) => {
    const garden = new GardenPage(page);
    await garden.navigate();

    await expect(page.getByTestId('plant-card').first()).toBeVisible();
  });

  test('add a new plant via the form', async ({ page }) => {
    const garden = new GardenPage(page);
    await garden.navigate();
    await garden.clickAddPlant();
    await garden.fillPlantForm('My Test Plant');
    await garden.submitPlantForm();

    // Form should close / success indicator
    await expect(page.getByTestId('plant-nickname-input')).not.toBeVisible();
  });
});
