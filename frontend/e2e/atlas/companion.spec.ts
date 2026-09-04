import { expect, test, type Page } from '@playwright/test';
import { AtlasPage } from './atlas.page';

/** Every node that survives an answer keeps the cell it had (C8/C9/C15). */
function nothingMoved(before: Record<string, string>, after: Record<string, string>): void {
  for (const [id, cell] of Object.entries(before)) {
    if (after[id] !== undefined) expect(after[id]).toBe(cell);
  }
}

/**
 * Write one settings key before the app boots (settings are read at bootstrap).
 * The group is MERGED, never replaced: `data` also carries which source and
 * scenario the mock garden is on, and replacing it would send the page live.
 */
async function seedSettings(page: Page, group: string, patch: Record<string, unknown>): Promise<void> {
  await page.addInitScript(
    ([g, p]) => {
      try {
        const raw = window.localStorage.getItem('atlas_settings');
        const cur = (raw ? JSON.parse(raw) : {}) as Record<string, Record<string, unknown>>;
        cur[g as string] = { ...(cur[g as string] ?? {}), ...(p as Record<string, unknown>) };
        window.localStorage.setItem('atlas_settings', JSON.stringify(cur));
      } catch {
        /* a private window simply gets the defaults */
      }
    },
    [group, patch] as const,
  );
}

/**
 * Travel to the companion through the "Navigate to" chrome — the same store.go()
 * a card click uses (C21), and always reachable whatever the viewport holds.
 */
async function openAsk(page: Page, atlas: AtlasPage): Promise<void> {
  await expect
    .poll(async () => {
      if ((await atlas.focusId()) === 'n-ask') return 'n-ask';
      const hop = page.locator('#navto-body .ch-btn', { hasText: 'Ask PlantPal' }).first();
      if (await hop.count()) await hop.click();
      return atlas.focusId();
    })
    .toBe('n-ask');
  await expect(atlas.node('n-ask')).toHaveAttribute('data-focus', 'true');
  await expect(atlas.node('n-ask')).toHaveAttribute('data-show', 'full');
}

/** Ask one question through the one in-world sheet. */
async function ask(page: Page, atlas: AtlasPage, question: string): Promise<void> {
  await atlas.stake('n-ask', 'Ask something').click();
  await expect(atlas.askSheet()).toBeVisible();
  await atlas.askSheet().locator('textarea').fill(question);
  await atlas.askSheet().locator('.stake', { hasText: 'Ask it' }).click();
  await expect(page.locator('[role="dialog"]')).toHaveCount(0);
}

/** How slowly the mock answers — read at bootstrap, so it is seeded before it. */
async function seedMockDelay(page: Page, ms: number): Promise<void> {
  await seedSettings(page, 'data', { mockLatencyMs: ms });
}

test.describe('the companion answers in its own card and moves nothing', () => {
  test('an answer lands as a feed row inside n-ask, and the camera never moves', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await openAsk(page, atlas);

    const before = await atlas.geography();
    const camera = await atlas.camera();
    const mock = await atlas.mockState();

    await ask(page, atlas, 'why are the low leaves going?');

    await expect(atlas.node('n-ask')).toContainText('why are the low leaves going?');
    await expect(atlas.live()).toContainText('The camera did not move.');
    // the answer is material of this card, and of no other
    expect(await atlas.feedRows('n-ask')).toBeGreaterThan(0);
    await expect(
      page.locator('rz-node:not(#n-ask)', { hasText: 'why are the low leaves going?' }),
    ).toHaveCount(0);
    nothingMoved(before, await atlas.geography());
    expect(await atlas.camera()).toBe(camera);
    expect(await atlas.focusId()).toBe('n-ask');
    await expect(page.locator('[role="dialog"]')).toHaveCount(0);
    // the companion reads the garden: it wrote nothing to it
    expect(await atlas.mockState()).toBe(mock);
  });

  test('an answer being written grows the row while the board stays byte-identical', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await seedMockDelay(page, 1500);
    await atlas.goto('garden');
    await openAsk(page, atlas);

    const before = await atlas.geography();
    const camera = await atlas.camera();
    await ask(page, atlas, 'how often should I water?');

    const streaming = atlas.node('n-ask').locator('[data-streaming] .feed__val');
    await expect(streaming).not.toBeEmpty();
    const first = (await streaming.textContent()) ?? '';
    const midGeography = await atlas.geography();
    const midCamera = await atlas.camera();
    await expect
      .poll(async () => ((await streaming.textContent()) ?? '').length)
      .toBeGreaterThan(first.length);
    expect(await atlas.geography()).toEqual(midGeography);
    expect(await atlas.camera()).toBe(midCamera);
    nothingMoved(before, await atlas.geography());
    expect(await atlas.camera()).toBe(camera);
    expect(await atlas.focusId()).toBe('n-ask');
  });

  test('moving on mid-answer keeps what arrived and asks for nothing more', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await seedMockDelay(page, 1500);
    await atlas.goto('garden');
    await openAsk(page, atlas);
    await ask(page, atlas, 'why are the low leaves going?');
    await expect(atlas.node('n-ask').locator('[data-streaming] .feed__val')).not.toBeEmpty();

    await page.locator('#navto-body .ch-btn', { hasText: 'My garden' }).first().click();
    await expect(atlas.node('n-garden')).toHaveAttribute('data-focus', 'true');
    await expect(atlas.live()).toContainText('stopped part-way');
    await openAsk(page, atlas);
    // the partial is kept as a turn — it is not dropped, and nothing is retried
    await expect(atlas.node('n-ask')).toContainText('why are the low leaves going?');
    await expect(atlas.node('n-ask').locator('[data-streaming]')).toBeHidden();
    // and it is kept as what it is: truncated, with the ask offered again
    await page.reload();
    await page.locator('rz-node#n-treatments').waitFor();
    await openAsk(page, atlas);
    await expect(atlas.node('n-ask')).toContainText('stopped part-way');
    await expect(atlas.stake('n-ask', 'Ask it again')).toBeVisible();
  });

  test('reading the whole thread widens the same feed and opens nothing', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await seedMockDelay(page, 0);
    await atlas.goto('garden');
    await openAsk(page, atlas);
    for (const q of ['who are you?', 'how often should I water?', 'why the yellow leaves?', 'what is due?']) {
      await ask(page, atlas, q);
    }
    // the fold and its stake are assembled material: this body was built with an
    // empty thread, so it carries no toggle — the next board is where it stands
    await page.reload();
    await page.locator('rz-node#n-treatments').waitFor();
    await openAsk(page, atlas);

    const before = await atlas.geography();
    const camera = await atlas.camera();
    const folded = await atlas.feedRows('n-ask');
    await atlas.stake('n-ask', 'Read the whole thread').click();
    await expect
      .poll(async () => atlas.feedRows('n-ask'))
      .toBeGreaterThan(folded);
    await expect(page.locator('[role="dialog"]')).toHaveCount(0);
    expect(await atlas.camera()).toBe(camera);
    expect(await atlas.focusId()).toBe('n-ask');
    nothingMoved(before, await atlas.geography());
  });

  test('day zero prints a real zero and invents no plant', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('day-zero');
    await openAsk(page, atlas);
    await expect(atlas.node('n-ask')).toContainText('Nothing asked yet.');
    await ask(page, atlas, 'what should I do first?');
    await expect(atlas.node('n-ask')).toContainText('Your garden is empty here');
    await expect(atlas.node('n-ask')).not.toContainText('Office Fig');
  });

  test('offline holds the question and sends nothing', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await openAsk(page, atlas);
    await atlas.probe('offline').click();
    // the mock answers in memory, so the network log cannot prove this: count the
    // asks the backend itself served instead
    const asked = await atlas.mockAsks();

    await atlas.stake('n-ask', 'Ask something').click();

    await expect(atlas.live()).toContainText('queued');
    await expect(page.locator('[role="dialog"]')).toHaveCount(0);
    expect(await atlas.mockAsks()).toBe(asked);
    expect(atlas.apiRequests.length).toBe(0);
    await atlas.probe('offline').click();
  });

  test('a chat family that refuses is worn by n-ask alone', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('outage');
    await openAsk(page, atlas);
    const before = await atlas.geography();
    const camera = await atlas.camera();

    await ask(page, atlas, 'why are the low leaves going?');

    await expect(atlas.node('n-ask').locator('[data-chat-failure]')).toContainText(
      'cannot reach its thinking',
    );
    await expect(atlas.node('n-ask')).not.toContainText('Ollama');
    // per-node material: no overlay, no toast, and never a second dialog
    await expect(page.locator('[role="dialog"]')).toHaveCount(0);
    await expect(
      page.locator('rz-node:not(#n-ask)', { hasText: 'cannot reach its thinking' }),
    ).toHaveCount(0);
    nothingMoved(before, await atlas.geography());
    expect(await atlas.camera()).toBe(camera);
    expect(await atlas.focusId()).toBe('n-ask');
  });

  test('mock mode asks nothing of the network', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await openAsk(page, atlas);
    await ask(page, atlas, 'who are you?');
    await expect(atlas.node('n-ask')).toContainText('I am PlantPal');
    expect(atlas.apiRequests).toEqual([]);
  });
});
