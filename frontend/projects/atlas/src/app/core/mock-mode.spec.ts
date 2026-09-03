import { resolveMockMode } from './mock-mode';

/** A hand-rolled Window stand-in — resolveMockMode must be pure over it. */
function fakeWin(href: string, store: Record<string, string> = {}, throwing = false) {
  const replaced: string[] = [];
  const win = {
    location: { href } as Location,
    history: {
      replaceState: (_s: unknown, _t: string, url?: string | URL | null) => {
        replaced.push(String(url));
        win.location.href = new URL(String(url), href).href;
      },
    } as History,
    localStorage: {
      getItem: (k: string) => {
        if (throwing) throw new Error('blocked');
        return k in store ? store[k] : null;
      },
      setItem: (k: string, v: string) => {
        if (throwing) throw new Error('blocked');
        store[k] = v;
      },
    } as unknown as Storage,
  };
  return { win, store, replaced };
}

const settings = (data: Record<string, unknown>) => ({ atlas_settings: JSON.stringify({ data }) });

describe('resolveMockMode (S1 — activation)', () => {
  it('enables the mock garden from ?mock=1 and scrubs only that param', () => {
    const { win, store, replaced } = fakeWin('http://localhost:4300/?mock=1&keep=yes#frag');
    const mode = resolveMockMode(win, { mockByDefault: false });
    expect(mode).toEqual({ enabled: true, scenario: 'garden', latencyMs: 0 });
    expect(replaced[0]).toBe('/?keep=yes#frag');
    expect(JSON.parse(store['atlas_settings']).data).toEqual({ source: 'mock', mockScenario: 'garden' });
  });

  it('maps ?mock=empty to the day-zero scenario', () => {
    const { win } = fakeWin('http://localhost:4300/?mock=empty');
    expect(resolveMockMode(win, { mockByDefault: false }).scenario).toBe('day-zero');
  });

  it('reads the scenario from ?mock=outage', () => {
    const { win } = fakeWin('http://localhost:4300/?mock=outage');
    expect(resolveMockMode(win, { mockByDefault: false })).toMatchObject({ enabled: true, scenario: 'outage' });
  });

  it('?mock=0 disables and persists live', () => {
    const { win, store } = fakeWin('http://localhost:4300/?mock=0', settings({ source: 'mock' }));
    expect(resolveMockMode(win, { mockByDefault: true }).enabled).toBe(false);
    expect(JSON.parse(store['atlas_settings']).data.source).toBe('live');
  });

  it('falls back to the stored data.source when no query is present', () => {
    const { win } = fakeWin('http://localhost:4300/', settings({ source: 'mock', mockScenario: 'outage', mockLatencyMs: 300 }));
    expect(resolveMockMode(win, { mockByDefault: false })).toEqual({ enabled: true, scenario: 'outage', latencyMs: 300 });
  });

  it('falls back to environment.mockByDefault when storage is empty', () => {
    const { win } = fakeWin('http://localhost:4300/');
    expect(resolveMockMode(win, { mockByDefault: true }).enabled).toBe(true);
    expect(resolveMockMode(win, { mockByDefault: false }).enabled).toBe(false);
  });

  it('survives malformed atlas_settings JSON', () => {
    const { win } = fakeWin('http://localhost:4300/', { atlas_settings: '{not json' });
    expect(resolveMockMode(win, { mockByDefault: false }).enabled).toBe(false);
  });

  it('never throws when localStorage throws', () => {
    expect(() => resolveMockMode(fakeWin('http://localhost:4300/?mock=garden', {}, true).win, { mockByDefault: false })).not.toThrow();
    expect(resolveMockMode(fakeWin('http://localhost:4300/?mock=garden', {}, true).win, { mockByDefault: false }).enabled).toBe(true);
  });
});
