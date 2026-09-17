import { expect, test } from '@playwright/test';

test.describe('Session monitor resumption', () => {
  test('evicts and re-authenticates in one SPA visit, then starts monitoring the fresh session', async ({
    page,
    request,
  }) => {
    const suffix = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const email = `session-monitor-${suffix}@plantpal.test`;
    const password = 'correct-password';
    const registration = await request.post('/api/v1/auth/register', {
      data: { email, password, firstName: 'Session', lastName: 'Monitor' },
    });
    expect(registration.ok()).toBeTruthy();

    let sessionChecks = 0;
    await page.route('**/api/v1/auth/session', async route => {
      sessionChecks += 1;
      const inactive = sessionChecks === 1;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            active: !inactive,
            secondsRemaining: inactive ? null : 1800,
            revokedReason: inactive ? 'IDLE_TIMEOUT' : null,
            enforcementActive: true,
          },
        }),
      });
    });

    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(password);
    await page.getByTestId('login-submit').click();

    await expect(page).toHaveURL(/\/login\?returnUrl=%2Fgarden/);
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(password);
    await page.getByTestId('login-submit').click();

    await expect(page).toHaveURL(/\/garden/);
    await expect.poll(() => sessionChecks).toBe(2);
  });
});
