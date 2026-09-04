import { expect, Page, test } from '@playwright/test';
import { AtlasPage } from './atlas.page';

/**
 * Layout determinism (C7/C8) and "nothing unmounts" (C1/C4), walked on the
 * running mock garden: the same sources draw the same board, an insertion takes a
 * free cell and moves nothing, and travelling changes what is legible — never what
 * is there.
 */
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

test.describe('the board is a formula, not a memory', () => {
  test('two fresh loads of the mock garden draw identical cells', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    const first = await atlas.geography();
    const firstIds = (await atlas.nodeIds()).sort();

    await page.reload();
    await page.locator('rz-node#n-treatments').waitFor();
    await page.locator('rz-node[data-focus="true"]').first().waitFor();

    expect(await atlas.geography()).toEqual(first);
    expect((await atlas.nodeIds()).sort()).toEqual(firstIds);
    expect(await atlas.focusId()).toBe('n-garden');
  });

  test('a created reminder adds no node and moves no cell', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await atlas.node('n-reminders').click();
    await expect(atlas.node('n-reminders')).toHaveAttribute('data-show', 'full');

    const before = await atlas.geography();
    const px = await atlas.positions();
    const ids = (await atlas.nodeIds()).sort();
    const camera = await atlas.camera();

    await atlas.stake('n-reminders', 'Add a reminder').click();
    const sheet = page.locator('.rz-form');
    await expect(sheet).toBeVisible();
    await sheet.locator('select').first().selectOption({ index: 1 });
    await sheet.locator('.stake', { hasText: 'Set the reminder' }).click();

    await expect(atlas.live()).toContainText('The reminder is set.');

    // a reminder is a ROW inside its hub — deliberately not a node of its own, so
    // creating one cannot change the membership of any layer (C8/C9)
    expect((await atlas.nodeIds()).sort()).toEqual(ids);
    expect(await atlas.geography()).toEqual(before);
    expect(await atlas.positions()).toEqual(px);
    expect(await atlas.camera()).toBe(camera);
    expect(await atlas.focusId()).toBe('n-reminders');
  });

  test('a created plant takes a free cell and every prior cell is kept', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    const before = await atlas.geography();
    const camera = await atlas.camera();

    await atlas.railAction('Add new plant').click();
    const sheet = page.locator('.rz-form');
    await expect(sheet).toBeVisible();
    await sheet.locator('input').first().fill('Determinism Fern');
    await sheet.locator('.stake', { hasText: 'Plant it' }).click();

    await expect(atlas.live()).toContainText('nothing else moves');

    const after = await atlas.geography();
    for (const [id, cell] of Object.entries(before)) {
      if (after[id] !== undefined) expect({ id, cell: after[id] }).toEqual({ id, cell });
    }
    expect(await atlas.camera()).toBe(camera);
    expect(await atlas.focusId()).toBe('n-garden');
  });

  test('ten hops leave the node set identical, one card focused, and name each place', async ({ page }) => {
    // announceMs 0 keeps #live standing, so each destination can be read back
    await seedSettings(page, { general: { announceMs: 0 } });
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    const ids = (await atlas.nodeIds()).sort();
    const cells = await atlas.geography();

    // hop through the "Navigate to" chrome: always reachable, and always the same
    // store.go() a card click uses (C21)
    const visited: string[] = [];
    for (let i = 0; i < 10; i++) {
      const neighbours = page.locator('#navto-body .ch-btn');
      await expect(neighbours.first()).toBeVisible();
      await neighbours.nth(i % (await neighbours.count())).click();

      // the world named where it went — the announcement and the focus agree
      await expect
        .poll(async () => {
          const focus = await atlas.focusId();
          if (!focus) return 'no focus';
          const name = (await atlas.node(focus).getAttribute('data-name')) ?? '';
          const said = (await atlas.live().innerText()).trim();
          return said.startsWith(`Travelled to ${name}.`) ? 'named' : `${said} ≠ ${name}`;
        })
        .toBe('named');

      const focus = await atlas.focusId();
      visited.push(focus as string);
      expect(await page.locator('rz-node[data-focus="true"]').count()).toBe(1);
    }
    expect(visited.length).toBe(10);

    expect((await atlas.nodeIds()).sort()).toEqual(ids);
    expect(await atlas.geography()).toEqual(cells);
    expect(atlas.apiRequests).toEqual([]);
    expect(atlas.consoleErrors).toEqual([]);
  });
});
