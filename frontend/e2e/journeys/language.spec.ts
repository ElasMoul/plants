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
  await page.getByRole('combobox', { name: 'Language / Langue / اللغة' }).selectOption('fr');
  await expect(page.getByText('Connexion à PlantPal')).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
  await page.getByTestId('login-email').fill('invalid');
  await page.getByTestId('login-password').focus();
  await expect(page.getByText('Saisissez une adresse e-mail valide')).toBeVisible();
  await page.reload();
  await expect(page.getByText('Connexion à PlantPal')).toBeVisible();
  await page.getByRole('combobox', { name: 'Language / Langue / اللغة' }).selectOption('en');
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

test('saved AI descriptions and care cards become French after the translation job completes', async ({ page }) => {
  await userSession(page);
  let polls = 0;
  let language = '';
  await page.route('**/api/v1/species/77', async route => {
    language = route.request().headers()['x-content-language'];
    await route.fulfill({ json: { success: true, data: {
      id: 77, scientificName: 'Monstera deliciosa', descriptionStatus: 'READY',
      description: 'A tropical climbing plant.', careOverview: 'Water every 7 days with 100 ml.',
      careCards: [{ type: 'WATERING', icon: 'water_drop', title: 'Water the roots',
        summary: 'Water every 7 days with 100 ml.', detail: 'Keep the leaves dry.', urgency: 'LOW' }],
    }, localization: { id: 'species-fr', language: 'fr', status: 'PENDING', texts: {} } } });
  });
  await page.route('**/api/v1/translations/species-fr', route => route.fulfill({ json: { success: true, data: {
    id: 'species-fr', language: 'fr', status: ++polls > 1 ? 'READY' : 'PENDING', texts: {
      'A tropical climbing plant.': 'Une plante grimpante tropicale.',
      'Water every 7 days with 100 ml.': 'Arrosez tous les 7 jours avec 100 ml.',
      'Water the roots': 'Arroser les racines', 'Keep the leaves dry.': 'Gardez les feuilles sèches.',
    },
  } } }));
  await page.goto('/garden/species/77');
  await expect(page.getByText('Préparation du texte IA en français…')).toBeVisible();
  await expect(page.getByText('Une plante grimpante tropicale.')).toBeVisible();
  await expect(page.getByText('Arroser les racines', { exact: true })).toBeVisible();
  await expect(page.getByText('Arrosez tous les 7 jours avec 100 ml.').first()).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Monstera deliciosa', exact: true })).toBeVisible();
  expect(language).toBe('fr');
});

test('translation failure displays the original with a retry control', async ({ page }) => {
  await userSession(page);
  await page.route('**/api/v1/species/78', route => route.fulfill({ json: { success: true, data: {
    id: 78, scientificName: 'Monstera deliciosa', descriptionStatus: 'READY',
    description: 'Original description.', careCards: [],
  }, localization: { id: 'failed-translation', language: 'fr', status: 'FAILED', texts: {} } } }));
  await page.goto('/garden/species/78');
  await expect(page.getByText('Original description.', { exact: true })).toBeVisible();
  await expect(page.getByRole('status')).toContainText('Le texte original est affiché');
  await expect(page.getByRole('button', { name: 'Réessayer la traduction' })).toBeVisible();
});


test('Arabic selector, RTL forms and calendar work on mobile and switching back restores LTR', async ({ page }) => {
  await page.goto('/login');
  const selector = page.getByRole('combobox', { name: 'Language / Langue / اللغة' });
  await expect(selector.locator('option')).toHaveText(['EN', 'FR', 'AR']);
  await expect(page.locator('app-language-switch')).not.toContainText('◎');
  await selector.selectOption('ar');
  await expect(page.locator('html')).toHaveAttribute('lang', 'ar');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await expect(page.getByText('تسجيل الدخول إلى PlantPal')).toBeVisible();
  await page.getByTestId('login-email').fill('reader@example.test');
  await expect(page.getByTestId('login-email')).toHaveCSS('direction', 'ltr');
  await userSession(page);
  await page.goto('/plants/new');
  await page.getByTestId('plant-nickname-input').fill('نبتتي Monstera');
  await expect(page.getByTestId('plant-nickname-input')).toHaveValue('نبتتي Monstera');
  await page.locator('mat-datepicker-toggle button').click();
  await expect(page.locator('mat-calendar')).toContainText(/[\u0600-\u06ff]/);
  await expect(page.locator('mat-calendar')).toHaveCSS('direction', 'rtl');
  await page.locator('mat-calendar .mat-calendar-body-cell').first().click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await expect(page.locator('mat-calendar')).not.toBeVisible();
  await expect(page.locator('.cdk-overlay-pane')).toHaveCount(0);
  await page.screenshot({ path: '../docs/screenshots/arabic-plant-form.png', fullPage: true });
  for (const route of ['/home', '/garden', '/reminders', '/preferences']) {
    await page.goto(route);
    await expect(page.locator('h1, h2').first()).toContainText(/[\u0600-\u06ff]/);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
  await page.getByRole('combobox', { name: 'Language / Langue / اللغة' }).first().selectOption('en');
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
});

test('Arabic AI text uses the Arabic target and keeps scientific names and quantities', async ({ page }) => {
  await userSession(page);
  await page.addInitScript(() => localStorage.setItem('plantpal.language', 'ar'));
  let language = '';
  await page.route('**/api/v1/species/79', async route => {
    language = route.request().headers()['x-content-language'];
    await route.fulfill({ json: { success: true, data: {
      id: 79, scientificName: 'Monstera deliciosa', commonName: 'Swiss cheese plant', descriptionStatus: 'READY',
      description: 'Water every 7 days with 100 ml.', careCards: [],
    }, localization: { id: 'species-ar', language: 'ar', status: 'READY', texts: {
      'Water every 7 days with 100 ml.': 'اسقِ كل 7 أيام بكمية 100 ml.',
      'Swiss cheese plant': 'المونستيرا',
    } } } });
  });
  await page.goto('/garden/species/79');
  await expect(page.getByText('اسقِ كل 7 أيام بكمية 100 ml.', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Monstera deliciosa', exact: true })).toBeVisible();
  await expect(page.getByText('المونستيرا', { exact: true })).toBeVisible();
  expect(language).toBe('ar');
});


test('language changes are saved to the account before the interface reloads', async ({ page }) => {
  await userSession(page);
  let saved = '';
  await page.route('**/api/v1/users/me/preferences', async route => {
    if (route.request().method() === 'PUT') saved = route.request().postDataJSON().language;
    await route.fulfill({ json: { success: true, data: { language: saved || 'fr' } } });
  });
  await page.goto('/preferences');
  await page.getByRole('combobox', { name: 'Language / Langue / اللغة' }).first().selectOption('ar');
  await expect(page.locator('html')).toHaveAttribute('lang', 'ar');
  expect(saved).toBe('ar');
});

test.describe('Registration browser language', () => {
  test.use({ locale: 'ar-MA' });
  test('detects Arabic and sends it with registration', async ({ page }) => {
    let language = '';
    await page.route('**/api/v1/auth/register', async route => {
      language = route.request().postDataJSON().language;
      await route.fulfill({ status: 400, json: { success: false, message: 'Synthetic test response' } });
    });
    await page.goto('/register');
    await expect(page.locator('html')).toHaveAttribute('lang', 'ar');
    await page.getByTestId('register-name').fill('قارئ');
    await page.locator('[formControlName="lastName"]').fill('اختبار');
    await page.getByTestId('register-email').fill('reader@example.test');
    await page.getByTestId('register-password').fill('TestPassword123!');
    await page.locator('[formControlName="confirmPassword"]').fill('TestPassword123!');
    await page.locator('button[type="submit"]').click();
    await expect.poll(() => language).toBe('ar');
  });
});
