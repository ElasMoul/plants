import { expect, test } from '@playwright/test';
import { AtlasPage } from './atlas.page';

/** A card shows its body only once it IS the focus and its travel has settled (C17). */
async function opened(atlas: AtlasPage, id: string): Promise<void> {
  await expect(atlas.node(id)).toHaveAttribute('data-focus', 'true');
  await expect(atlas.node(id)).toHaveAttribute('data-show', 'full');
}

test.describe('round 3 on the running mock garden', () => {
  test('Today counts the day, and every line is a door', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    const before = await atlas.geography();

    await atlas.node('n-today').click();
    await opened(atlas, 'n-today');
    const today = atlas.node('n-today');

    // one due today, two already late — the server's own buckets
    await expect(today).toHaveAttribute('data-recap', '1 due · 2 overdue');
    const text = await today.innerText();
    expect(text).toContain('Office Fig · 1 day overdue');
    expect(text).toContain('Terrace Lemon · 4 days overdue');
    expect(text).toContain('Studio Fig · today');
    expect(text).toMatch(/Root rot · day \d+ of \d+/);

    // a count is not a feed: no stake anywhere on this card
    expect(await today.locator('.stake').count()).toBe(0);

    // and every name travels along the veins
    await today.locator('[data-goto="n-plant-1"]').first().click();
    await opened(atlas, 'n-plant-1');
    expect(await atlas.geography()).toEqual(before);
    expect(atlas.apiRequests).toEqual([]);
  });

  test('the bell announces the distance before it travels, and Mark all read empties it', async ({
    page,
  }) => {
    const atlas = new AtlasPage(page);
    // the bell is deliberately silent inside quiet hours, and this walk must not
    // depend on the hour the suite happens to run at
    await page.addInitScript(() => {
      localStorage.setItem('atlas_settings', JSON.stringify({ profile: { quietHours: 'off' } }));
      // the world says its own sentence the moment travel begins, so the arrival's
      // sentence is transient: every announcement is recorded as it is made
      const spoken: string[] = [];
      (window as unknown as { __spoken: string[] }).__spoken = spoken;
      addEventListener('DOMContentLoaded', () => {
        // #live is rendered by the app, so the whole document is watched and the
        // region is read on every change
        new MutationObserver(() => {
          const t = (document.querySelector('#live')?.textContent ?? '').trim();
          if (t && spoken[spoken.length - 1] !== t) spoken.push(t);
        }).observe(document.documentElement, {
          childList: true,
          characterData: true,
          subtree: true,
        });
      });
    });
    await atlas.goto('garden');
    const before = await atlas.geography();

    const bell = page.locator('#bell');
    await expect(bell.locator('.ch-count')).toHaveText('3');

    await bell.click();
    await opened(atlas, 'n-reminders');
    const spoken = await page.evaluate(() => (window as unknown as { __spoken: string[] }).__spoken);
    const arrival = spoken.find(t => t.includes('3 due,'));
    expect(arrival).toContain('nothing on the way opens');
    // the count and the distance were spoken BEFORE the travel sentence (C21)
    expect(spoken.indexOf(arrival!)).toBeLessThan(spoken.findIndex(t => t.startsWith('Travelled')));
    expect(await atlas.geography()).toEqual(before);

    // the notifications panel reads this device, and refuses what PlantPal cannot do
    const reminders = await atlas.node('n-reminders').innerText();
    expect(reminders).toContain('Off · enable in Settings · Notifications');
    expect(reminders).toContain('Not offered by PlantPal');
    expect(reminders).toContain('Unread');

    await atlas.stake('n-reminders', 'Mark all read').click();
    await expect(atlas.live()).toHaveText('Marked read on this device. Nothing moved.');
    await expect(bell.locator('.ch-count')).toHaveCount(0);
    expect(await atlas.geography()).toEqual(before);
  });

  test('the account is what PlantPal holds, and signing out is honest in the mock garden', async ({
    page,
  }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');

    await atlas.node('n-account').click();
    await opened(atlas, 'n-account');
    const account = await atlas.node('n-account').innerText();
    expect(account).toContain('sam@example.org');
    // the mock seed mirrors the server's current defaults (Claude for both menus)
    expect(account).toContain('Claude');
    expect(account).toContain('mock session');

    await atlas.stake('n-account', 'Sign out here').click();
    await expect(atlas.live()).toHaveText('This is the mock garden — there is no session to end.');
    // it really is still the mock garden
    await expect(atlas.accountChip()).toHaveText('Sam');
  });

  test('an outage leaves Today wearing its own failure, with the count it can still make', async ({
    page,
  }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('outage');

    await atlas.node('n-today').click();
    await opened(atlas, 'n-today');
    const today = atlas.node('n-today');
    await expect(today).toHaveAttribute('data-recap', 'Did not come back');
    const text = await today.innerText();
    expect(text).toContain('did not come back');
    // the panel is still there, saying where the count would have come from —
    // in this scenario the reminders did not answer either, so there is nothing
    // left to count and the card says that rather than printing a false zero
    expect(text).toContain("Today's summary");
    expect(text).toContain('the dashboard did not come back');
    await expect(atlas.stake('n-today', 'Count again')).toBeVisible();
    await expect(atlas.node('n-reminders')).toHaveAttribute('data-recap', 'Did not come back');

    // the geography never degrades: the rest of the board is still live
    await expect(atlas.node('n-garden')).toHaveAttribute('data-recap', /plants/);
  });
});
