import { expect, test } from '@playwright/test';
import { AtlasPage } from './atlas.page';

const SECTIONS = [
  'General',
  'Profile',
  'Notifications',
  'Appearance',
  'Data & Sync',
  'AI Preferences',
  'Privacy & Security',
  'Integrations',
  'Advanced',
];

test.describe('settings', () => {
  test('every section has a pane, and the geography never moves', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    const before = await atlas.geography();
    const focus = await atlas.focusId();

    await atlas.openSettings();
    for (const label of SECTIONS) {
      await atlas.settingsNav(label).click();
      await expect(atlas.settingsNav(label)).toHaveAttribute('aria-current', 'true');
      await expect(atlas.pane().locator('h3.sec').first()).toBeVisible();
    }
    // the settings overlay is never a dialog
    expect(await page.locator('#overview [role="dialog"]').count()).toBe(0);

    await page.locator('#save-settings').click();
    expect(await atlas.geography()).toEqual(before);
    expect(await atlas.focusId()).toBe(focus);
  });

  test('a change applies at once and is kept across a reload', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await atlas.openSettings();

    // the API lines are material on every panel — hiding them is immediate
    await atlas.settingsNav('Integrations').click();
    await atlas.pane().locator('[data-set="integrations.showApiIds"][data-value="false"]').click();
    await expect(page.locator('body')).toHaveAttribute('data-api-ids', 'off');

    // the reading itself, kept and painted before the first node on the way back
    await atlas.settingsNav('Appearance').click();
    await atlas.pane().locator('.palette[data-ui="glasshouse-table"]').click();
    await expect(page.locator('html')).toHaveAttribute('data-ui', 'glasshouse-table');
    await page.locator('#save-settings').click();

    const before = await atlas.geography();
    await page.reload();
    await page.locator('rz-node#n-treatments').waitFor();
    expect(await page.locator('html').getAttribute('data-ui')).toBe('glasshouse-table');
    await expect(page.locator('body')).toHaveAttribute('data-api-ids', 'off');
    expect(await atlas.geography()).toEqual(before);
  });

  test('cancel puts back exactly what was there when the panel opened', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    const before = await atlas.geography();
    const ids = await atlas.nodeIds();
    const focus = await atlas.focusId();
    const camera = await atlas.camera();

    await atlas.openSettings();
    await atlas.settingsNav('Appearance').click();
    await atlas.pane().locator('.palette[data-palette="terrarium"]').click();
    await expect(page.locator('html')).toHaveAttribute('data-palette', 'terrarium');
    await page.locator('#cancel-settings').click();
    await expect(page.locator('html')).toHaveAttribute('data-palette', 'first-light');

    // cancel re-assembles: the board it comes back to is the board it left
    await expect(page.locator('rz-node#n-treatments')).toBeVisible();
    expect(await atlas.geography()).toEqual(before);
    expect(await atlas.nodeIds()).toEqual(ids);
    expect(await atlas.focusId()).toBe(focus);
    expect(await atlas.camera()).toBe(camera);
  });

  test('cancel undoes a change made from the account stake, not only the gear', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await atlas.openSettings();
    await atlas.settingsNav('Profile').click();
    const metric = atlas.pane().locator('[data-set="profile.units"][data-value="metric"]');
    await expect(metric).toHaveAttribute('aria-pressed', 'true');
    await page.locator('#save-settings').click();

    // the other door into the overlay: the account node's own stake
    await atlas.node('n-account').click();
    await atlas.stake('n-account', 'Edit your details').click();
    await expect(page.locator('#overview')).toBeVisible();
    await atlas.pane().locator('[data-set="profile.units"][data-value="imperial"]').click();
    await expect(atlas.pane().locator('[data-set="profile.units"][data-value="imperial"]'))
      .toHaveAttribute('aria-pressed', 'true');
    await page.locator('#cancel-settings').click();

    await atlas.openSettings();
    await atlas.settingsNav('Profile').click();
    await expect(atlas.pane().locator('[data-set="profile.units"][data-value="metric"]'))
      .toHaveAttribute('aria-pressed', 'true');
  });

  test('the probe panel and the classic link follow their settings', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    await expect(page.locator('#probe')).toBeVisible();
    expect(await page.locator('#open-classic').count()).toBe(0);

    await atlas.openSettings();
    await atlas.settingsNav('Advanced').click();
    await atlas.pane().locator('[data-set="advanced.probes"][data-value="hide"]').click();
    await atlas.settingsNav('Integrations').click();
    await atlas.pane().locator('[data-set="integrations.openInClassic"][data-value="show"]').click();
    await page.locator('#save-settings').click();

    expect(await page.locator('#probe').count()).toBe(0);
    await expect(page.locator('#open-classic')).toHaveAttribute('target', '_blank');
  });

  test('reset restores the defaults, in words and on the document', async ({ page }) => {
    const atlas = new AtlasPage(page);
    await atlas.goto('garden');
    const before = await atlas.geography();
    const focus = await atlas.focusId();
    const camera = await atlas.camera();

    await atlas.openSettings();
    await atlas.settingsNav('Appearance').click();
    await atlas.pane().locator('.palette[data-ui="glasshouse-table"]').click();
    await page.locator('#settings footer .hop').click();
    await expect(page.locator('html')).toHaveAttribute('data-ui', 'sill-line');
    await expect(atlas.live()).toContainText('Defaults restored');

    // reset re-assembles too — and moves neither the board nor the camera
    await expect(page.locator('rz-node#n-treatments')).toBeVisible();
    expect(await atlas.geography()).toEqual(before);
    expect(await atlas.focusId()).toBe(focus);
    expect(await atlas.camera()).toBe(camera);
  });
});
