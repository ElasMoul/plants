import { Injectable, signal } from '@angular/core';
import {
  AtlasSettings,
  DEFAULT_SETTINGS,
  SETTINGS_KEY,
  SettingsSection,
  diffFromDefaults,
  isAllowed,
  parseSettings,
  structuredCloneish,
} from './settings.model';
import type { AssemblySettings, UserPreferencesDto } from '../world/world.dto';

function read(): string | null {
  try {
    return localStorage.getItem(SETTINGS_KEY);
  } catch {
    return null;
  }
}

/**
 * Paint the remembered interface and palette onto <html> BEFORE the first node
 * renders, so a stored glasshouse-table never flashes as a sill line first.
 * index.html carries the defaults; this only overwrites them when a choice was
 * actually stored. Never throws — a blocked storage simply keeps the defaults.
 */
export function applyBootAppearance(doc: Document = document): void {
  const stored = read();
  if (!stored) return;
  const s = parseSettings(stored);
  doc.documentElement.setAttribute('data-ui', s.appearance.ui);
  doc.documentElement.setAttribute('data-palette', s.appearance.palette);
}

/**
 * The single home for atlas preferences. Settings apply live; Cancel restores the
 * snapshot taken when the overlay opened; Save keeps them; Reset applies the
 * defaults. Server-backed preferences are held beside them (serverPrefs) and are
 * touched by neither Cancel nor Reset — the pane says so.
 */
@Injectable({ providedIn: 'root' })
export class SettingsStore {
  readonly settings = signal<AtlasSettings>(parseSettings(read()));
  readonly section = signal<SettingsSection>('appearance');
  readonly serverPrefs = signal<UserPreferencesDto | null>(null);
  readonly prefsState = signal<'idle' | 'reading' | 'failed'>('idle');

  private openSnapshot: AtlasSettings | null = null;

  /** `get('general.pollIntervalMs')` — undefined for an unknown path. */
  get(path: string): unknown {
    const [group, key] = path.split('.');
    const g = this.settings()[group as keyof AtlasSettings] as Record<string, unknown> | undefined;
    return g?.[key];
  }

  /** Sets one leaf when the value is legal; illegal values are ignored, never thrown. */
  set(path: string, value: unknown): void {
    const [group, key] = path.split('.');
    if (!group || !key || !isAllowed(group, key, value)) return;
    const next = structuredCloneish(this.settings());
    (next[group as keyof AtlasSettings] as Record<string, unknown>)[key] = value;
    this.settings.set(next);
    this.persist(next);
  }

  /** Sets several leaves at once, e.g. `{ 'data.source': 'mock' }`. */
  patch(partial: Record<string, unknown>): void {
    const next = structuredCloneish(this.settings());
    let changed = false;
    for (const [path, value] of Object.entries(partial)) {
      const [group, key] = path.split('.');
      if (!group || !key || !isAllowed(group, key, value)) continue;
      (next[group as keyof AtlasSettings] as Record<string, unknown>)[key] = value;
      changed = true;
    }
    if (!changed) return;
    this.settings.set(next);
    this.persist(next);
  }

  snapshot(): AtlasSettings {
    return structuredCloneish(this.settings());
  }

  restore(s: AtlasSettings): void {
    const next = structuredCloneish(s);
    this.settings.set(next);
    this.persist(next);
  }

  /** The overlay opened — remember what to come back to if the reader cancels. */
  open(): void {
    this.openSnapshot = this.snapshot();
  }

  cancel(): void {
    if (this.openSnapshot) this.restore(this.openSnapshot);
    this.openSnapshot = null;
  }

  save(): void {
    this.openSnapshot = null;
    this.persist(this.settings());
  }

  reset(): void {
    this.restore(DEFAULT_SETTINGS);
  }

  /** A plain object (JSON round-trip equal) so the assembly stays a pure function. */
  assemblySnapshot(): AssemblySettings {
    const s = this.settings();
    return {
      dueWindow: s.notifications.dueWindow,
      dateStyle: s.general.dateStyle,
      stepReminders: s.data.stepReminders,
      snooze: s.reminders.snooze,
      pause: s.treatment.pause,
      keepFinished: s.general.keepFinished,
      careLogPageSize: s.data.careLogPageSize,
      displayName: s.profile.displayName,
      units: s.profile.units,
      quietHours: s.profile.quietHours,
      openInClassic: s.integrations.openInClassic,
      showApiIds: s.integrations.showApiIds,
      seenAt: s.notifications.seenAt,
      bellCounts: s.notifications.bellCounts,
    };
  }

  private persist(s: AtlasSettings): void {
    try {
      localStorage.setItem(SETTINGS_KEY, JSON.stringify(diffFromDefaults(s)));
    } catch {
      /* storage is a convenience, never a requirement */
    }
  }
}
