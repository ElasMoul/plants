import { Injectable, signal } from '@angular/core';

export const DEVICE_KEY = 'atlas_device';

export type DataSource = 'live' | 'mock';

/** Care state PlantPal has no home for — honest only while it stays on this device. */
export interface CareLocal {
  pausedPlanIds: number[];
  /** reminder id → ISO instant the snooze ends. */
  snoozed: Record<number, string>;
  /** Finished courses to re-fetch by id so they do not vanish under the reader. */
  knownTreatmentIds: number[];
}

export interface DeviceState {
  v: 1;
  lastFocus?: string;
  push?: { endpoint: string; subscribedAt: string };
  care: { live: CareLocal; mock: CareLocal };
}

const MAX_KNOWN = 10;

function emptyCare(): CareLocal {
  return { pausedPlanIds: [], snoozed: {}, knownTreatmentIds: [] };
}

function emptyState(): DeviceState {
  return { v: 1, care: { live: emptyCare(), mock: emptyCare() } };
}

function numbers(value: unknown): number[] {
  return Array.isArray(value) ? value.filter((n): n is number => typeof n === 'number') : [];
}

function snoozeMap(value: unknown): Record<number, string> {
  const out: Record<number, string> = {};
  if (!value || typeof value !== 'object') return out;
  for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
    const id = Number(k);
    if (Number.isFinite(id) && typeof v === 'string') out[id] = v;
  }
  return out;
}

function careFrom(value: unknown): CareLocal {
  if (!value || typeof value !== 'object') return emptyCare();
  const r = value as Record<string, unknown>;
  return {
    pausedPlanIds: numbers(r['pausedPlanIds']),
    snoozed: snoozeMap(r['snoozed']),
    knownTreatmentIds: numbers(r['knownTreatmentIds']).slice(0, MAX_KNOWN),
  };
}

export function parseDevice(raw: string | null): DeviceState {
  const out = emptyState();
  if (!raw) return out;
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return out;
  }
  if (!parsed || typeof parsed !== 'object') return out;
  const r = parsed as Record<string, unknown>;
  if (r['v'] !== 1) return out;
  if (typeof r['lastFocus'] === 'string') out.lastFocus = r['lastFocus'];
  const push = r['push'] as Record<string, unknown> | undefined;
  if (push && typeof push['endpoint'] === 'string' && typeof push['subscribedAt'] === 'string') {
    out.push = { endpoint: push['endpoint'], subscribedAt: push['subscribedAt'] };
  }
  const care = (r['care'] ?? {}) as Record<string, unknown>;
  out.care.live = careFrom(care['live']);
  out.care.mock = careFrom(care['mock']);
  return out;
}

/**
 * Device state, namespaced by data source. The namespacing is not tidiness: a
 * mock reminder id must never be fetched against the live backend after a source
 * switch.
 */
@Injectable({ providedIn: 'root' })
export class DeviceStore {
  readonly state = signal<DeviceState>(parseDevice(this.read()));

  /** A copy: the only way to change device state is through the methods below. */
  care(source: DataSource): CareLocal {
    const care = this.state().care[source];
    return {
      pausedPlanIds: [...care.pausedPlanIds],
      snoozed: { ...care.snoozed },
      knownTreatmentIds: [...care.knownTreatmentIds],
    };
  }

  pausePlan(source: DataSource, planId: number): void {
    this.mutate(source, care => {
      if (!care.pausedPlanIds.includes(planId)) care.pausedPlanIds.push(planId);
    });
  }

  resumePlan(source: DataSource, planId: number): void {
    this.mutate(source, care => {
      care.pausedPlanIds = care.pausedPlanIds.filter(id => id !== planId);
    });
  }

  snooze(source: DataSource, ids: number[], untilISO: string): void {
    this.mutate(source, care => {
      for (const id of ids) care.snoozed[id] = untilISO;
    });
  }

  /** Drops snoozes whose moment has passed, so a row stops lying about itself. */
  pruneSnoozed(source: DataSource, nowISO: string): void {
    const now = Date.parse(nowISO);
    this.mutate(source, care => {
      for (const [k, until] of Object.entries(care.snoozed)) {
        const t = Date.parse(until);
        if (!Number.isFinite(t) || t <= now) delete care.snoozed[Number(k)];
      }
    });
  }

  /** Newest first, capped at ten — a long-running garden never grows an unbounded list. */
  rememberTreatment(source: DataSource, id: number): void {
    this.mutate(source, care => {
      care.knownTreatmentIds = [id, ...care.knownTreatmentIds.filter(x => x !== id)].slice(
        0,
        MAX_KNOWN,
      );
    });
  }

  forgetTreatment(source: DataSource, id: number): void {
    this.mutate(source, care => {
      care.knownTreatmentIds = care.knownTreatmentIds.filter(x => x !== id);
    });
  }

  setLastFocus(id: string): void {
    const next = this.clone();
    next.lastFocus = id;
    this.commit(next);
  }

  setPush(v: { endpoint: string; subscribedAt: string } | undefined): void {
    const next = this.clone();
    if (v) next.push = v;
    else delete next.push;
    this.commit(next);
  }

  clear(): void {
    this.state.set(emptyState());
    try {
      localStorage.removeItem(DEVICE_KEY);
    } catch {
      /* storage is a convenience, never a requirement */
    }
  }

  private mutate(source: DataSource, fn: (care: CareLocal) => void): void {
    const next = this.clone();
    fn(next.care[source]);
    this.commit(next);
  }

  private clone(): DeviceState {
    return JSON.parse(JSON.stringify(this.state())) as DeviceState;
  }

  private commit(next: DeviceState): void {
    this.state.set(next);
    try {
      localStorage.setItem(DEVICE_KEY, JSON.stringify(next));
    } catch {
      /* storage is a convenience, never a requirement */
    }
  }

  private read(): string | null {
    try {
      return localStorage.getItem(DEVICE_KEY);
    } catch {
      return null;
    }
  }
}
