import { Locator, Page, Request } from '@playwright/test';

export type MockScenario = 'garden' | 'day-zero' | 'outage';

/**
 * The atlas under Playwright. Every walk boots the mock garden — no backend, no
 * login, no page.route stubs: the app answers its own HTTP in memory.
 */
export class AtlasPage {
  /** Every request the page made to the API — must stay empty in mock mode. */
  readonly apiRequests: string[] = [];
  readonly consoleErrors: string[] = [];

  constructor(private readonly page: Page) {
    page.on('request', (r: Request) => {
      if (r.url().includes('/api/')) this.apiRequests.push(`${r.method()} ${r.url()}`);
    });
    page.on('console', m => {
      if (m.type() === 'error') this.consoleErrors.push(m.text());
    });
  }

  async goto(scenario: MockScenario = 'garden'): Promise<void> {
    await this.page.goto(`/?mock=${scenario}`);
    // the fixture board paints first; `n-treatments` exists only on an assembled
    // live board, so waiting for it is waiting for the mock garden itself
    await this.page.locator('rz-node#n-treatments').waitFor();
    await this.page.locator('rz-node[data-focus="true"]').first().waitFor();
  }

  /** The id of the node the camera is on. */
  async focusId(): Promise<string | null> {
    return this.page.locator('rz-node[data-focus="true"]').first().getAttribute('id');
  }

  node(id: string): Locator {
    return this.page.locator(`rz-node#${id}`);
  }

  nodeIds(): Promise<string[]> {
    return this.page.locator('rz-node').evaluateAll(els => els.map(e => e.id));
  }

  /** A stake inside a node's own body. */
  stake(nodeId: string, label: string): Locator {
    return this.node(nodeId).locator('.stake', { hasText: label }).first();
  }

  /** A button on the Actions rail (chrome, keyed by the focused node). */
  railAction(label: string): Locator {
    return this.page.locator('#actions-body .ch-btn', { hasText: label }).first();
  }

  /** One of the six left-rail places, by its accessible title. */
  navTo(name: string): Locator {
    return this.page.locator(`#rail .ch-btn[title="${name}"]`);
  }

  /** The announcement region — what the world last said. */
  live(): Locator {
    return this.page.locator('#live');
  }

  /** Every node's cell — the geography, for the laws that forbid it moving. */
  geography(): Promise<Record<string, string>> {
    return this.page.locator('rz-node').evaluateAll(els =>
      Object.fromEntries(els.map(e => [e.id, e.getAttribute('data-cell') ?? ''])),
    );
  }

  camera(): Promise<string> {
    return this.page.locator('#plane').evaluate(el => (el as HTMLElement).style.transform);
  }

  async openSettings(): Promise<void> {
    await this.page.locator('#open-settings').click();
    await this.page.locator('#settings').waitFor();
  }

  settingsNav(label: string): Locator {
    return this.page.locator('#settings nav button', { hasText: label }).first();
  }

  pane(): Locator {
    return this.page.locator('#settings .pane');
  }

  /** A reviewer probe: 'slow' | 'offline' | 'reduced' (the motion one is #p-motion). */
  probe(name: 'slow' | 'offline' | 'reduced'): Locator {
    return this.page.locator(name === 'reduced' ? '#p-motion' : `#p-${name}`);
  }

  /** Every node's left/top in the plane — the geometry a law says must not move. */
  positions(): Promise<Record<string, string>> {
    return this.page.locator('rz-node').evaluateAll(els =>
      Object.fromEntries(
        els.map(e => {
          const s = (e as HTMLElement).style;
          return [e.id, `${s.left}|${s.top}`];
        }),
      ),
    );
  }

  /** The in-memory backend's whole state — so a walk can prove nothing was written. */
  mockState(): Promise<string> {
    return this.page.evaluate(() => {
      const b = (window as unknown as { __atlasMock?: { state?: unknown } }).__atlasMock;
      return JSON.stringify(b?.state ?? null);
    });
  }

  topbarSub(): Locator {
    return this.page.locator('#topbar .sub');
  }

  accountChip(): Locator {
    return this.page.locator('#account');
  }
}
