import { expect, test } from '@playwright/test';
import { AtlasPage } from './atlas.page';

test.describe('the atlas boots the mock garden', () => {
  test('boots the mock garden with no backend', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');

    expect(await atlas.focusId()).toBe('n-garden');

    const ids = await atlas.nodeIds();
    for (const id of [
      'n-account',
      'n-platform',
      'n-ident',
      'n-species',
      'n-reminders',
      'n-care',
      'n-today',
      'n-treatments',
      'n-ask',
    ]) {
      expect(ids).toContain(id);
    }

    // The mock backend answers inside the page: nothing reaches the network.
    expect(atlas.apiRequests).toEqual([]);
    await expect(atlas.topbarSub()).toContainText('mock garden');
    await expect(atlas.accountChip()).toHaveText('Sam');
    expect(atlas.consoleErrors).toEqual([]);
  });

  test('day zero prints zeros and no sample records', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('day-zero');

    await expect(atlas.node('n-garden')).toHaveAttribute('data-recap', /^0 plants/);

    const ids = await atlas.nodeIds();
    expect(ids.filter(id => /^n-(plant|species|scan|treatment|log)-/.test(id))).toEqual([]);

    const text = await page.locator('#plane').innerText();
    for (const sample of ['Office Fig', 'Monstera', 'Terrace Lemon', 'Root rot', 'Spider mites']) {
      expect(text).not.toContain(sample);
    }
  });

  test('without the flag the fixture board and Sign in are shown', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await page.goto('/?mock=off');
    await page.locator('rz-node[data-focus="true"]').first().waitFor();

    await expect(atlas.topbarSub()).toHaveText('Botanical Network');
    await expect(atlas.accountChip()).toHaveText('Sign in');
    expect(await atlas.focusId()).toBe('n-fig');
  });
});
