import { InjectionToken, Provider } from '@angular/core';
import type { MockScenario } from '../mock/mock.dataset';

/**
 * Whether this atlas instance talks to the live PlantPal backend or to the
 * in-memory mock garden, resolved ONCE at bootstrap (providers are
 * bootstrap-scoped, so changing the source needs a reload — the Data & Sync
 * pane says so). Mock mode is always an explicit, visible switch: a real empty
 * garden must never be dressed with sample records (day-zero Law 5).
 */
export interface MockMode {
  enabled: boolean;
  scenario: MockScenario;
  latencyMs: number;
}

export const MOCK_MODE = new InjectionToken<MockMode>('MOCK_MODE');

/** The off switch — used by every TestBed that is exercising live mode. */
export function provideMockModeOff(): Provider {
  return { provide: MOCK_MODE, useValue: { enabled: false, scenario: 'garden', latencyMs: 0 } };
}

const SETTINGS_KEY = 'atlas_settings';

interface StoredData {
  source?: string;
  mockScenario?: string;
  mockLatencyMs?: number;
}

type WinLike = Pick<Window, 'location' | 'history' | 'localStorage'>;

function readSettings(win: WinLike): Record<string, unknown> {
  try {
    const raw = win.localStorage.getItem(SETTINGS_KEY);
    if (!raw) return {};
    const parsed: unknown = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

/**
 * Per-window fallback for browsers where storage is blocked or throws (private
 * windows, blocked site data). `?mock=` is scrubbed from the URL as soon as it
 * is read, so without this a second resolution on the same window would lose
 * the garden the user explicitly asked for.
 */
const memoryData = new WeakMap<object, StoredData>();

function writeData(win: WinLike, patch: StoredData): void {
  memoryData.set(win, { ...(memoryData.get(win) ?? {}), ...patch });
  try {
    const all = readSettings(win);
    const prev = (all['data'] ?? {}) as StoredData;
    all['data'] = { ...prev, ...patch };
    win.localStorage.setItem(SETTINGS_KEY, JSON.stringify(all));
  } catch {
    /* storage is a convenience, never a requirement */
  }
}

/** '1'|'garden'|'day-zero'|'empty'|'outage' enable; '0'|'off' disable. */
function scenarioFromParam(value: string): MockScenario | null {
  const v = value.trim().toLowerCase();
  if (v === '1' || v === 'garden' || v === 'true' || v === 'on') return 'garden';
  if (v === 'day-zero' || v === 'empty') return 'day-zero';
  if (v === 'outage') return 'outage';
  return null;
}

function asScenario(value: unknown): MockScenario {
  return value === 'day-zero' || value === 'outage' || value === 'garden' ? value : 'garden';
}

/** Scrub ONLY the `mock` param; other params and the hash survive. */
function scrubMockParam(win: WinLike): void {
  try {
    const url = new URL(win.location.href);
    if (!url.searchParams.has('mock')) return;
    url.searchParams.delete('mock');
    const search = url.searchParams.toString();
    win.history.replaceState({}, '', `${url.pathname}${search ? `?${search}` : ''}${url.hash}`);
  } catch {
    /* history is optional furniture */
  }
}

/**
 * Precedence: `?mock=` (persisted, then scrubbed) → stored data.source →
 * environment.mockByDefault. Never throws.
 */
export function resolveMockMode(win: WinLike, env: { mockByDefault: boolean }): MockMode {
  const data = {
    ...(memoryData.get(win) ?? {}),
    ...((readSettings(win)['data'] ?? {}) as StoredData),
  } as StoredData;
  const latencyMs = typeof data.mockLatencyMs === 'number' ? data.mockLatencyMs : 0;

  let param: string | null = null;
  try {
    param = new URL(win.location.href).searchParams.get('mock');
  } catch {
    param = null;
  }

  if (param !== null) {
    const scenario = scenarioFromParam(param);
    if (scenario) {
      writeData(win, { source: 'mock', mockScenario: scenario });
      scrubMockParam(win);
      return { enabled: true, scenario, latencyMs };
    }
    writeData(win, { source: 'live' });
    scrubMockParam(win);
    return { enabled: false, scenario: asScenario(data.mockScenario), latencyMs };
  }

  const enabled = data.source === 'mock' || (data.source === undefined && env.mockByDefault);
  return { enabled, scenario: asScenario(data.mockScenario), latencyMs };
}
