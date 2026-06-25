import { test, expect } from '@playwright/test';
import { AuthPage } from '../pages/auth.page';
import { stubAuth, stubPlants, stubAiCalls } from '../fixtures/api-stubs';

test.describe('Chat', () => {
  test.beforeEach(async ({ page }) => {
    await stubAuth(page);
    await stubPlants(page);
    await stubAiCalls(page);
    const auth = new AuthPage(page);
    await auth.navigateToLogin();
    await auth.login('test@plantpal.test', 'password123');
  });

  test('send a chat message and receive a response', async ({ page }) => {
    await page.goto('/chat');

    await page.getByTestId('chat-input').fill('How often should I water my Monstera?');
    await page.getByTestId('chat-send-btn').click();

    // The stubbed stream returns "Your plant looks healthy!"
    await expect(page.getByTestId('chat-messages')).toContainText('Your plant looks healthy!', { timeout: 10000 });
  });
});
