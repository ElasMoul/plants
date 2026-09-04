/**
 * The settings panes, built from the prototype's own material: `h3.sec` headings,
 * `p.state__note` explanations, `.palettes > .palette` pickers, `dl.rows` for the
 * things that are read rather than chosen, and `.stake` for the few actions.
 *
 * The Appearance pane is NOT built here — it is the pinned overlay's own markup,
 * captured from the DOM at first render and re-inserted untouched (overview.html.ts
 * is never edited). Only its motion row gains controls, from MOTION_CONTROLS_HTML.
 */

import type { PushState, UserPreferencesDto } from '../world/world.dto';
import type { AtlasSettings, SettingsSection } from './settings.model';
import { SECTION_OF_LABEL } from './settings.model';

export interface PaneContext {
  settings: AtlasSettings;
  prefs: UserPreferencesDto | null;
  prefsState: 'idle' | 'reading' | 'failed';
  /** True while this atlas is reading the in-memory mock garden. */
  mock: boolean;
  push: PushState;
  pushEndpoint?: string;
  pushSubscribedAt?: string;
  account: { name: string; email: string; session: string };
  vapidConfigured: boolean;
}

export function esc(s: string | null | undefined): string {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

type Kind = 'string' | 'number' | 'boolean';

interface Option {
  value: string | number | boolean;
  name: string;
  note?: string;
  /** An option this server cannot serve — offered, disabled, with the reason. */
  disabled?: boolean;
  disabledNote?: string;
}

function kindOf(v: Option['value']): Kind {
  return typeof v === 'number' ? 'number' : typeof v === 'boolean' ? 'boolean' : 'string';
}

/** One enumerated key as the prototype's own picker; exactly one option is pressed. */
function picker(label: string, key: string, current: unknown, options: Option[]): string {
  const buttons = options
    .map(o => {
      const pressed = String(o.value) === String(current);
      const note = o.disabled ? (o.disabledNote ?? 'Not available here') : (o.note ?? '');
      return `<button class="palette" type="button" data-set="${esc(key)}" data-value="${esc(
        String(o.value),
      )}" data-kind="${kindOf(o.value)}" aria-pressed="${pressed}"${
        o.disabled ? ' aria-disabled="true"' : ''
      }><span class="palette__name">${esc(o.name)}</span><span class="palette__note">${esc(
        note,
      )}</span></button>`;
    })
    .join('');
  return `<div class="palettes" role="group" aria-label="${esc(label)}">${buttons}</div>`;
}

function yesNo(label: string, key: string, current: boolean, yes: string, no: string): string {
  return picker(label, key, current, [
    { value: true, name: yes },
    { value: false, name: no },
  ]);
}

function text(label: string, key: string, value: string, max = 40): string {
  return `<label class="rz-field"><span>${esc(label)}</span><input type="text" data-set="${esc(
    key,
  )}" data-kind="string" value="${esc(value)}" maxlength="${max}"></label>`;
}

function rows(pairs: [string, string][]): string {
  return `<dl class="rows">${pairs
    .map(([k, v]) => `<div class="row"><dt>${esc(k)}</dt><dd class="v mono">${esc(v)}</dd></div>`)
    .join('')}</dl>`;
}

function sec(title: string): string {
  return `<h3 class="sec">${esc(title)}</h3>`;
}

function note(body: string): string {
  return `<p class="state__note">${esc(body)}</p>`;
}

function action(name: string, label: string, quiet = true): string {
  return `<button class="stake${quiet ? ' stake--quiet' : ''}" type="button" data-action="${esc(
    name,
  )}">${esc(label)}</button>`;
}

/**
 * The two motion choices, in the pinned Appearance pane's own place: the prototype
 * printed "Follow the system's reduced-motion setting · on" as a static value.
 */
export function MOTION_FOLLOW_HTML(s: AtlasSettings): string {
  return yesNo(
    "Follow the system's reduced-motion setting",
    'appearance.followSystemMotion',
    s.appearance.followSystemMotion,
    'Follow the system',
    'Ignore it here',
  );
}

/** Appended beneath the pinned motion rows; the drift is decoration, and optional. */
export function CARD_DRIFT_HTML(s: AtlasSettings): string {
  return `<h3 class="sec" style="margin-top:var(--hbm-space-5)">Appearance · card drift</h3>${note(
    'The cards breathe a little where they stand. It is decoration, and it can be still while you read a long course.',
  )}${yesNo(
    'Card drift',
    'appearance.cardDrift',
    s.appearance.cardDrift,
    'Let them drift',
    'Hold them still',
  )}`;
}

export function MOTION_CONTROLS_HTML(s: AtlasSettings): string {
  return MOTION_FOLLOW_HTML(s) + CARD_DRIFT_HTML(s);
}

const FOCUS_OPTIONS: Option[] = [
  { value: 'garden', name: 'My garden', note: 'default · the hub every plant hangs from' },
  { value: 'today', name: 'Today', note: 'what is due, first' },
  { value: 'last', name: 'Where I was', note: 'needs Privacy · remember the last place' },
];

function generalPane(s: AtlasSettings): string {
  return `${sec('General · where you arrive')}
      ${note('The card the world opens on. Nothing else changes: every cell stays where it is.')}
      ${picker('Initial focus', 'general.initialFocus', s.general.initialFocus, FOCUS_OPTIONS)}

      ${sec('General · how often PlantPal is asked')}
      ${note('While a scan or a disease description is still being written, the atlas asks again on this beat. Rarer is kinder to a phone.')}
      ${picker('Poll interval', 'general.pollIntervalMs', s.general.pollIntervalMs, [
        { value: 4000, name: 'Every 4 seconds', note: 'freshest' },
        { value: 8000, name: 'Every 8 seconds', note: 'default' },
        { value: 20000, name: 'Every 20 seconds', note: 'kindest to a battery' },
      ])}
      ${note('An atlas left open can also re-read the garden quietly, so care logged on your phone lands here. An arrival never moves the camera.')}
      ${picker('Quiet refresh', 'general.refreshMinutes', s.general.refreshMinutes, [
        { value: 0, name: 'Never', note: 'only when you act' },
        { value: 5, name: 'Every 5 minutes', note: 'default' },
        { value: 15, name: 'Every 15 minutes' },
      ])}

      ${sec('General · what stays on the board')}
      ${note('PlantPal drops a finished course from the plant the moment it completes. This atlas can keep it for the rest of the session, so the card you are reading does not vanish under you.')}
      ${picker('Finished courses', 'general.keepFinished', s.general.keepFinished, [
        { value: 'session', name: 'Keep them this session', note: 'default' },
        { value: 'hide', name: 'Let them go at once' },
      ])}

      ${sec('General · how the world speaks')}
      ${note('The world says what it just did, once, in the live region. It can clear itself or stay for you to read.')}
      ${picker('Announcement', 'general.announceMs', s.general.announceMs, [
        { value: 1800, name: 'Clear after 1.8 seconds', note: 'default' },
        { value: 2600, name: 'Clear after 2.6 seconds' },
        { value: 0, name: 'Keep the last sentence' },
      ])}
      ${picker('Dates', 'general.dateStyle', s.general.dateStyle, [
        { value: 'relative', name: 'In days', note: 'default · “Today”, “In 3 days”, “Overdue 2 days”' },
        { value: 'absolute', name: 'By date', note: '“Sep 6 · overdue”' },
      ])}

      ${sec('General · logging care')}
      ${note('Two PlantPal calls finish a reminder. One writes a journal entry and can carry a note; the other only advances the schedule.')}
      ${picker('Completion', 'care.completeVerb', s.care.completeVerb, [
        { value: 'care/done', name: 'Log it in the journal', note: 'default · carries your note' },
        { value: 'reminders/complete', name: 'Only advance the schedule' },
      ])}
      ${yesNo('Ask for notes', 'care.askForNotes', s.care.askForNotes, 'Ask me for a note', 'One press, no note')}
      ${note('A watering can only be logged against a schedule. On a plant with none, the atlas can make the schedule first — two real calls — or say so and offer the form.')}
      ${picker('Care without a schedule', 'care.logWithoutReminder', s.care.logWithoutReminder, [
        { value: 'create-schedule', name: 'Set the schedule first', note: 'default' },
        { value: 'refuse', name: 'Say so, and offer the form' },
      ])}
      ${picker('Default frequency', 'care.defaultFrequencyDays', s.care.defaultFrequencyDays, [
        { value: 5, name: 'Every 5 days' },
        { value: 7, name: 'Every 7 days', note: 'default' },
        { value: 10, name: 'Every 10 days' },
        { value: 14, name: 'Every 14 days' },
      ])}
      ${picker('Water all', 'care.waterAllScope', s.care.waterAllScope, [
        { value: 'due', name: 'Only what is due', note: 'default' },
        { value: 'all-watering', name: 'Every watering schedule' },
      ])}
      ${picker('Care types offered', 'care.careTypes', s.care.careTypes, [
        { value: 'all', name: 'All ten', note: 'default · everything PlantPal knows' },
        { value: 'four', name: 'The usual four', note: 'water, feed, repot, prune' },
      ])}

      ${sec('General · reminders and courses')}
      ${note('PlantPal has no snooze and no pause. This atlas can keep both on this device, and every row that wears one says where it lives.')}
      ${picker('Snooze', 'reminders.snooze', s.reminders.snooze, [
        { value: 'local', name: 'Keep snoozes on this device', note: 'default' },
        { value: 'off', name: 'Do not offer snoozing' },
      ])}
      ${picker('Pause a course', 'treatment.pause', s.treatment.pause, [
        { value: 'local', name: 'Keep pauses on this device', note: 'default' },
        { value: 'off', name: 'Do not offer pausing' },
      ])}
      ${picker('Treatment steps', 'data.stepReminders', s.data.stepReminders, [
        { value: 'under-course', name: 'Only under their course', note: 'default' },
        { value: 'also-in-reminders', name: 'Also in the reminders list' },
      ])}`;
}

function profilePane(s: AtlasSettings, ctx: PaneContext): string {
  return `${sec('Profile · who PlantPal has')}
      ${note('PlantPal has no endpoint for a profile: the name and email below are what your session carries, and they are edited on the PlantPal pages themselves.')}
      ${rows([
        ['Name', ctx.account.name],
        ['Email', ctx.account.email],
        ['Session', ctx.account.session],
      ])}

      ${sec('Profile · on this device')}
      ${note('These three are kept here, on this device, because PlantPal has nowhere to keep them.')}
      ${text('Display name', 'profile.displayName', s.profile.displayName)}
      ${picker('Units', 'profile.units', s.profile.units, [
        { value: 'metric', name: 'Metric · °C', note: 'shown on your account card only' },
        { value: 'imperial', name: 'Imperial · °F', note: 'shown on your account card only' },
      ])}
      ${picker('Quiet hours', 'profile.quietHours', s.profile.quietHours, [
        { value: '21:00-07:30', name: '21:00 – 07:30', note: 'default · the bell stays quiet' },
        { value: '22:00-08:00', name: '22:00 – 08:00' },
        { value: 'off', name: 'No quiet hours' },
      ])}

      ${sec('Profile · garden type')}
      ${note('This one is PlantPal’s, not this device’s. Saved to PlantPal at once — Cancel does not undo it.')}
      ${
        ctx.prefs
          ? gardenTypePicker(ctx.prefs.businessTier)
          : ctx.prefsState === 'reading'
            ? note('PlantPal is being asked which garden type it has for you.')
            : note('PlantPal did not say which garden type it has for you. Your choice is unchanged; this atlas simply could not read it.')
      }

      <div class="btn-row">${action('sign-out', 'Sign out here')}</div>`;
}

function gardenTypePicker(businessTier: unknown): string {
  return `${picker('Garden type', 'profile.businessTier', !!businessTier, [
        { value: false, name: 'Home garden' },
        { value: true, name: 'Business or professional' },
      ])}`;
}

const PUSH_SENTENCE: Record<PushState, string> = {
  on: 'This device is subscribed. Reminders themselves are unchanged — a knock is not a reminder.',
  off: 'This device receives no knocks. Every reminder still stands.',
  blocked: 'This browser has refused notifications for this site. Only the browser can undo that.',
  unsupported: 'This browser cannot receive pushes at all.',
  unconfigured: "Push needs this server's public key, and it is not configured for this atlas.",
};

function notificationsPane(s: AtlasSettings, ctx: PaneContext): string {
  const refused =
    ctx.push === 'blocked' || ctx.push === 'unsupported' || ctx.push === 'unconfigured';
  const panel = refused
    ? `<section class="state state--error"><div class="state__head"><span class="state__title">Push is not possible here</span></div><p class="state__note">${esc(
        PUSH_SENTENCE[ctx.push],
      )}</p></section>`
    : '';
  return `${sec('Notifications · push to this device')}
      ${note(PUSH_SENTENCE[ctx.push])}
      ${panel}
      ${picker('Push', 'notifications.push', s.notifications.push, [
        { value: 'off', name: 'Off', note: 'default' },
        {
          value: 'on',
          name: 'On for this device',
          disabled: refused,
          disabledNote: 'Not possible in this browser',
        },
      ])}
      ${rows([
        ['Subscribed', ctx.pushSubscribedAt ?? 'not on this device'],
        ['Endpoint', ctx.pushEndpoint ? ctx.pushEndpoint.slice(0, 42) : 'none kept here'],
        ['Marked read', s.notifications.seenAt ?? 'never'],
      ])}
      <div class="btn-row">${action('forget-push', 'Forget this device')}</div>

      ${sec('Notifications · the bell')}
      ${note('The bell is an arrival, not a feed: it says how many and how far, then travels the same veins a card click does.')}
      ${picker('What it counts', 'notifications.bellCounts', s.notifications.bellCounts, [
        { value: 'due', name: 'Everything due', note: 'default' },
        { value: 'overdue', name: 'Only the late ones' },
        { value: 'none', name: 'Nothing — hide the count' },
      ])}
      ${picker('Where it goes', 'notifications.bellTarget', s.notifications.bellTarget, [
        { value: 'n-reminders', name: 'Reminders', note: 'default' },
        { value: 'n-today', name: 'Today' },
      ])}
      ${note('PlantPal buckets “today” in its own day, from its own midnight. The browser can decide instead, from the clock in front of you.')}
      ${picker('Due today means', 'notifications.dueWindow', s.notifications.dueWindow, [
        { value: 'server-day', name: 'PlantPal’s day', note: 'default · trusts the dashboard' },
        { value: 'rolling-24h', name: 'This browser, right now' },
      ])}`;
}

function dataPane(s: AtlasSettings, ctx: PaneContext): string {
  const mockOnly = ctx.mock
    ? `${sec('Data & Sync · the mock garden')}
      ${note('The mock garden lives for as long as this page does. A reload builds it again from its seed.')}
      ${picker('Scenario', 'data.mockScenario', s.data.mockScenario, [
        { value: 'garden', name: 'A garden', note: 'six plants, courses, a journal' },
        { value: 'day-zero', name: 'Day zero', note: 'a real, empty garden' },
        { value: 'outage', name: 'A partial outage', note: 'some families refuse' },
      ])}
      ${picker('Answer delay', 'data.mockLatencyMs', s.data.mockLatencyMs, [
        { value: 0, name: 'At once' },
        { value: 300, name: '300 ms', note: 'default' },
        { value: 1500, name: '1.5 seconds' },
        { value: 12000, name: '12 seconds', note: 'walks the slow state' },
      ])}
      <div class="btn-row">${action('reset-mock', 'Reset the mock garden')}</div>`
    : '';
  return `${sec('Data & Sync · where the world comes from')}
      ${note('Applies after a reload — the world is rebuilt from the other source. While the mock garden is on the topbar says so, so a real empty garden is never dressed with sample records.')}
      ${picker('Source', 'data.source', s.data.source, [
        { value: 'live', name: 'Live PlantPal', note: 'default · your own garden' },
        { value: 'mock', name: 'Mock garden', note: 'no backend, no sign-in' },
      ])}
      <div class="btn-row"><button class="stake" type="button" data-action="reload">Reload now</button></div>
      ${mockOnly}

      ${sec('Data & Sync · how much is read')}
      ${note('Each family is read in one page. A large garden may want more; a slow link, less.')}
      ${picker('Page size', 'data.pageSize', s.data.pageSize, [
        { value: 20, name: '20 rows' },
        { value: 50, name: '50 rows', note: 'default' },
        { value: 100, name: '100 rows' },
      ])}
      ${note('There is no endpoint for care across plants, so the journal is read plant by plant. Zero turns that fan-out off entirely.')}
      ${picker('Care history per plant', 'data.careLogPageSize', s.data.careLogPageSize, [
        { value: 0, name: 'Do not read it', note: 'the journal says so' },
        { value: 3, name: '3 entries' },
        { value: 5, name: '5 entries', note: 'default' },
        { value: 10, name: '10 entries' },
      ])}`;
}

const VISION_OPTIONS: { value: string; name: string; note: string }[] = [
  { value: 'GITHUB_GPT4O', name: 'GPT-4o', note: 'Best' },
  { value: 'GITHUB_GPT41', name: 'GPT-4.1', note: 'Frontier' },
  { value: 'ANTHROPIC_CLAUDE', name: 'Claude', note: 'Specialist' },
  { value: 'OLLAMA_GEMMA3', name: 'Gemma 3', note: 'Offline' },
  { value: 'PLANTNET', name: 'PlantNet', note: 'Balanced' },
];

const REASONING_OPTIONS: { value: string; name: string; note: string }[] = [
  { value: 'DEEPSEEK_R1', name: 'DeepSeek-R1', note: 'Best' },
  { value: 'GITHUB_GPT41_MINI', name: 'GPT-4.1 mini', note: 'Balanced' },
  { value: 'GITHUB_O4_MINI', name: 'o4-mini', note: 'Frontier' },
  { value: 'ANTHROPIC_CLAUDE', name: 'Claude', note: 'Specialist' },
  { value: 'OLLAMA_GEMMA3', name: 'Gemma 3', note: 'Offline' },
];

function modelOptions(
  table: { value: string; name: string; note: string }[],
  availability: Partial<Record<string, boolean>> | undefined,
): Option[] {
  return table.map(o => ({
    ...o,
    disabled: availability?.[o.value] === false,
    disabledNote: 'Not configured on this server',
  }));
}

function aiPane(s: AtlasSettings, ctx: PaneContext): string {
  if (ctx.prefsState === 'reading') {
    return `${sec('AI Preferences · your models')}
      ${note('PlantPal is being asked which models it will serve.')}
      <div class="n__skel" aria-hidden="true"><span class="sk sk--row"></span><span class="sk sk--row"></span></div>`;
  }
  if (ctx.prefsState === 'failed' || !ctx.prefs) {
    return `${sec('AI Preferences · your models')}
      <section class="state state--error"><div class="state__head"><span class="state__title">PlantPal did not say which models it has</span></div><p class="state__note">Your choices are unchanged; this atlas simply could not read them.</p><div class="btn-row">${action(
      'reload-prefs',
      'Ask again',
    )}</div></section>`;
  }
  return `${sec('AI Preferences · vision')}
      ${note('The model that looks at a photograph and names the plant.')}
      ${picker(
        'Vision model',
        'ai.visionModelPreference',
        ctx.prefs.visionModelPreference,
        modelOptions(VISION_OPTIONS, ctx.prefs.visionModelAvailability),
      )}

      ${sec('AI Preferences · reasoning')}
      ${note('The model that writes care plans, disease descriptions and treatment steps.')}
      ${picker(
        'Reasoning model',
        'ai.reasoningModelPreference',
        ctx.prefs.reasoningModelPreference,
        modelOptions(REASONING_OPTIONS, ctx.prefs.reasoningModelAvailability),
      )}

      ${sec('AI Preferences · PlantNet')}
      ${note('The flora PlantNet searches, and the language its common names come back in.')}
      ${text('PlantNet flora', 'ai.plantnetProject', ctx.prefs.plantnetProject ?? 'all', 60)}
      ${picker('Common names', 'ai.plantnetLang', ctx.prefs.plantnetLang ?? 'en', [
        { value: 'en', name: 'English' },
        { value: 'fr', name: 'Français' },
        { value: 'de', name: 'Deutsch' },
        { value: 'es', name: 'Español' },
      ])}
      <div class="btn-row">${action('save-plantnet', 'Save PlantNet preferences')}</div>

      ${sec('AI Preferences · starting a course')}
      ${note('Writing a plan is one blocking AI call, and it is the call that runs out of quota. It can wait for you to ask.')}
      ${yesNo(
        'Craft the plan on start',
        'ai.craftPlanOnStart',
        s.ai.craftPlanOnStart,
        'Write it straight away',
        'Wait until I ask',
      )}`;
}

function privacyPane(s: AtlasSettings): string {
  return `${sec('Privacy & Security · what this device keeps')}
      ${note('Three keys in this browser, and nothing else. PlantPal never sees any of them.')}
      ${rows([
        ['atlas_settings', 'the choices on these panes'],
        ['atlas_device', 'paused plans, snoozes, the push endpoint'],
        ['atlas_layout', 'cells, dragged positions and size pins'],
      ])}
      ${yesNo(
        'Remember the layout',
        'privacy.rememberLayout',
        s.privacy.rememberLayout,
        'Keep where I put things',
        'Forget it on reload',
      )}
      ${yesNo(
        'Remember the last place',
        'privacy.rememberLastFocus',
        s.privacy.rememberLastFocus,
        'Remember where I was',
        'Do not keep it',
      )}
      <div class="btn-row">${action(
        'forget-device',
        'Forget everything on this device',
      )}${action('sign-out', 'Sign out here')}</div>`;
}

function integrationsPane(s: AtlasSettings, ctx: PaneContext): string {
  return `${sec('Integrations · the PlantPal pages')}
      ${note('The classic PlantPal pages live at their own address, and a deployment can move them.')}
      ${text('PlantPal address', 'integrations.classicAppUrl', s.integrations.classicAppUrl, 300)}
      ${picker('Open in PlantPal', 'integrations.openInClassic', s.integrations.openInClassic, [
        { value: 'hide', name: 'Keep it out of the chrome', note: 'default' },
        { value: 'show', name: 'Offer it beside the veins' },
      ])}
      ${rows([
        ['Classic address', s.integrations.classicAppUrl],
        ['Push key', ctx.vapidConfigured ? 'configured' : 'not configured here'],
      ])}

      ${sec('Integrations · the API lines')}
      ${note('Each panel names the PlantPal call behind it. That is the atlas being the API’s brief; it can be quiet instead.')}
      ${yesNo(
        'Show the API lines',
        'integrations.showApiIds',
        s.integrations.showApiIds,
        'Show them',
        'Hide them',
      )}`;
}

function advancedPane(s: AtlasSettings, ctx: PaneContext): string {
  const mockOnly = ctx.mock
    ? `<div class="btn-row">${action('mock-fail-next', 'Make the next change fail')}</div>`
    : '';
  return `${sec('Advanced · the reviewer’s probes')}
      ${note('The three probes paint the slow, offline and reduced-motion readings of this very board. They change nothing about the garden.')}
      ${picker('Probe panel', 'advanced.probes', s.advanced.probes, [
        { value: 'show', name: 'Show it', note: 'default' },
        { value: 'hide', name: 'Hide it' },
      ])}
      ${picker('The slow probe holds', 'advanced.slowNodes', s.advanced.slowNodes, [
        { value: 'hubs', name: 'The hubs of this board', note: 'default' },
        { value: 'fixture', name: 'The pinned set', note: 'ids from the prototype only' },
      ])}
      ${mockOnly}

      ${sec('Advanced · fixed by the constitution')}
      ${note('Fixed by the constitution — not a setting.')}
      ${rows([
        ['Density', '4 → 2 + N more'],
        ['Travel', '300 ms'],
        ['Slow threshold', '10 s'],
        ['Shell scale', '0.42'],
      ])}`;
}

/**
 * The inner HTML of `.pane` for a section — or null for Appearance, whose pane is
 * the pinned markup itself and is re-inserted rather than rebuilt.
 */
export function renderPane(section: SettingsSection, ctx: PaneContext): string | null {
  const s = ctx.settings;
  switch (section) {
    case 'general':
      return generalPane(s);
    case 'profile':
      return profilePane(s, ctx);
    case 'notifications':
      return notificationsPane(s, ctx);
    case 'appearance':
      return null;
    case 'data':
      return dataPane(s, ctx);
    case 'ai':
      return aiPane(s, ctx);
    case 'privacy':
      return privacyPane(s);
    case 'integrations':
      return integrationsPane(s, ctx);
    case 'advanced':
      return advancedPane(s, ctx);
  }
}

// ── click routing ──────────────────────────────────────────────────────────

export type OverviewIntent =
  | { kind: 'section'; section: SettingsSection }
  | { kind: 'set'; key: string; value: unknown }
  | { kind: 'action'; name: string }
  | { kind: 'ui'; value: 'sill-line' | 'glasshouse-table' }
  | { kind: 'palette'; value: string }
  | { kind: 'close' }
  | { kind: 'cancel' }
  | { kind: 'save' }
  | { kind: 'reset' };

export function coerce(raw: string, kind: string | undefined): unknown {
  if (kind === 'number') return Number(raw);
  if (kind === 'boolean') return raw === 'true';
  return raw;
}

/** Pure classifier for a click inside #overview — the one place delegation is decided. */
export function routeOverviewClick(target: HTMLElement): OverviewIntent | null {
  if (target.closest('#cancel-settings')) return { kind: 'cancel' };
  if (target.closest('#save-settings')) return { kind: 'save' };
  if (target.closest('#dive-back, #close-settings')) return { kind: 'close' };
  if (target.closest('#settings footer .hop')) return { kind: 'reset' };

  const iface = target.closest<HTMLElement>('.palette[data-ui]');
  if (iface) return { kind: 'ui', value: iface.dataset['ui'] as 'sill-line' | 'glasshouse-table' };
  const pal = target.closest<HTMLElement>('.palette[data-palette]');
  if (pal) return { kind: 'palette', value: pal.dataset['palette'] as string };

  const setter = target.closest<HTMLElement>('[data-set][data-value]');
  if (setter) {
    if (setter.getAttribute('aria-disabled') === 'true') return null;
    return {
      kind: 'set',
      key: setter.dataset['set'] as string,
      value: coerce(setter.dataset['value'] as string, setter.dataset['kind']),
    };
  }

  const act = target.closest<HTMLElement>('[data-action]');
  if (act) return { kind: 'action', name: act.dataset['action'] as string };

  const nav = target.closest<HTMLElement>('#settings nav button');
  if (nav) {
    const section = SECTION_OF_LABEL[(nav.textContent ?? '').trim()];
    return section ? { kind: 'section', section } : null;
  }
  return null;
}
