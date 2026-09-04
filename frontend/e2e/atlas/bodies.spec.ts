import { expect, test } from '@playwright/test';
import { AtlasPage } from './atlas.page';

/** A card only shows its body once it IS the focus and its travel has settled (C17). */
async function opened(atlas: AtlasPage, id: string): Promise<void> {
  await expect(atlas.node(id)).toHaveAttribute('data-focus', 'true');
  await expect(atlas.node(id)).toHaveAttribute('data-show', 'full');
}

/**
 * The care loop, read on the running mock garden: the reminders hub, a plant's
 * vitals, a treatment course and the journal — and the law that reading them
 * moves nothing (C7/C9: the geography before the walk is the geography after).
 */
test.describe('the care loop as it reads on the board', () => {
  test('walks garden → plant → course → treatments → reminders → journal', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    const before = await atlas.geography();

    // the reminders hub: routine care, most overdue first, each row a plant
    await atlas.navTo('Due today').click();
    await opened(atlas, 'n-reminders');
    await expect(atlas.node('n-reminders')).toHaveAttribute('data-recap', '1 due today');
    const reminders = await atlas.node('n-reminders').innerText();
    expect(reminders).toContain('Prune · Terrace Lemon');
    expect(reminders).toContain('Water · Office Fig');
    expect(reminders).toContain('Today, 18:00');
    // the overdue count is the server's own (dueWindow 'server-day')
    expect(reminders).toMatch(/Overdue \d+ days?/);
    expect(reminders.indexOf('Prune · Terrace Lemon')).toBeLessThan(
      reminders.indexOf('Water · Office Fig'),
    );

    // a plant: vitals are a readout — colour and words, and no control in the list
    await atlas.node('n-reminders').locator('[data-goto="n-plant-1"]').first().click();
    await opened(atlas, 'n-plant-1');
    const vitals = atlas.node('n-plant-1').locator('[data-vitals]');
    await expect(vitals).toContainText('Needs attention');
    await expect(vitals).toContainText('Root rot · 1 of 4 done');
    expect(await vitals.locator('button').count()).toBe(0);

    // its course: steps are rows, one due, one stake naming the first open step
    await vitals.locator('[data-goto="n-treatment-301"]').click();
    await opened(atlas, 'n-treatment-301');
    const course = atlas.node('n-treatment-301');
    await expect(course).toContainText('Four steps');
    await expect(course.locator('[data-course] .row[data-done="true"]')).toHaveCount(1);
    await expect(course.locator('[data-course] .row[data-due="true"]')).toHaveCount(1);
    await expect(atlas.stake('n-treatment-301', 'Mark step 2 as done')).toBeVisible();
    // a step is a mutation, never an exit
    expect(await course.locator('[data-course] a').count()).toBe(0);
    expect(await course.locator('[data-course] .hop').count()).toBe(0);

    // the treatments hub: two courses drawn, the rest as one aggregate (C4)
    await atlas.navTo('My garden').click();
    await opened(atlas, 'n-garden');
    await atlas.node('n-treatments').click();
    await opened(atlas, 'n-treatments');
    await expect(atlas.node('n-treatments')).toHaveAttribute(
      'data-recap',
      '2 running · 2 waiting for a plan',
    );
    const ids = await atlas.nodeIds();
    expect(ids).toContain('n-treatment-305');
    expect(ids).toContain('n-treatments-more');

    // the journal: entries are nodes of their own
    await atlas.navTo('Journal').click();
    await opened(atlas, 'n-journal');
    await expect(atlas.node('n-journal')).toContainText('entries');
    expect((await atlas.nodeIds()).filter(id => /^n-log-/.test(id)).length).toBeGreaterThan(0);

    // nothing that was read moved anything
    expect(await atlas.geography()).toEqual(before);
    expect(atlas.apiRequests).toEqual([]);
    expect(atlas.consoleErrors).toEqual([]);
  });

  test('day zero prints zeros in each care-loop node', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('day-zero');

    await expect(atlas.node('n-reminders')).toHaveAttribute('data-recap', 'Nothing due today');
    await expect(atlas.node('n-reminders')).toHaveAttribute('data-focus', 'false');
    await expect(atlas.node('n-journal')).toHaveAttribute('data-recap', 'Nothing written yet');
    await expect(atlas.node('n-treatments')).toHaveAttribute('data-recap', 'No course running');

    const ids = await atlas.nodeIds();
    expect(ids.filter(id => /^n-(plant|species|scan|treatment|log)-/.test(id))).toEqual([]);
  });

  test('an outage is written into the node it belongs to, and nowhere else', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('outage');

    const failed = atlas.node('n-reminders');
    await expect(failed).toHaveAttribute('data-recap', 'Did not come back');
    await expect(failed).toContainText('Did not come back');
    await expect(failed.locator('.state--error')).toHaveCount(1);
    // the way forward lives in the failing node's own body (C25) — it is a
    // reader's control, so it is only shown once that node is the focus (C17)
    await expect(atlas.stake('n-reminders', 'Fetch this region')).toHaveCount(1);

    // the course whose plan did not come back wears its own failure too — the
    // failing ref is a PLAN id, and it must resolve back to its treatment node
    const course = atlas.node('n-treatment-301');
    await expect(course).toHaveAttribute('data-recap', 'Did not come back');
    await expect(course.locator('.state--error')).toHaveCount(1);
    await expect(course).not.toContainText('Every step is done');

    // every other family is still live
    await expect(atlas.node('n-garden')).toHaveAttribute('data-recap', /plants/);
    expect(await atlas.focusId()).toBe('n-garden');
  });
});
