import { expect, test } from '@playwright/test';
import { AtlasPage } from './atlas.page';

async function opened(atlas: AtlasPage, id: string): Promise<void> {
  await expect(atlas.node(id)).toHaveAttribute('data-focus', 'true');
  await expect(atlas.node(id)).toHaveAttribute('data-show', 'full');
}

/** Every node that survives a mutation keeps the cell it had (C8/C9). */
function nothingMoved(before: Record<string, string>, after: Record<string, string>): void {
  for (const [id, cell] of Object.entries(before)) {
    if (after[id] !== undefined) expect(after[id]).toBe(cell);
  }
}

/** A course is a sequence with an end: a step is a mutation, never an exit. */
test.describe('running a treatment course', () => {
  test('marking a step done moves the rules down one row and keeps the board still', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await atlas.node('n-treatment-301').click();
    await opened(atlas, 'n-treatment-301');

    const course = atlas.node('n-treatment-301');
    await expect(course.locator('[data-course] .row[data-done="true"]')).toHaveCount(1);
    const before = await atlas.geography();
    const camera = await atlas.camera();

    await atlas.stake('n-treatment-301', 'Mark step 2 as done').click();

    await expect(course.locator('[data-course] .row[data-done="true"]')).toHaveCount(2);
    await expect(course).toHaveAttribute('data-recap', /2 of 4 done/);
    nothingMoved(before, await atlas.geography());
    expect(await atlas.camera()).toBe(camera);
    expect(await atlas.focusId()).toBe('n-treatment-301');
  });

  test('pausing is device-local and says so; resuming gives the stakes back', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await atlas.node('n-treatment-301').click();
    await opened(atlas, 'n-treatment-301');

    await atlas.stake('n-treatment-301', 'Pause this course').click();
    await expect(atlas.live()).toContainText('on this device');
    await expect(atlas.node('n-treatment-301')).toHaveAttribute('data-recap', /paused/);

    await atlas.stake('n-treatment-301', 'Resume this course').click();
    await expect(atlas.live()).toContainText('Resumed');
    await expect(atlas.node('n-treatment-301')).not.toHaveAttribute('data-recap', /paused/);
  });

  test('finishing a course leaves it on the board and clears the plant it was treating', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await atlas.node('n-treatment-301').click();
    await opened(atlas, 'n-treatment-301');
    const camera = await atlas.camera();

    await atlas.stake('n-treatment-301', 'Finish this course').click();

    await expect(atlas.live()).toContainText('part of its story');
    await expect(atlas.node('n-treatment-301')).toBeVisible();
    expect(await atlas.focusId()).toBe('n-treatment-301');
    expect(await atlas.camera()).toBe(camera);

    await atlas.node('n-plant-1').click();
    await opened(atlas, 'n-plant-1');
    await expect(atlas.node('n-plant-1').locator('[data-vitals]')).toContainText('None running');
  });
});
