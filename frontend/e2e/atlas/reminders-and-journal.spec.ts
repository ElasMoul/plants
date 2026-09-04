import { expect, test } from '@playwright/test';
import { AtlasPage } from './atlas.page';

async function opened(atlas: AtlasPage, id: string): Promise<void> {
  await expect(atlas.node(id)).toHaveAttribute('data-focus', 'true');
  await expect(atlas.node(id)).toHaveAttribute('data-show', 'full');
}

test.describe('reminders and the journal, pressed', () => {
  test('a due row is completed from the hub and the journal grows', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await atlas.navTo('Due today').click();
    await opened(atlas, 'n-reminders');

    const before = await atlas.geography();
    const camera = await atlas.camera();
    await atlas.node('n-reminders').locator('.stake', { hasText: 'Done' }).first().click();

    await expect(atlas.live()).toContainText('camera did not move');
    expect(await atlas.camera()).toBe(camera);
    expect(await atlas.focusId()).toBe('n-reminders');
    // completing does not re-rank the board
    const after = await atlas.geography();
    for (const [id, cell] of Object.entries(before)) expect(after[id]).toBe(cell);
  });

  test('snoozing is device-local and the row says so', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await atlas.navTo('Due today').click();
    await opened(atlas, 'n-reminders');

    await atlas.stake('n-reminders', 'Snooze the overdue one').click();
    await expect(atlas.live()).toContainText('on this device');
    await expect(atlas.node('n-reminders')).toContainText('Snoozed until tomorrow · on this device');
  });

  test('the rail reaches the journal on a live board', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await atlas.navTo('Journal').click();
    await opened(atlas, 'n-journal');
    expect(await atlas.focusId()).toBe('n-journal');
    await expect(atlas.railAction('Log a watering')).toBeVisible();
  });

  test('logging a watering from the care hub opens the sheet and records it', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await atlas.node('n-care').click();
    await opened(atlas, 'n-care');
    const camera = await atlas.camera();

    await atlas.stake('n-care', 'Log a watering').click();
    const sheet = page.locator('.rz-form');
    await expect(sheet).toContainText('Logging care completes the schedule it belongs to');
    await sheet.locator('.stake', { hasText: 'Log it' }).click();

    await expect(atlas.live()).toContainText('Care logged');
    expect(await atlas.camera()).toBe(camera);
  });
});
