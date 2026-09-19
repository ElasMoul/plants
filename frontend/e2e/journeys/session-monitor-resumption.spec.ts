import { expect, test } from '@playwright/test';

test.describe('Session monitor resumption', () => {
  test('evicts and re-authenticates in one SPA visit, then starts monitoring the fresh session', async ({ page }) => {
    const email = 'session-monitor@plantpal.test';
    const password = 'correct-password';
    const syntheticToken = ['eyJhbGciOiJub25lIn0', 'eyJleHAiOjQxMDI0NDQ4MDB9', 'signature'].join('.');
    // This is deliberately a browser-state test. The response supplies a valid
    // client-decodable token; real auth/session API coverage is kept separate.
    let sessionChecks = 0;
    await page.route('**/api/v1/**', async route => {
      const path = new URL(route.request().url()).pathname;
      if (path.endsWith('/auth/login')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: {
              token: syntheticToken,
              user: { id: 1, email, firstName: 'Session', lastName: 'Monitor' },
            },
          }),
        });
        return;
      }

      if (path.endsWith('/auth/session')) {
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
        return;
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: { content: [], totalElements: 0, totalPages: 0, number: 0 } }),
      });
    });

    await page.goto('/garden?from=release-readiness');
    await expect(page).toHaveURL(/\/login\?returnUrl=%2Fgarden%3Ffrom%3Drelease-readiness/);
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(password);
    await page.getByTestId('login-submit').click();

    await expect.poll(() => sessionChecks).toBe(1);
    await expect(page).toHaveURL(/\/login\?returnUrl=%2Fgarden%3Ffrom%3Drelease-readiness/);
    await expect(page.getByTestId('login-submit')).toBeEnabled();
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(password);
    await page.getByTestId('login-submit').click();

    await expect(page).toHaveURL(/\/garden\?from=release-readiness/);
    await expect.poll(() => sessionChecks).toBe(2);
  });
});
