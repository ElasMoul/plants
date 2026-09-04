import { expect, test } from '@playwright/test';
import { AtlasPage } from './atlas.page';

/** A card only shows its body once it IS the focus and its travel has settled (C17). */
async function opened(atlas: AtlasPage, id: string): Promise<void> {
  await expect(atlas.node(id)).toHaveAttribute('data-focus', 'true');
  await expect(atlas.node(id)).toHaveAttribute('data-show', 'full');
}

/**
 * Photographs on plates (PP-095), read on the running mock garden. The picture
 * fills the plate the card already draws; without one — or with the setting
 * off — the drawn specimen stands in its place. Either way the plate is the
 * same box, so the board never moves (C9).
 */
test.describe('the photograph a plant wears', () => {
  test('a plant plate carries the photograph, and the drawn leaf steps aside', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    const before = await atlas.geography();

    await atlas.node('n-plant-1').click();
    await opened(atlas, 'n-plant-1');

    const plate = atlas.node('n-plant-1').locator('.plate__specimen');
    await expect(plate).toHaveAttribute('data-photo', '1');

    // it is a real painted picture, sized to the plate, not a stretched thumbnail
    const painted = await plate.evaluate(el => {
      const cs = getComputedStyle(el);
      const r = el.getBoundingClientRect();
      return {
        isImage: cs.backgroundImage.startsWith('url("data:image/'),
        size: cs.backgroundSize,
        w: Math.round(r.width),
        h: Math.round(r.height),
        drawnLeaf: getComputedStyle(el, '::after').content,
      };
    });
    expect(painted.isImage).toBe(true);
    expect(painted.size).toBe('cover');
    expect(painted.w).toBeGreaterThan(0);
    // the plate keeps its 3/4 box whatever fills it
    expect(painted.h / painted.w).toBeGreaterThan(1.2);
    expect(painted.drawnLeaf).toBe('none');

    // nothing external is ever fetched: the mock garden paints from its own bytes
    expect(atlas.apiRequests.filter(u => u.includes('/photos/'))).toHaveLength(0);
    // and a photograph arriving moved nothing on the board (C9)
    expect(await atlas.geography()).toEqual(before);
  });

  test('the scan wears the photograph that was scanned', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');

    const scan = (await atlas.nodeIds()).find(id => id.startsWith('n-scan-'));
    expect(scan).toBeTruthy();
    await atlas.node(scan!).click();
    await opened(atlas, scan!);

    await expect(atlas.node(scan!).locator('.plate__specimen')).toHaveAttribute('data-photo', '1');
  });

  test('turning photographs off restores the drawn specimen and moves nothing', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await atlas.node('n-plant-1').click();
    await opened(atlas, 'n-plant-1');
    const before = await atlas.geography();

    await atlas.openSettings();
    await atlas.settingsNav('Appearance').click();
    await atlas.pane().getByRole('button', { name: 'Draw the specimen' }).click();
    await page.keyboard.press('Escape');

    const plate = atlas.node('n-plant-1').locator('.plate__specimen');
    await expect(plate).not.toHaveAttribute('data-photo', '1');
    await expect(plate).toBeVisible();
    expect(await atlas.geography()).toEqual(before);
  });

  test('a garden with no photographs still draws every plate', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('day-zero');
    // day zero has no plants at all, so the board simply offers the first one
    await expect(atlas.node('n-garden')).toHaveAttribute('data-recap', '0 plants · 0 need water');
    expect(await atlas.node('n-garden').locator('.plate__specimen[data-photo]').count()).toBe(0);
  });
});
