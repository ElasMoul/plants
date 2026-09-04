import { expect, Page, test } from '@playwright/test';
import { AtlasPage } from './atlas.page';

/**
 * Degradation as the constitution defines it: material inside the node it belongs
 * to, never a banner over the world, and never a change of geography (C22-C25).
 * Every probe toggle and every scenario is checked against the board it came from.
 */

/** Both halves of the geography: the cell it was given and the pixel it sits at. */
async function place(atlas: AtlasPage): Promise<{ cells: Record<string, string>; px: Record<string, string> }> {
  return { cells: await atlas.geography(), px: await atlas.positions() };
}

/**
 * The geometry comparison RETRIES: a probe toggle re-measures clearance, so a
 * one-shot read can sample the board mid-settle. The assertion is unchanged in
 * strength — only the sampling is allowed a second look.
 */
async function samePlace(
  atlas: AtlasPage,
  before: { cells: Record<string, string>; px: Record<string, string> },
): Promise<void> {
  await expect.poll(() => atlas.geography()).toEqual(before.cells);
  await expect.poll(() => atlas.positions()).toEqual(before.px);
}

/** Write a settings key before the app boots (settings are read at bootstrap). */
async function seedSettings(page: Page, patch: Record<string, unknown>): Promise<void> {
  await page.addInitScript(p => {
    try {
      const raw = window.localStorage.getItem('atlas_settings');
      const cur = raw ? JSON.parse(raw) : {};
      window.localStorage.setItem('atlas_settings', JSON.stringify({ ...cur, ...(p as object) }));
    } catch {
      /* a private window simply gets the defaults */
    }
  }, patch);
}

test.describe('degradation is material, never geometry', () => {
  test('the slow probe dresses each node in its own waiting material', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    const before = await place(atlas);
    const camera = await atlas.camera();

    await atlas.probe('slow').click();
    await expect(page.locator('body')).toHaveAttribute('data-speed', 'slow');

    for (const id of ['n-garden', 'n-treatments', 'n-reminders']) {
      const pending = atlas.node(id).locator('.pending');
      await expect(pending).toBeVisible();
      await expect(pending).toContainText('Still arriving');
    }
    // the wait is skeleton material, never a spinner and never the word
    expect(await page.locator('[class*="spinner"]').count()).toBe(0);
    expect(await page.locator('#plane').innerText()).not.toMatch(/loading/i);

    // the cell a node was given is the geography, and it is untouched; the pixel
    // it settles at follows the card's measured height, which the waiting material
    // legitimately changes (clearance is measured, never guessed)
    await expect.poll(() => atlas.geography()).toEqual(before.cells);
    expect(await atlas.camera()).toBe(camera);

    await atlas.probe('slow').click();
    await expect(page.locator('body')).toHaveAttribute('data-speed', 'normal');
    await samePlace(atlas, before);
  });

  test('the offline probe stales every node in its own words and writes nothing', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await atlas.node('n-plant-1').click();
    await expect(atlas.node('n-plant-1')).toHaveAttribute('data-show', 'full');
    const before = await place(atlas);
    const camera = await atlas.camera();
    const state = await atlas.mockState();

    await atlas.probe('offline').click();
    await expect(page.locator('body')).toHaveAttribute('data-net', 'offline');
    await expect(page.locator('#offline-bar')).toBeVisible();

    // every node says how stale IT is — never one banner claiming it for the world
    const lines = await page.locator('.staleness:visible').allInnerTexts();
    expect(lines.length).toBeGreaterThanOrEqual(2);
    expect(new Set(lines.map(l => l.trim())).size).toBeGreaterThanOrEqual(2);

    // the stakes themselves are dimmed while offline — the visual half of the
    // contract, applied per node by the stylesheet rather than by a global veil
    const stake = atlas.stake('n-plant-1', 'Water plant');
    const dimmed = await stake.evaluate(el => getComputedStyle(el).opacity);
    expect(Number(dimmed)).toBeLessThan(1);

    // a press while offline is queued in words, and the backend is untouched
    await atlas.stake('n-plant-1', 'Water plant').click();
    await expect(atlas.live()).toContainText('queued');
    expect(await atlas.mockState()).toBe(state);

    await samePlace(atlas, before);
    expect(await atlas.camera()).toBe(camera);

    await atlas.probe('offline').click();
    await expect(page.locator('#offline-bar')).toBeHidden();
    // and the dimming is lifted with it
    await expect.poll(() => stake.evaluate(el => Number(getComputedStyle(el).opacity))).toBe(1);
    await samePlace(atlas, before);
  });

  test('a hop under reduced motion lands, and moves no cell', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    const before = await place(atlas);

    await atlas.probe('reduced').click();
    await expect(page.locator('body')).toHaveAttribute('data-motion', 'reduced');
    await samePlace(atlas, before);

    await atlas.navTo('Due today').click();
    await expect(atlas.node('n-reminders')).toHaveAttribute('data-focus', 'true');
    await expect(atlas.node('n-reminders')).toHaveAttribute('data-show', 'full');
    // travelling changes rank and size, never the cell a node was given
    await expect.poll(() => atlas.geography()).toEqual(before.cells);
  });

  test('an outage fails only the families that failed, in the same cells', async ({ page }) => {
    const atlas = new AtlasPage(page);
    const garden = new AtlasPage(page);
    await garden.goto('garden');
    const gardenCells = await garden.geography();

    await atlas.goto('outage');
    const outageCells = await atlas.geography();

    for (const [id, cell] of Object.entries(outageCells)) {
      if (gardenCells[id] !== undefined) expect({ id, cell }).toEqual({ id, cell: gardenCells[id] });
    }

    for (const id of ['n-reminders', 'n-treatment-301']) {
      const node = atlas.node(id);
      await expect(node).toHaveAttribute('data-recap', 'Did not come back');
      await expect(node.locator('.state--error')).toHaveCount(1);
      // the fact carries a time, and a way forward lives in the node itself
      await expect(node.locator('.state--error')).toContainText(/\d{2}:\d{2}/);
      await expect(atlas.stake(id, 'Fetch this region')).toHaveCount(1);
      await expect(node).not.toContainText('Something went wrong');
    }

    // everything else is live, and the camera never left where it booted
    await expect(atlas.node('n-garden')).toHaveAttribute('data-recap', /plants/);
    await expect(atlas.node('n-species')).not.toHaveAttribute('data-recap', 'Did not come back');
    expect(await atlas.focusId()).toBe('n-garden');
    expect(await page.locator('[role="dialog"]').count()).toBe(0);
  });

  test('a twelve-second garden waits in skeletons, then arrives without moving', async ({ page }) => {
    test.setTimeout(120_000);
    await seedSettings(page, { data: { source: 'mock', mockScenario: 'garden', mockLatencyMs: 12000 } });
    const atlas = new AtlasPage(page);
    await page.goto('/?mock=garden');
    await page.locator('rz-node[data-focus="true"]').first().waitFor();

    // the first ten seconds: real skeleton material, no spinner, no "loading"
    const plane = page.locator('#plane');
    expect(await plane.innerText()).not.toMatch(/loading/i);
    expect(await plane.innerText()).not.toMatch(/please wait/i);
    expect(await page.locator('[class*="spinner"]').count()).toBe(0);
    expect(await page.locator('.n__skel').count()).toBeGreaterThan(0);

    // then the mock garden arrives, and the board it settles into stays put
    await page.locator('rz-node#n-treatments').waitFor({ timeout: 60000 });
    const arrived = await atlas.geography();
    await page.waitForTimeout(1500);
    expect(await atlas.geography()).toEqual(arrived);
    expect(await atlas.focusId()).toBe('n-garden');
    expect(await page.locator('[class*="spinner"]').count()).toBe(0);
  });

  test('day zero is zeros with room in it, and invites rather than samples', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('day-zero');

    await expect(atlas.node('n-garden')).toHaveAttribute('data-recap', /^0 plants/);
    await atlas.node('n-garden').click();
    await expect(atlas.node('n-garden')).toHaveAttribute('data-show', 'full');
    // zero is written as zero, and it offers a way to begin rather than a sample
    await expect(atlas.node('n-garden')).toContainText('No plants yet — add the first one.');
    await expect(atlas.stake('n-garden', 'Add a plant')).toBeVisible();
    // a family that is genuinely empty wears the empty material, not the unknown one
    await expect(atlas.node('n-reminders')).toHaveAttribute('data-recap', 'Nothing due today');
    await expect(atlas.node('n-reminders')).not.toHaveAttribute('data-unknown', 'true');

    const text = await page.locator('#plane').innerText();
    for (const sample of ['Office Fig', 'Studio Fig', 'Monstera', 'Terrace Lemon', 'Root rot', 'Spider mites', 'Mealybugs']) {
      expect(text).not.toContain(sample);
    }
    expect(atlas.apiRequests).toEqual([]);
  });
});
