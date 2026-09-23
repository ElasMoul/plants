import { expect, Page, test } from '@playwright/test';

async function userSession(page: Page) {
  await page.addInitScript(() => {
    const payload = btoa(JSON.stringify({ sub: 'test@example.test', userId: 2, exp: Date.now() / 1000 + 3600 }));
    localStorage.setItem('plantpal_token', `test.${payload}.test`);
    localStorage.setItem('plantpal_user', JSON.stringify({ id: 2, firstName: 'Camille', email: 'test@example.test' }));
    localStorage.setItem('plantpal_notifications_prompted', 'true');
    if (!localStorage.getItem('plantpal.language')) localStorage.setItem('plantpal.language', 'fr');
  });
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    let data: unknown = [];
    if (path.endsWith('/access')) data = { administrator: false };
    else if (path.endsWith('/auth/session')) data = { active: true, enforcementActive: false };
    else if (path.endsWith('/preferences')) data = { visionModelPreference: 'GITHUB_GPT4O', reasoningModelPreference: 'DEEPSEEK_R1' };
    else if (path.endsWith('/dashboard')) data = {
      healthSummary: { totalPlants: 0, healthyCount: 0, issuesCount: 0, unknownCount: 0 },
      speciesCount: 0, todayReminders: [], overdueReminders: [], recentScans: [], healthTrends: [],
    };
    else if (path.endsWith('/mine') || path.endsWith('/plants') || path.endsWith('/identifications')) {
      data = { content: [], totalElements: 0, totalPages: 0, number: 0 };
    }
    await route.fulfill({ json: { success: true, data } });
  });
}

test('switches languages before login and retains the choice after a reload', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByText('Sign in to PlantPal')).toBeVisible();
  await page.getByRole('combobox', { name: 'Language / Langue' }).selectOption('fr');
  await expect(page.getByText('Connexion à PlantPal')).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
  await page.getByTestId('login-email').fill('invalid');
  await page.getByTestId('login-password').focus();
  await expect(page.getByText('Saisissez une adresse e-mail valide')).toBeVisible();
  await page.reload();
  await expect(page.getByText('Connexion à PlantPal')).toBeVisible();
  await page.getByRole('combobox', { name: 'Language / Langue' }).selectOption('en');
  await expect(page.getByText('Sign in to PlantPal')).toBeVisible();
});

test('French user journeys cover home, garden, identification, reminders and settings', async ({ page }) => {
  await userSession(page);
  await page.goto('/home');
  await expect(page.getByRole('heading', { name: 'Découvrons votre première plante' })).toBeVisible();
  await page.getByRole('button', { name: 'Identifier votre première plante' }).click();
  await expect(page.getByRole('dialog')).toContainText('Importer une photo');
  await page.keyboard.press('Escape');
  await page.goto('/garden');
  await expect(page.getByRole('heading', { name: 'Mon jardin' })).toBeVisible();
  await page.goto('/reminders');
  await expect(page.getByRole('heading', { name: 'Calendrier des soins' })).toBeVisible();
  await page.goto('/preferences');
  await expect(page.getByRole('heading', { name: 'Langue', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Paramètres de l’IA' })).toBeVisible();
});

test('French plant form keeps entered content and displays a French date picker', async ({ page }) => {
  await userSession(page);
  await page.goto('/plants/new');
  await page.getByTestId('plant-nickname-input').fill('Office fern');
  await expect(page.getByTestId('plant-nickname-input')).toHaveValue('Office fern');
  await expect(page.getByText('Nom de la plante *', { exact: true })).toBeVisible();
  await page.locator('mat-datepicker-toggle button').click();
  await expect(page.locator('mat-calendar')).toBeVisible();
  await expect(page.locator('.mat-calendar-table-header')).toContainText('L');
  await page.locator('mat-calendar .mat-calendar-body-cell').first().click();
  await expect(page.locator('mat-calendar')).not.toBeVisible();
  await expect(page.locator('.cdk-overlay-pane')).toHaveCount(0);
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: '../docs/screenshots/french-plant-form.png', fullPage: true });
});
