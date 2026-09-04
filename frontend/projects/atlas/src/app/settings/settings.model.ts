/**
 * The atlas's own preferences: every product choice the mission left open,
 * named, defaulted and validated in one place.
 *
 * What is NOT here: the five server-backed keys (ai.* and profile.businessTier)
 * which round-trip through GET/PUT /users/me/preferences, and device STATE
 * (paused plans, snoozed reminders, the push endpoint, last focus) which lives
 * in DeviceStore under its own key, namespaced by data source.
 */

import { environment } from '../../environments/environment';

export const SETTINGS_KEY = 'atlas_settings';

export type SettingsSection =
  | 'general'
  | 'profile'
  | 'notifications'
  | 'appearance'
  | 'data'
  | 'ai'
  | 'privacy'
  | 'integrations'
  | 'advanced';

/** The nine verbatim nav button texts of the pinned settings overlay. */
export const SECTION_OF_LABEL: Record<string, SettingsSection> = {
  General: 'general',
  Profile: 'profile',
  Notifications: 'notifications',
  Appearance: 'appearance',
  'Data & Sync': 'data',
  'AI Preferences': 'ai',
  'Privacy & Security': 'privacy',
  Integrations: 'integrations',
  Advanced: 'advanced',
};

export interface AtlasSettings {
  general: {
    initialFocus: 'garden' | 'today' | 'last';
    pollIntervalMs: number;
    refreshMinutes: number;
    keepFinished: 'session' | 'hide';
    announceMs: number;
    dateStyle: 'relative' | 'absolute';
  };
  care: {
    completeVerb: 'care/done' | 'reminders/complete';
    askForNotes: boolean;
    logWithoutReminder: 'create-schedule' | 'refuse';
    defaultFrequencyDays: number;
    waterAllScope: 'due' | 'all-watering';
    careTypes: 'four' | 'all';
  };
  reminders: { snooze: 'local' | 'off' };
  treatment: { pause: 'local' | 'off' };
  data: {
    stepReminders: 'under-course' | 'also-in-reminders';
    source: 'live' | 'mock';
    mockScenario: 'garden' | 'day-zero' | 'outage';
    mockLatencyMs: number;
    pageSize: number;
    careLogPageSize: number;
  };
  profile: {
    displayName: string;
    units: 'metric' | 'imperial';
    quietHours: 'off' | '21:00-07:30' | '22:00-08:00';
  };
  notifications: {
    push: 'off' | 'on';
    bellCounts: 'due' | 'overdue' | 'none';
    bellTarget: 'n-reminders' | 'n-today';
    dueWindow: 'server-day' | 'rolling-24h';
    seenAt: string | null;
  };
  appearance: {
    ui: 'sill-line' | 'glasshouse-table';
    palette: string;
    followSystemMotion: boolean;
    cardDrift: boolean;
  };
  privacy: { rememberLayout: boolean; rememberLastFocus: boolean };
  integrations: { classicAppUrl: string; openInClassic: 'hide' | 'show'; showApiIds: boolean };
  advanced: { probes: 'show' | 'hide'; slowNodes: 'hubs' | 'fixture' };
}

export const DEFAULT_SETTINGS: AtlasSettings = {
  general: {
    initialFocus: 'garden',
    pollIntervalMs: 8000,
    refreshMinutes: 5,
    keepFinished: 'session',
    announceMs: 1800,
    dateStyle: 'relative',
  },
  care: {
    completeVerb: 'care/done',
    askForNotes: false,
    logWithoutReminder: 'create-schedule',
    defaultFrequencyDays: 7,
    waterAllScope: 'due',
    careTypes: 'all',
  },
  reminders: { snooze: 'local' },
  treatment: { pause: 'local' },
  data: {
    stepReminders: 'under-course',
    source: 'live',
    mockScenario: 'garden',
    mockLatencyMs: 300,
    pageSize: 50,
    careLogPageSize: 5,
  },
  profile: { displayName: '', units: 'metric', quietHours: '21:00-07:30' },
  notifications: {
    push: 'off',
    bellCounts: 'due',
    bellTarget: 'n-reminders',
    dueWindow: 'server-day',
    seenAt: null,
  },
  appearance: { ui: 'sill-line', palette: 'first-light', followSystemMotion: true, cardDrift: true },
  privacy: { rememberLayout: true, rememberLastFocus: false },
  integrations: {
    classicAppUrl: environment.classicAppUrl,
    openInClassic: 'hide',
    showApiIds: true,
  },
  advanced: { probes: 'show', slowNodes: 'hubs' },
};

/** The seven palettes the pinned prototype ships. */
export const PALETTES = [
  'first-light',
  'glasshouse',
  'terracotta',
  'moss',
  'night-garden',
  'paper',
  'ink',
];

type Rule = readonly unknown[] | 'string' | 'boolean' | 'isoOrNull' | 'url';

/** Every leaf key's permitted values — anything else is dropped back to the default. */
export const ALLOWED: Record<string, Record<string, Rule>> = {
  general: {
    initialFocus: ['garden', 'today', 'last'],
    pollIntervalMs: [4000, 8000, 20000],
    refreshMinutes: [0, 5, 15],
    keepFinished: ['session', 'hide'],
    announceMs: [1800, 2600, 0],
    dateStyle: ['relative', 'absolute'],
  },
  care: {
    completeVerb: ['care/done', 'reminders/complete'],
    askForNotes: 'boolean',
    logWithoutReminder: ['create-schedule', 'refuse'],
    defaultFrequencyDays: [5, 7, 10, 14],
    waterAllScope: ['due', 'all-watering'],
    careTypes: ['four', 'all'],
  },
  reminders: { snooze: ['local', 'off'] },
  treatment: { pause: ['local', 'off'] },
  data: {
    stepReminders: ['under-course', 'also-in-reminders'],
    source: ['live', 'mock'],
    mockScenario: ['garden', 'day-zero', 'outage'],
    mockLatencyMs: [0, 300, 1500, 12000],
    pageSize: [20, 50, 100],
    careLogPageSize: [0, 3, 5, 10],
  },
  profile: {
    displayName: 'string',
    units: ['metric', 'imperial'],
    quietHours: ['off', '21:00-07:30', '22:00-08:00'],
  },
  notifications: {
    push: ['off', 'on'],
    bellCounts: ['due', 'overdue', 'none'],
    bellTarget: ['n-reminders', 'n-today'],
    dueWindow: ['server-day', 'rolling-24h'],
    seenAt: 'isoOrNull',
  },
  appearance: {
    ui: ['sill-line', 'glasshouse-table'],
    palette: PALETTES,
    followSystemMotion: 'boolean',
    cardDrift: 'boolean',
  },
  privacy: { rememberLayout: 'boolean', rememberLastFocus: 'boolean' },
  integrations: {
    classicAppUrl: 'url',
    openInClassic: ['hide', 'show'],
    showApiIds: 'boolean',
  },
  advanced: { probes: ['show', 'hide'], slowNodes: ['hubs', 'fixture'] },
};

const MAX_NAME = 40;

function accepts(rule: Rule, value: unknown): boolean {
  if (rule === 'boolean') return typeof value === 'boolean';
  if (rule === 'string') return typeof value === 'string' && value.length <= MAX_NAME;
  if (rule === 'url') return typeof value === 'string' && value.length > 0 && value.length <= 300;
  if (rule === 'isoOrNull')
    return value === null || (typeof value === 'string' && !Number.isNaN(Date.parse(value)));
  return rule.includes(value);
}

/** True when `value` is a legal setting for `group.key`. Unknown keys are never legal. */
export function isAllowed(group: string, key: string, value: unknown): boolean {
  const rule = ALLOWED[group]?.[key];
  return rule === undefined ? false : accepts(rule, value);
}

/**
 * Read stored settings over the defaults: unknown keys are dropped, out-of-range
 * values fall back to their default, malformed JSON yields the defaults. Never
 * throws.
 */
export function parseSettings(raw: string | null): AtlasSettings {
  const out = structuredCloneish(DEFAULT_SETTINGS);
  if (!raw) return out;
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return out;
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return out;
  const record = parsed as Record<string, unknown>;
  for (const group of Object.keys(ALLOWED)) {
    const stored = record[group];
    if (!stored || typeof stored !== 'object' || Array.isArray(stored)) continue;
    const groupOut = out[group as keyof AtlasSettings] as Record<string, unknown>;
    for (const [key, value] of Object.entries(stored as Record<string, unknown>)) {
      if (isAllowed(group, key, value)) groupOut[key] = value;
    }
  }
  return out;
}

/** A deep copy of a settings object; jsdom-safe (no structuredClone dependency). */
export function structuredCloneish(s: AtlasSettings): AtlasSettings {
  return JSON.parse(JSON.stringify(s)) as AtlasSettings;
}

/** The difference from the defaults — the only thing worth persisting. */
export function diffFromDefaults(s: AtlasSettings): Record<string, Record<string, unknown>> {
  const out: Record<string, Record<string, unknown>> = {};
  for (const group of Object.keys(ALLOWED)) {
    const mine = s[group as keyof AtlasSettings] as Record<string, unknown>;
    const base = DEFAULT_SETTINGS[group as keyof AtlasSettings] as Record<string, unknown>;
    for (const key of Object.keys(base)) {
      if (mine[key] !== base[key]) {
        out[group] ??= {};
        out[group][key] = mine[key];
      }
    }
  }
  return out;
}
