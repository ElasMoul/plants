import { expect, test } from '@playwright/test';
import { AtlasPage } from './atlas.page';

async function opened(atlas: AtlasPage, id: string): Promise<void> {
  await expect(atlas.node(id)).toHaveAttribute('data-focus', 'true');
  await expect(atlas.node(id)).toHaveAttribute('data-show', 'full');
}

/** Every node that survives a mutation keeps the cell it had (C8/C9). */
function nothingMoved(
  before: Record<string, string>,
  after: Record<string, string>,
): void {
  for (const [id, cell] of Object.entries(before)) {
    if (after[id] !== undefined) expect(after[id]).toBe(cell);
  }
}

/**
 * The care loop as a set of presses: every stake mutates the in-memory backend,
 * the board shows the result on the next load, and the camera never moves (C15/C16).
 */
test.describe('care presses change data and nothing else', () => {
  test('watering a plant logs care and leaves the geography and the camera alone', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await atlas.node('n-plant-1').click();
    await opened(atlas, 'n-plant-1');

    const vitals = atlas.node('n-plant-1').locator('[data-vitals]');
    await expect(vitals).toContainText('Overdue');
    const before = await atlas.geography();
    const camera = await atlas.camera();

    await atlas.stake('n-plant-1', 'Water plant').click();

    await expect(atlas.live()).toContainText('The camera did not move.');
    // the schedule moved forward and the journal knows it happened today
    await expect(vitals).not.toContainText('Overdue');
    await expect(vitals).toContainText('Today');
    nothingMoved(before, await atlas.geography());
    expect(await atlas.camera()).toBe(camera);
    expect(await atlas.focusId()).toBe('n-plant-1');
  });

  test('the rail sets a first schedule: a real row, and no card moves', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await atlas.node('n-plant-5').click();
    await opened(atlas, 'n-plant-5');
    // this plant has pruning but no watering — the rail offers to make one
    await expect(atlas.railAction('Set a watering schedule')).toBeVisible();

    const before = await atlas.geography();
    const camera = await atlas.camera();
    await atlas.railAction('Set a watering schedule').click();
    const sheet = page.locator('.rz-form');
    await expect(sheet).toContainText('A reminder belongs to one plant and one kind of care.');
    await sheet.locator('.stake', { hasText: 'Set the reminder' }).click();

    await expect(atlas.live()).toContainText('reminder is set');
    await expect(atlas.node('n-plant-5').locator('[data-vitals]')).not.toContainText('No schedule yet');
    nothingMoved(before, await atlas.geography());
    expect(await atlas.camera()).toBe(camera);
  });

  test('offline, a press is queued and no call is made', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await atlas.node('n-plant-1').click();
    await opened(atlas, 'n-plant-1');
    await atlas.probe('offline').click();
    const calls = atlas.apiRequests.length;
    await atlas.stake('n-plant-1', 'Water plant').click();
    await expect(atlas.live()).toContainText('queued');
    expect(atlas.apiRequests.length).toBe(calls);
  });
});
