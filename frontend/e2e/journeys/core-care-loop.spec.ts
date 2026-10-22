import { test, expect } from '@playwright/test';
import path from 'path';

/**
 * End-to-end smoke test of the core care loop against a real backend running the `e2e` Spring
 * profile (stubbed AI providers, no outbound mail). No browser-side mocking: the API, database and
 * identification pipeline are all real. Every stage is a named test.step so a failure reports it.
 */
const PHOTO = path.join(__dirname, '..', 'fixtures', 'plant.jpg');
const SPECIES = 'Monstera deliciosa';
const COMMON_NAME = 'Swiss Cheese Plant';
const CHAT_REPLY = 'Water your Swiss Cheese Plant when the top two inches of soil feel dry.';

test('core care loop: sign in, scan, save plant, set reminder, ask chat', async ({
  page,
  request,
}) => {
  test.setTimeout(180_000);
  const email = `e2e-pla120-${Date.now()}@plantpal.test`;
  const password = 'e2e-Passw0rd!';

  await test.step('sign in', async () => {
    const registered = await request.post('/api/v1/auth/register', {
      data: { email, password, firstName: 'Smoke', lastName: 'Tester', language: 'en' },
    });
    expect(registered.ok(), `register failed: ${registered.status()}`).toBeTruthy();

    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(password);
    await page.getByTestId('login-submit').click();
    await expect(page).not.toHaveURL(/\/login/);

    // A new account sees the first-run intro on its first page; skip it so it cannot cover the UI.
    const skipIntro = page.getByTestId('intro-skip');
    await skipIntro.waitFor({ timeout: 10_000 }).then(() => skipIntro.click(), () => undefined);
  });

  await test.step('scan fixture photo', async () => {
    await page.goto('/identify');
    await page.locator('app-photo-upload input[type="file"]:not([capture])').setInputFiles(PHOTO);
    await page.getByRole('button', { name: 'Identify Plant' }).click();

    const row = page.locator('.identification-row').filter({ hasText: COMMON_NAME });
    await expect(row).toBeVisible({ timeout: 90_000 });
    await expect(row).toContainText('COMPLETED', { timeout: 90_000 });
    await row.click();
    await expect(page.getByText(SPECIES).first()).toBeVisible();
  });

  await test.step('save plant', async () => {
    // "Yes, add it" for a new species, plain "Yes" when an earlier run already created it; one test id covers both.
    await page.getByRole('button', { name: /Yes/ }).click();
    await expect(page).toHaveURL(/\/plants\/\d+/);
    await page.goto('/garden');
    await expect(page.getByText(COMMON_NAME).first()).toBeVisible();
  });

  await test.step('set reminder', async () => {
    await page.goto('/reminders');
    await page.getByRole('button', { name: 'Add reminder' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('combobox', { name: 'Plant' }).click();
    await page.getByRole('option').filter({ hasText: SPECIES }).first().click();
    await dialog.getByRole('combobox', { name: 'Care type' }).click();
    await page.getByRole('option', { name: 'Pruning' }).click();
    await dialog.getByRole('button', { name: 'Add Reminder' }).click();
    await expect(dialog).toBeHidden();
    await expect(
      page.locator('.reminder-card').filter({ hasText: 'Pruning' }).first(),
    ).toBeVisible();
  });

  await test.step('ask chat question', async () => {
    await page.goto('/chat');
    await page.getByTestId('chat-input').fill('How often should I water it?');
    await page.getByTestId('chat-send-btn').click();
    await expect(page.getByTestId('chat-messages').getByText(CHAT_REPLY)).toBeVisible({
      timeout: 30_000,
    });
  });
});
