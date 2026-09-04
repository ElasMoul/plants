import { buildAdjacency, Edge, rank } from '@plantpal/rhizome-engine';
import {
  agoLabel,
  careLabel,
  dateLabel,
  daysUntil,
  dueLine,
  isDue,
  timeLabel,
  wordNumber,
} from './dates';
import {
  AssemblySettings,
  CareLogDto,
  Cell,
  FamilyFailure,
  IdentificationDto,
  PlantDto,
  ReminderDto,
  SpeciesDto,
  TreatmentDto,
  TreatmentPlanDto,
  WorldSources,
} from './world.dto';
import { NodeKind, WorldData, WorldMeta, WorldNode } from './world.model';

/** A collection with this many members or more collapses to 2 + "+N more" (C4/density). */
const DENSITY_CAP = 4;
const CENTER_ROW = 6;

type DraftNode = Omit<WorldNode, 'cell'> & { cell?: WorldNode['cell'] };

/** Escape user-originated text before it enters generated body HTML. */
function esc(s: string | null | undefined): string {
  return (s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/** The default skeleton shape — a name and two lines, the prototype's card blank. */
const DEFAULT_SKEL = ['sk--name', 'sk--line', 'sk--line is-short'];

/** Still-arriving material, revealed only under the slow probe (rhizome.css:899). */
const PENDING_BLOCK =
  '<div class="pending"><p class="label" style="margin:0">Still arriving</p><div class="sk sk--row"></div><div class="sk sk--row"></div></div>';

/**
 * The head of every generated body, in the prototype's own order: the recap, this
 * node's own staleness sentence (each node says its own — C24), its own skeleton
 * shape at the height of the rows it will hold, and the still-arriving block.
 */
const recapWrap = (line: string, note?: string, stale?: string, skel: string[] = DEFAULT_SKEL) =>
  `<div class="n__recap"><p class="n__recap-line">${line}</p>${note ? `<p class="n__recap-note">${note}</p>` : ''}</div>
   ${stale ? `<div class="staleness"><span aria-hidden="true">◷</span> ${stale}</div>` : ''}
   <div class="n__skel" aria-hidden="true">${skel.map(c => `<div class="sk ${c}"></div>`).join('')}</div>
   ${PENDING_BLOCK}`;

/** The `action · …` line is the API's own brief — a reader may turn it off. */
function stateId(id: string, settings: AssemblySettings): string {
  return settings.showApiIds ? `<span class="state__id">${id}</span>` : '';
}

const full = (inner: string) => `<div class="n__full">${inner}</div>`;

function healthTag(status?: string | null): string {
  if (status === 'ISSUES_DETECTED') return '<span class="tag tag--ailing">Needs attention</span>';
  if (status === 'HEALTHY') return '<span class="tag tag--thriving">Healthy</span>';
  return '<span class="tag tag--unknown">Unknown</span>';
}

function waterLine(p: PlantDto): string {
  const d = p.nextWaterDays;
  if (d == null) return '—';
  if (d <= 0) return d === 0 ? 'Due today' : `Overdue ${-d}d`;
  return `In ${d} days`;
}

// ── the care loop's shared reading of the sources ────────────────────────────

/**
 * What every care-loop body reads: the rows themselves, the device-local state
 * that is honestly named as device-local, and the set of nodes that will actually
 * be drawn, so a row can only ever link somewhere that exists.
 */
interface Ctx {
  now: string;
  settings: AssemblySettings;
  drawn: Set<string>;
  reminders: ReminderDto[];
  logs: CareLogDto[];
  treatments: TreatmentDto[];
  plansById: Record<number, TreatmentPlanDto>;
  plants: PlantDto[];
  paused: number[];
  snoozed: Record<number, string>;
  rateLimited: Record<number, { retryAfterSeconds: number; at: string }>;
  /** The server's own overdue count, per reminder id, from /dashboard's buckets. */
  overdueByReminder: Record<number, number>;
  /** True when care history was fetched only for the plants this board draws. */
  logsPartial: boolean;
}

/** The scope clause a count wears when it is not the whole garden's record. */
function logScope(ctx: Ctx): string {
  return ctx.logsPartial ? ' from the plants on this board' : '';
}

/** "3 entries" / "1 entry", scoped honestly when the fan-out was partial. */
function entriesLine(ctx: Ctx): string {
  const n = ctx.logs.length;
  return `${n} ${n === 1 ? 'entry' : 'entries'}${logScope(ctx)}`;
}

/** "3 things logged" / "1 thing logged", scoped honestly. */
function loggedLine(ctx: Ctx): string {
  const n = ctx.logs.length;
  return `${n} ${n === 1 ? 'thing' : 'things'} logged${logScope(ctx)}`;
}

/** The one sentence a due row says about itself, in this world's clock. */
function due(ctx: Ctx, r: ReminderDto): string {
  return dueLine(r.nextDueAt, ctx.now, ctx.settings, ctx.overdueByReminder[r.id]);
}

function isSnoozed(ctx: Ctx, r: ReminderDto): boolean {
  const until = ctx.snoozed[r.id];
  return until != null && Date.parse(until) > Date.parse(ctx.now);
}

/** Routine care: treatment steps live inside their course unless the reader says otherwise. */
function routineReminders(ctx: Ctx): ReminderDto[] {
  return ctx.reminders
    .filter(r => r.enabled)
    .filter(r => ctx.settings.stepReminders === 'also-in-reminders' || r.treatmentPlanId == null)
    .sort((a, b) => a.nextDueAt.localeCompare(b.nextDueAt) || a.id - b.id);
}

function remindersOfPlant(ctx: Ctx, plantId: number, careType?: string): ReminderDto[] {
  return routineReminders(ctx).filter(
    r => r.plantId === plantId && (careType == null || r.careType === careType),
  );
}

function logsOfPlant(ctx: Ctx, plantId: number, careType?: string): CareLogDto[] {
  return ctx.logs.filter(l => l.plantId === plantId && (careType == null || l.careType === careType));
}

function stepsOf(ctx: Ctx, t: TreatmentDto): ReminderDto[] {
  const plan = t.treatmentPlanId != null ? ctx.plansById[t.treatmentPlanId] : undefined;
  return [...(plan?.steps ?? [])].sort((a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0) || a.id - b.id);
}

/** Did this course's plan actually arrive? A missing answer is never "0 of 0". */
function hasPlan(ctx: Ctx, t: TreatmentDto): boolean {
  return t.treatmentPlanId == null || ctx.plansById[t.treatmentPlanId] != null;
}

function isPaused(ctx: Ctx, t: TreatmentDto): boolean {
  return (
    ctx.settings.pause === 'local' &&
    t.treatmentPlanId != null &&
    ctx.paused.includes(t.treatmentPlanId)
  );
}

/** The one clause a course says about itself from across the plane (C17/C19). */
function courseRecap(ctx: Ctx, t: TreatmentDto): string {
  if (t.status === 'DRAFT') return 'Draft · no plan yet';
  const steps = stepsOf(ctx, t);
  if (t.status === 'COMPLETED') return `${esc(t.diseaseName)} · finished`;
  if (t.status === 'DISMISSED') return `${esc(t.diseaseName)} · dismissed`;
  if (!hasPlan(ctx, t)) return `${esc(t.diseaseName)} · the plan did not come back`;
  const done = steps.filter(s => !s.enabled).length;
  const line = `${done} of ${steps.length} done`;
  return isPaused(ctx, t) ? `paused · ${line}` : line;
}

/** Rank: what is due first, then what is running, then what is waiting, then what is over. */
function treatmentRank(ctx: Ctx, t: TreatmentDto): number {
  if (t.status === 'COMPLETED' || t.status === 'DISMISSED') return 4;
  // A device-local pause deliberately does NOT re-rank: pausing the course you are
  // reading must change that node's own words, never the board's membership — a
  // demoted course would drop out of the drawn two and vanish under the reader (C9).
  if (t.status === 'DRAFT') return 2;
  const open = stepsOf(ctx, t).filter(s => s.enabled);
  return open.some(s => isDue(s.nextDueAt, ctx.now, ctx.settings.dueWindow)) ? 0 : 1;
}

// ── per-node body builders (the prototype's material language, live data) ────

function plantBody(p: PlantDto, ctx: Ctx): string {
  const binomial = p.species ? `<span class="plate__binomial">${esc(p.species)}</span>` : '';
  const water = remindersOfPlant(ctx, p.id, 'WATERING')[0];
  const routine = remindersOfPlant(ctx, p.id);
  const lastWater = logsOfPlant(ctx, p.id, 'WATERING')[0];
  const lastFeed = logsOfPlant(ctx, p.id, 'FERTILIZING')[0];
  const course = ctx.treatments.find(
    t => t.plantId === p.id && (t.status === 'DRAFT' || t.status === 'IN_PROGRESS'),
  );
  const courseCell = course
    ? linkTo(ctx.drawn, `n-treatment-${course.id}`, `${esc(course.diseaseName)} · ${courseRecap(ctx, course)}`)
    : 'None running';
  const careRows = routine
    .map(r => `<div class="row"><dt>${careLabel(r.careType)}</dt><dd>${due(ctx, r)}</dd></div>`)
    .join('');
  return (
    recapWrap(
      `${waterLine(p)} · water`,
      esc(p.commonName ?? p.species ?? ''),
      `Last read ${timeLabel(ctx.now)}`,
      ['sk--plate', 'sk--row', 'sk--row'],
    ) +
    full(`
      <div class="plate">
        <div class="plate__specimen" aria-hidden="true"></div>
        <div>
          <h3 class="plate__name">${esc(p.nickname)}</h3>
          ${binomial}
          <p class="plate__meta">${healthTag(p.healthStatus)}</p>
        </div>
      </div>
      <section class="state" data-brief-item="action:/api/v1/plants/**">
        <div class="state__head"><h4 class="state__title">This plant</h4>${stateId(`action · /api/v1/plants/${p.id}`, ctx.settings)}</div>
        <dl class="rows" data-vitals>
          <div class="row"><dt>Health</dt><dd>${healthTag(p.healthStatus)}</dd></div>
          <div class="row"><dt>Species</dt><dd>${esc(p.commonName ?? p.species ?? 'Not identified yet')}</dd></div>
          <div class="row"><dt>Next water</dt><dd>${water ? due(ctx, water) : 'No schedule yet'}</dd></div>
          <div class="row"><dt>Location</dt><dd>${p.location ? esc(p.location) : 'Not recorded'}</dd></div>
          <div class="row"><dt>Course</dt><dd>${courseCell}</dd></div>
          <div class="row"><dt>Watered</dt><dd>${lastWater ? agoLabel(lastWater.performedAt, ctx.now, ctx.settings.dateStyle) : 'Not logged yet'}</dd></div>
          <div class="row"><dt>Fed</dt><dd>${lastFeed ? agoLabel(lastFeed.performedAt, ctx.now, ctx.settings.dateStyle) : 'Not logged yet'}</dd></div>
        </dl>
        <div class="btn-row">
          <button class="stake" type="button" data-arg="plant:${p.id}">${water ? 'Water plant' : 'Set a watering schedule'}</button>
          <button class="stake stake--quiet" type="button" data-arg="plant:${p.id}">Add note</button>
          <button class="stake stake--quiet" type="button" data-arg="plant:${p.id}">Add a reminder</button>
        </div>
      </section>
      <section class="state" data-brief-item="action:/api/v1/care/**">
        <div class="state__head"><h4 class="state__title">Care, and what you actually did</h4>${stateId('action · /api/v1/care/**', ctx.settings)}</div>
        ${careRows ? `<dl class="rows">${careRows}</dl>` : `<p class="state__note">${esc(p.nickname)} has no care schedule yet — one press below makes the first one.</p>`}
        <div class="btn-row">
          <button class="stake" type="button" data-arg="plant:${p.id}">Log a watering</button>
          ${water ? `<button class="stake stake--quiet" type="button" data-arg="reminder:${water.id}">Change the schedule</button>` : ''}
          ${water ? `<button class="stake stake--quiet" type="button" data-arg="reminder:${water.id}">Stop this reminder</button>` : ''}
        </div>
      </section>`)
  );
}

// ── the care loop's own nodes ────────────────────────────────────────────────

function reminderHubBody(ctx: Ctx): string {
  const routine = routineReminders(ctx);
  const awake = routine.filter(r => !isSnoozed(ctx, r));
  const dueToday = awake.filter(r => daysUntil(r.nextDueAt, ctx.now, ctx.settings.dueWindow) === 0);
  const next = awake.find(r => !isDue(r.nextDueAt, ctx.now, ctx.settings.dueWindow)) ?? awake[0];
  const overdue = awake.find(r => daysUntil(r.nextDueAt, ctx.now, ctx.settings.dueWindow) < 0);

  const rows = routine
    .slice(0, 6)
    .map(r => {
      const overdueRow = !isSnoozed(ctx, r) && daysUntil(r.nextDueAt, ctx.now, ctx.settings.dueWindow) <= 0;
      const dd = isSnoozed(ctx, r)
        ? 'Snoozed until tomorrow · on this device'
        : `${due(ctx, r)} <button class="stake stake--quiet" type="button" data-arg="reminder:${r.id}">Done</button>`;
      return `<div class="row" data-due="${overdueRow}"><dt>${careLabel(r.careType)} · ${linkTo(ctx.drawn, `n-plant-${r.plantId}`, esc(r.plantNickname ?? `Plant ${r.plantId}`))}</dt><dd>${dd}</dd></div>`;
    })
    .join('');

  const snoozeStake =
    ctx.settings.snooze === 'local' && overdue
      ? `<button class="stake stake--quiet" type="button" data-arg="reminder:${overdue.id}">Snooze the overdue one</button>`
      : '';
  const snoozeNote =
    ctx.settings.snooze === 'off'
      ? '<p class="state__note">Snoozing is not something PlantPal keeps yet.</p>'
      : '';

  const emptyPanel = `
      <section class="state state--empty" data-brief-item="state:empty">
        <div class="state__head"><h4 class="state__title">Nothing due today</h4>${stateId('state · empty', ctx.settings)}</div>
        <div class="empty-plot">
          <span class="glyph" aria-hidden="true">◌</span>
          <p class="state__note">No reminder is due. This is an empty plot with room in it, not a failure${next ? ` — the next check-in is ${due(ctx, next).toLowerCase()}` : ''}.</p>
        </div>
        <button class="stake stake--quiet" type="button">Add a reminder</button>
      </section>`;

  return (
    recapWrap(
      dueToday.length > 0 ? `${dueToday.length} due today` : 'Nothing due today',
      next ? `Next check-in ${due(ctx, next).toLowerCase()}.` : undefined,
      `Last synced ${timeLabel(ctx.now)}`,
      ['sk--row'],
    ) +
    full(`
      <section class="state" data-brief-item="action:/api/v1/reminders/**">
        <div class="state__head"><h4 class="state__title">What PlantPal will remind you of</h4>${stateId('action · /api/v1/reminders/**', ctx.settings)}</div>
        <p class="state__note">A reminder belongs to a plant and to a kind of care. Snoozing one moves that reminder and nothing else; it never silently reschedules the rest of your garden.</p>
        ${rows ? `<dl class="rows">${rows}</dl>` : ''}
        ${routine.length > 6 ? `<p class="state__note">${routine.length - 6} more reminders are not listed here.</p>` : ''}
        ${snoozeNote}
        <div class="btn-row">
          <button class="stake" type="button">Add a reminder</button>
          ${snoozeStake}
        </div>
      </section>
      ${routine.length === 0 ? emptyPanel : ''}`)
  );
}

function careHubBody(ctx: Ctx): string {
  const logs = ctx.logs;
  const last = logs[0];
  const lastWater = logs.find(l => l.careType === 'WATERING');
  const lastFeed = logs.find(l => l.careType === 'FERTILIZING');
  const nextWater = routineReminders(ctx).find(r => r.careType === 'WATERING');
  const off = ctx.settings.careLogPageSize === 0;

  const notFetched = `
      <section class="state state--empty" data-brief-item="action:/api/v1/care/**">
        <div class="state__head"><h4 class="state__title">Care, and what you actually did</h4>${stateId('action · /api/v1/care/**', ctx.settings)}</div>
        <div class="empty-plot"><span class="glyph" aria-hidden="true">◌</span>
        <p class="state__note">Care history not fetched — turn it on in Settings · Data &amp; Sync.</p></div>
      </section>`;

  const record = `
      <section class="state" data-brief-item="action:/api/v1/care/**">
        <div class="state__head"><h4 class="state__title">Care, and what you actually did</h4>${stateId('action · /api/v1/care/**', ctx.settings)}</div>
        <p class="state__note">The guide below is what a plant wants. This is the record of what it got: every watering, feeding and repotting you logged, and the schedule they are measured against.</p>
        <dl class="rows">
          <div class="row"><dt>Watered</dt><dd>${lastWater ? `${agoLabel(lastWater.performedAt, ctx.now, ctx.settings.dateStyle)}${lastWater.notes ? ` · ${esc(lastWater.notes)}` : ''}` : 'Not logged yet'}</dd></div>
          <div class="row"><dt>Fed</dt><dd>${lastFeed ? `${agoLabel(lastFeed.performedAt, ctx.now, ctx.settings.dateStyle)}${lastFeed.notes ? ` · ${esc(lastFeed.notes)}` : ''}` : 'Not logged yet'}</dd></div>
          <div class="row"><dt>Next due</dt><dd>${nextWater ? due(ctx, nextWater) : 'No schedule yet'}</dd></div>
        </dl>
        <div class="btn-row">
          <button class="stake" type="button">Log a watering</button>
          ${nextWater ? `<button class="stake stake--quiet" type="button" data-arg="reminder:${nextWater.id}">Change the schedule</button>` : ''}
        </div>
      </section>`;

  return (
    recapWrap(
      off ? 'Care history not fetched' : loggedLine(ctx),
      off
        ? undefined
        : last
          ? `Last ${careLabel(last.careType).toLowerCase()} ${agoLabel(last.performedAt, ctx.now, ctx.settings.dateStyle).toLowerCase()}.`
          : 'Nothing logged yet.',
      `Cached ${timeLabel(ctx.now)} · the record only grows`,
      ['sk--sub', 'sk--line', 'sk--line is-short'],
    ) +
    full(`
      ${off ? notFetched : record}
      <section>
        <h3 class="sec">Watering</h3>
        <p>Water slowly until it drains from the bottom, then let the top 2–3 cm dry before the next drink. If the leaves are drooping and the soil is dry all the way down, see ${linkTo(ctx.drawn, 'n-problems', 'underwatering')}.</p>
        <dl class="rows">
          <div class="row"><dt>Ideal moisture</dt><dd>40 – 60%</dd></div>
          <div class="row"><dt>Soil</dt><dd>Well-draining, rich</dd></div>
          <div class="row"><dt>Pot</dt><dd>With drainage</dd></div>
        </dl>
      </section>`)
  );
}

function journalHubBody(ctx: Ctx, collapsed: number): string {
  const logs = ctx.logs;
  const feed = logs
    .slice(0, 2)
    .map(
      l =>
        `<div class="feed__row"><span class="feed__when">${dateLabel(l.performedAt)}</span><span>${careLabel(l.careType)} · ${linkTo(ctx.drawn, `n-plant-${l.plantId}`, esc(l.plantNickname ?? `Plant ${l.plantId}`))}</span><span class="feed__val">${l.notes ? esc(l.notes) : '·'}</span></div>`,
    )
    .join('');
  const empty = `
      <section class="state state--empty" data-brief-item="state:empty">
        <div class="state__head"><h4 class="state__title">Nothing written yet · a good place to start</h4>${stateId('state · empty', ctx.settings)}</div>
        <div class="empty-plot"><span class="glyph" aria-hidden="true">◌</span>
        <p class="state__note">The journal fills itself: every watering you log writes a line here.</p></div>
        <button class="stake stake--quiet" type="button">Log a watering</button>
      </section>`;
  return (
    recapWrap(
      logs.length === 0 ? 'Nothing written yet' : entriesLine(ctx),
      logs.length === 0 ? 'A good place to start.' : 'Watering, notes and photos, newest first.',
      `Last synced ${timeLabel(ctx.now)}`,
      ['sk--row', 'sk--row'],
    ) +
    full(
      logs.length === 0
        ? empty
        : `
      <section>
        <h3 class="sec">${entriesLine(ctx)}</h3>
        <p>${collapsed > 0 ? `${wordNumber(logs.length)} is four or more, so the same rule applies here as to species and to plants: the two most recent entries are drawn, and one node holds the other ${wordNumber(collapsed)}.` : `${wordNumber(logs.length)} is fewer than four, so every entry is drawn as its own node beside this card.`}</p>
        <div class="feed">${feed}</div>
        ${collapsed > 0 ? `<button class="hop hop--block" type="button" data-goto="n-journal-more" style="margin-top:var(--hbm-space-4)">Reach the other ${collapsed} entries <small>+${collapsed} more</small></button>` : ''}
      </section>`,
    )
  );
}

function logBody(l: CareLogDto, ctx: Ctx): string {
  return (
    recapWrap(
      `${careLabel(l.careType)} · ${esc(l.plantNickname ?? `Plant ${l.plantId}`)}`,
      agoLabel(l.performedAt, ctx.now, ctx.settings.dateStyle),
      `Last synced ${timeLabel(ctx.now)}`,
      ['sk--line'],
    ) +
    full(`
      <section class="state" data-brief-item="action:/api/v1/care/**">
        <div class="state__head"><h4 class="state__title">This entry</h4>${stateId(`action · /api/v1/care/plant/${l.plantId}`, ctx.settings)}</div>
        <dl class="rows">
          <div class="row"><dt>Care</dt><dd>${careLabel(l.careType)}</dd></div>
          <div class="row"><dt>Plant</dt><dd>${linkTo(ctx.drawn, `n-plant-${l.plantId}`, esc(l.plantNickname ?? `Plant ${l.plantId}`))}</dd></div>
          <div class="row"><dt>When</dt><dd class="v mono">${dateLabel(l.performedAt)} ${timeLabel(l.performedAt)}</dd></div>
        </dl>
        ${l.notes ? `<p>${esc(l.notes)}</p>` : '<p class="state__note">No note was written with this one.</p>'}
      </section>`)
  );
}

function treatmentsHubBody(ctx: Ctx): string {
  const active = ctx.treatments.filter(t => t.status === 'IN_PROGRESS');
  const drafts = ctx.treatments.filter(t => t.status === 'DRAFT');
  const rows = [...active, ...drafts]
    .map(
      t =>
        `<div class="row"><dt>${linkTo(ctx.drawn, `n-treatment-${t.id}`, esc(t.plantNickname ?? plantName(ctx, t.plantId)))}</dt><dd>${esc(t.diseaseName)} · ${courseRecap(ctx, t)}</dd></div>`,
    )
    .join('');
  const line =
    active.length === 0 && drafts.length === 0
      ? 'No course running'
      : `${active.length} running${drafts.length ? ` · ${drafts.length} waiting for a plan` : ''}`;
  const empty = `
      <section class="state state--empty" data-brief-item="state:empty">
        <div class="state__head"><h4 class="state__title">No course is running</h4>${stateId('state · empty', ctx.settings)}</div>
        <div class="empty-plot"><span class="glyph" aria-hidden="true">◌</span>
        <p class="state__note">When a scan finds a problem, start one from the plant.</p></div>
        <button class="stake stake--quiet" type="button">Start a treatment plan</button>
      </section>`;
  return (
    recapWrap(
      line,
      'A course is a sequence with an end, not a setting.',
      `Last synced ${timeLabel(ctx.now)} · steps may have been ticked elsewhere`,
      ['sk--row', 'sk--row'],
    ) +
    full(
      rows
        ? `
      <section class="state" data-brief-item="action:/api/v1/treatments/**">
        <div class="state__head"><h4 class="state__title">The courses you are running</h4>${stateId('action · /api/v1/treatments/**', ctx.settings)}</div>
        <dl class="rows">${rows}</dl>
        <div class="btn-row"><button class="stake" type="button">Start a treatment plan</button></div>
      </section>`
        : empty,
    )
  );
}

function plantName(ctx: Ctx, plantId: number): string {
  return ctx.plants.find(p => p.id === plantId)?.nickname ?? `Plant ${plantId}`;
}

function treatmentBody(t: TreatmentDto, ctx: Ctx): string {
  const steps = stepsOf(ctx, t);
  const open = steps.filter(s => s.enabled);
  const paused = isPaused(ctx, t);
  const nextOpen = open[0];
  const dueStep = open.find(s => isDue(s.nextDueAt, ctx.now, ctx.settings.dueWindow));
  const planHere = hasPlan(ctx, t);
  const limit = ctx.rateLimited[t.id];

  // 1 — what this is
  const model = t.diseaseDescriptionModel ? esc(t.diseaseDescriptionModel) : 'PlantPal';
  const description =
    t.descriptionStatus === 'READY' && t.diseaseDescription
      ? `<section><h3 class="sec">What this is</h3><p>${esc(t.diseaseDescription)}</p><p class="state__note">Described by ${model}.</p></section>`
      : t.descriptionStatus === 'FAILED'
        ? `<section class="state state--error" data-brief-item="state:error">
             <div class="state__head"><h4 class="state__title">The write-up did not come back</h4>${stateId('state · error', ctx.settings)}</div>
             <p class="state__note">PlantPal's model answered with an error at ${timeLabel(ctx.now)}. The treatment and its steps are kept. Nothing moved.</p>
             <div class="btn-row"><button class="stake" type="button" data-arg="treatment:${t.id}">Write it up again</button></div>
             <p class="state__note">The ${linkTo(ctx.drawn, 'n-care', 'care guide')} covers the same ground without a model.</p>
           </section>`
        : `<section class="state state--loading" data-brief-item="state:loading">
             <div class="state__head"><h4 class="state__title">Still describing this disease</h4>${stateId('state · pending', ctx.settings)}</div>
             <p class="state__note">Usually about fifteen seconds. The course keeps its place.</p>
             <div class="sk sk--row"></div><div class="sk sk--row"></div>
           </section>`;

  // 2 — the course
  const stepReason = !planHere
    ? 'The plan did not come back.'
    : paused
      ? 'The course is paused on this device.'
      : !nextOpen
        ? 'Every step is done.'
        : 'Nothing is due today.';
  const stepStake =
    paused || !dueStep
      ? `<button class="stake" type="button" aria-disabled="true" data-reason="${stepReason}">Mark today done</button>`
      : `<button class="stake" type="button" data-arg="reminder:${dueStep.id}">Mark today done</button>`;
  const pauseStake =
    ctx.settings.pause === 'local' && t.treatmentPlanId != null
      ? `<button class="stake stake--quiet" type="button" data-arg="plan:${t.treatmentPlanId}">${paused ? 'Resume this course' : 'Pause this course'}</button>`
      : '';
  const limitPanel = limit
    ? `<section class="state state--error" data-limit="reached" data-brief-item="state:limit">
         <div class="state__head"><h4 class="state__title">You have used today's AI plans</h4>${stateId('state · limit', ctx.settings)}</div>
         <p class="state__note">They come back in ${Math.max(1, Math.round(limit.retryAfterSeconds / 60))} minutes, and everything else still works.</p>
         <div class="btn-row"><button class="stake stake--quiet" type="button" data-arg="treatment:${t.id}">Add the steps by hand</button></div>
       </section>`
    : '';
  const courseButtons =
    t.status === 'DRAFT'
      ? limit
        ? ''
        : `<div class="btn-row"><button class="stake" type="button" data-arg="treatment:${t.id}">Craft the treatment plan</button></div>`
      : t.status === 'IN_PROGRESS'
        ? `<div class="btn-row">${stepStake}${pauseStake}<button class="stake stake--quiet" type="button" data-arg="treatment:${t.id}">Finish this course</button></div>`
        : `<p class="state__note">${t.status === 'DISMISSED' ? 'Dismissed' : 'Finished'} ${t.completedAt ? dateLabel(t.completedAt) : dateLabel(t.createdAt)}. It stays on ${esc(plantName(ctx, t.plantId))} as part of its story.</p>`;

  const course = `
      <section class="state" data-brief-item="action:/api/v1/treatment-plans/**">
        <div class="state__head"><h4 class="state__title">The course you are running</h4>${stateId('action · /api/v1/treatment-plans/**', ctx.settings)}</div>
        <p class="state__note">A plan is a sequence with an end, not a setting. You can start one, mark a step done, or pause it while you are away.</p>
        <dl class="rows">
          <div class="row"><dt>Running</dt><dd>${esc(t.diseaseName)} · ${courseRecap(ctx, t)}</dd></div>
          <div class="row"><dt>Next step</dt><dd>${nextOpen ? `${due(ctx, nextOpen)} · ${esc(nextOpen.instruction ?? careLabel(nextOpen.careType))}` : planHere ? 'Every step is done' : 'Not fetched — the plan did not come back'}</dd></div>
          <div class="row"><dt>Treating</dt><dd>${linkTo(ctx.drawn, `n-plant-${t.plantId}`, esc(plantName(ctx, t.plantId)))}</dd></div>
          ${t.treatmentPlanModel ? `<div class="row"><dt>Plan crafted using</dt><dd>${esc(t.treatmentPlanModel)}</dd></div>` : ''}
        </dl>
        ${courseButtons}
      </section>
      ${limitPanel}`;

  // 3 — the steps: a mutation, never an exit (no link, no hop, inside data-course)
  const stepRows = steps
    .map(s => {
      const isDueRow = dueStep != null && s.id === dueStep.id;
      return `<div class="row" data-step-id="${s.id}" data-done="${!s.enabled}" data-due="${isDueRow}" data-paused="${paused}"><dt>${s.stepOrder ?? ''} · ${esc(s.instruction ?? careLabel(s.careType))}</dt><dd>${s.enabled ? due(ctx, s) : `Done · ${dateLabel(s.completedAt ?? s.nextDueAt)}`}</dd></div>`;
    })
    .join('');
  const markStake =
    !paused && dueStep
      ? `<button class="stake" type="button" data-arg="reminder:${dueStep.id}" aria-pressed="false" style="margin-top:var(--hbm-space-4)">Mark step ${dueStep.stepOrder ?? 1} as done</button>`
      : '';
  const stepsSection = steps.length
    ? `<section><h3 class="sec">${wordNumber(steps.length)} ${steps.length === 1 ? 'step' : 'steps'}</h3><dl class="rows" data-course>${stepRows}</dl>${markStake}</section>`
    : planHere
      ? ''
      : `<section class="state state--unknown" data-brief-item="state:unknown">
        <div class="state__head"><h4 class="state__title">The steps did not come back</h4>${stateId('state · unknown', ctx.settings)}</div>
        <p class="state__note">PlantPal did not answer for this course's plan, so its steps are not drawn. What they are is unknown here, not empty. The course itself is kept.</p>
      </section>`;

  const recapNote = t.plantNickname ?? plantName(ctx, t.plantId);
  return (
    recapWrap(
      courseRecap(ctx, t),
      esc(recapNote),
      `Course read ${timeLabel(ctx.now)}`,
      ['sk--row', 'sk--row'],
    ) +
    full(`
      ${description}
      ${course}
      ${stepsSection}
      <section class="state" data-brief-item="action:/api/v1/treatments/**">
        <div class="state__head"><h4 class="state__title">This course</h4>${stateId(`action · /api/v1/treatments/${t.id}`, ctx.settings)}</div>
        <dl class="rows">
          <div class="row"><dt>Plant</dt><dd>${linkTo(ctx.drawn, `n-plant-${t.plantId}`, esc(plantName(ctx, t.plantId)))}</dd></div>
          <div class="row"><dt>Started</dt><dd>${t.startedAt ? dateLabel(t.startedAt) : 'Not started'}</dd></div>
          <div class="row"><dt>Written by</dt><dd>${model}</dd></div>
        </dl>
      </section>`)
  );
}

function speciesBody(s: SpeciesDto, plantsOfSpecies: PlantDto[], drawn: Set<string>, settings: AssemblySettings): string {
  const rows = plantsOfSpecies
    .slice(0, 3)
    .map(p => `<div class="row"><dt>${linkTo(drawn, `n-plant-${p.id}`, esc(p.nickname))}</dt><dd>${waterLine(p)}</dd></div>`)
    .join('');
  return (
    recapWrap(`${plantsOfSpecies.length} of your plants`, esc(s.scientificName)) +
    full(`
      <div class="plate">
        <div class="plate__specimen" aria-hidden="true"></div>
        <div>
          <h3 class="plate__name">${esc(s.commonName ?? s.scientificName)}</h3>
          <span class="plate__binomial">${esc(s.scientificName)}</span>
        </div>
      </div>
      <section class="state" data-brief-item="action:/api/v1/species/**">
        <div class="state__head"><h4 class="state__title">This species</h4>${stateId(`action · /api/v1/species/${s.id}`, settings)}</div>
        ${rows ? `<dl class="rows">${rows}</dl>` : '<p class="state__note">None of your plants are this species yet.</p>'}
      </section>`)
  );
}

function scanStatusLine(i: IdentificationDto): string {
  const name = esc(i.commonName ?? i.species ?? 'Unknown');
  if (i.status === 'FAILED') return `<span class="tag tag--ailing">Failed</span>`;
  if (i.status === 'PENDING' || i.status === 'PROCESSING') return `<span class="tag tag--watch">Still identifying</span>`;
  return name;
}

function identBody(idents: IdentificationDto[], drawn: Set<string>, settings: AssemblySettings): string {
  const latest = idents[0];
  const feed = idents
    .slice(0, 4)
    .map(i => `<div class="feed__row"><span class="feed__when">${esc(i.createdAt.slice(0, 10))}</span><span>${linkTo(drawn, `n-scan-${i.id}`, esc(i.commonName ?? i.species ?? 'Scan #' + i.id))}</span><span class="feed__val">${esc(i.status)}</span></div>`)
    .join('');
  const failedPanel =
    latest && latest.status === 'FAILED'
      ? `<section class="state state--error">
           <div class="state__head"><h4 class="state__title">The last scan did not come back</h4>${stateId(`action · /api/v1/identifications/**`, settings)}</div>
           <p class="state__note">Your photo is kept — nothing was lost. Retry sits here, in the node.</p>
           <div class="btn-row"><button class="stake" type="button">Try the scan again</button><button class="stake stake--quiet" type="button">Identify by hand</button></div>
         </section>`
      : '';
  const pendingPanel =
    latest && (latest.status === 'PENDING' || latest.status === 'PROCESSING')
      ? `<section class="state state--loading">
           <div class="state__head"><h4 class="state__title">A scan is being analysed</h4>${stateId(`data · polling`, settings)}</div>
           <p class="state__note">The answer arrives into this node — the geography holds while it does.</p>
         </section>`
      : '';
  return (
    recapWrap(latest ? scanStatusLine(latest) : 'No scans yet') +
    full(`
      ${failedPanel}${pendingPanel}
      <section class="state" data-brief-item="action:/api/v1/identifications/**">
        <div class="state__head"><h4 class="state__title">Your identifications</h4>${stateId(`action · /api/v1/identifications/**`, settings)}</div>
        ${feed ? `<div class="feed">${feed}</div>` : '<p class="state__note">Photograph a plant and the answer lands here.</p>'}
        <div class="btn-row"><button class="stake" type="button">Identify a plant</button></div>
      </section>`)
  );
}

/** Wrap a label in a travelling doc-link when the target node is drawn. */
function linkTo(drawn: Set<string>, id: string, label: string): string {
  return drawn.has(id) ? `<a class="doc-link" href="#${id}" data-goto="${id}">${label}</a>` : label;
}

function scanBody(i: IdentificationDto, drawn: Set<string>, settings: AssemblySettings): string {
  const name = esc(i.commonName ?? i.species ?? 'Unknown plant');
  const plantNode = i.plantId != null ? `n-plant-${i.plantId}` : null;
  const plantRow = plantNode
    ? `<div class="row"><dt>Plant</dt><dd>${linkTo(drawn, plantNode, 'Open the plant')}</dd></div>`
    : '<div class="row"><dt>Plant</dt><dd>Not added to the garden yet</dd></div>';
  return (
    recapWrap(scanStatusLine(i), esc(i.createdAt.slice(0, 10))) +
    full(`
      <section class="state" data-brief-item="action:/api/v1/identifications/**">
        <div class="state__head"><h4 class="state__title">This scan</h4>${stateId(`action · /api/v1/identifications/${i.id}`, settings)}</div>
        <dl class="rows">
          <div class="row"><dt>Answer</dt><dd>${name}</dd></div>
          ${i.species ? `<div class="row"><dt>Species</dt><dd>${esc(i.species)}</dd></div>` : ''}
          <div class="row"><dt>Status</dt><dd>${esc(i.status)}</dd></div>
          <div class="row"><dt>When</dt><dd class="v mono">${esc(i.createdAt.slice(0, 10))}</dd></div>
          ${plantRow}
        </dl>
        ${i.status === 'FAILED' ? '<div class="btn-row"><button class="stake" type="button">Try the scan again</button></div>' : ''}
      </section>`)
  );
}

function gardenBody(plants: PlantDto[], drawn: Set<string>, ctx: Ctx): string {
  const ranked = [...plants].sort(plantByOwed);
  const rows = ranked
    .slice(0, 3)
    .map(p => `<div class="row"><dt>${linkTo(drawn, `n-plant-${p.id}`, esc(p.nickname))}</dt><dd>${waterLine(p)}</dd></div>`)
    .join('');
  return (
    recapWrap(`${plants.length} plants`, undefined, `Last synced ${timeLabel(ctx.now)} · watering counts may be stale`, ['sk--sub', 'sk--row', 'sk--row']) +
    full(`
      <section class="state" data-brief-item="action:/api/v1/plants/**">
        <div class="state__head"><h4 class="state__title">Your plants</h4>${stateId(`action · /api/v1/plants/**`, ctx.settings)}</div>
        ${rows ? `<dl class="rows">${rows}</dl>` : '<p class="state__note">No plants yet — add the first one.</p>'}
        <div class="btn-row"><button class="stake" type="button">Add a plant</button></div>
      </section>`)
  );
}

function accountBody(user: WorldSources['user'], now: string, settings: AssemblySettings): string {
  const who = user ? `${esc(user.firstName)} ${esc(user.lastName)}` : 'Signed in';
  return (
    recapWrap(
      user ? esc(user.email) : 'Your session',
      undefined,
      `Session read ${timeLabel(now)}`,
      ['sk--sub', 'sk--row', 'sk--row'],
    ) +
    full(`
      <section class="state" data-brief-item="action:POST /api/v1/auth/login">
        <div class="state__head"><h4 class="state__title">Signing in</h4>${stateId(`action · POST /api/v1/auth/login`, settings)}</div>
        <p class="state__note">Sign-in lives on the classic PlantPal page — the session it issues is the one this atlas is using now.</p>
      </section>
      <section class="state" data-brief-item="action:/api/v1/users/**">
        <div class="state__head"><h4 class="state__title">You, as PlantPal holds you</h4>${stateId(`action · /api/v1/users/**`, settings)}</div>
        <dl class="rows">
          <div class="row"><dt>Name</dt><dd>${who}</dd></div>
          ${user ? `<div class="row"><dt>Email</dt><dd class="v mono">${esc(user.email)}</dd></div>` : ''}
        </dl>
      </section>`)
  );
}

function platformBody(settings: AssemblySettings): string {
  return (
    recapWrap('Health · feeds') +
    full(`
      <section class="state" data-brief-item="action:\`app.health\`">
        <div class="state__head"><h4 class="state__title">Health check</h4>${stateId(`action · app.health`, settings)}</div>
        <p class="state__note">The backend behind this world. A check runs end-to-end and reports here.</p>
        <div class="btn-row"><button class="stake stake--quiet" type="button">Check health again</button></div>
      </section>
      <section class="state state--unknown" data-brief-item="data:dimension.event">
        <div class="state__head"><h4 class="state__title">Dimension events</h4>${stateId(`data · dimension.event`, settings)}</div>
        <p class="state__note">Not fetched yet — the platform feed lands in a later round.</p>
      </section>
      <section class="state state--unknown" data-brief-item="data:state.event">
        <div class="state__head"><h4 class="state__title">State events</h4>${stateId(`data · state.event`, settings)}</div>
        <p class="state__note">Not fetched yet.</p>
      </section>`)
  );
}

/** A deferred-family node body (coverage-scope: rounds 2/3). */
function deferredBody(title: string, id: string, note: string, settings: AssemblySettings): string {
  return (
    recapWrap('Coming with the care loop') +
    full(`
      <section class="state state--empty" data-brief-item="action:${id}">
        <div class="state__head"><h4 class="state__title">${title}</h4>${stateId(`action · ${id}`, settings)}</div>
        <div class="empty-plot"><span aria-hidden="true">◌</span></div>
        <p class="state__note">${note}</p>
      </section>`)
  );
}

/** Which node wears a family's failure — degradation is per-node material (C25). */
const FAILURE_NODE: Record<string, string[]> = {
  reminders: ['n-reminders'],
  dashboard: ['n-today'],
  // the journal is built from the same care rows — an outage is not an empty plot
  care: ['n-care', 'n-journal'],
  treatments: ['n-treatments'],
  users: ['n-account'],
};

/** A spoken noun for each family — a slug never belongs in a sentence. */
const FAILURE_NAME: Record<string, string> = {
  reminders: 'your reminders',
  dashboard: "today's count",
  care: 'your care history',
  treatments: 'the treatments',
  users: 'your account',
  'treatment-plans': 'the treatment plan',
};

function failureName(f: FamilyFailure): string {
  return FAILURE_NAME[f.family] ?? f.family;
}

/**
 * Which nodes wear a family's failure. `treatment-plans` is stamped with the PLAN
 * id, not the treatment id — resolve it back through the treatments, or the outage
 * lands on a node that cannot exist and is silently swallowed.
 */
function failureNodeIds(f: FamilyFailure, treatments: TreatmentDto[]): string[] {
  if (f.family === 'treatment-plans') {
    if (f.ref == null) return [];
    const t = treatments.find(x => x.treatmentPlanId === f.ref);
    return t ? [`n-treatment-${t.id}`] : [];
  }
  return FAILURE_NODE[f.family] ?? [];
}

/** The failure, written inside the node it belongs to: fact, time, fate, ways on. */
function failureBody(f: FamilyFailure, extraWay: boolean, settings: AssemblySettings): string {
  const note = `PlantPal answered with ${f.status} at ${timeLabel(f.at)}. Everything already drawn is kept; nothing moved.`;
  return (
    recapWrap('Did not come back', esc(f.message ?? undefined)) +
    full(`
      <section class="state state--error" data-brief-item="state:error">
        <div class="state__head"><h4 class="state__title">${esc(failureName(f))} did not come back</h4>${stateId(`state · error`, settings)}</div>
        <p class="state__note">${note}</p>
        <div class="btn-row"><button class="stake" type="button">Fetch this region</button>${
          extraWay ? '<button class="stake stake--quiet" type="button">Count again</button>' : ''
        }</div>
      </section>`)
  );
}

/** Loader facts beside the board — never rendered, read by the chrome and actions. */
function buildMeta(sources: WorldSources): WorldMeta {
  const { now, reminders, plants, identifications, treatments, plansById, paused, failures } =
    sources;
  const dueReminders = reminders
    .filter(r => r.enabled && isDue(r.nextDueAt, now, sources.settings.dueWindow))
    .map(r => ({
      id: r.id,
      nextDueAt: r.nextDueAt,
      plantId: r.plantId,
      label: r.plantNickname
        ? `${careLabel(r.careType)} · ${r.plantNickname}`
        : careLabel(r.careType),
    }));

  const scansByPlant: Record<number, number> = {};
  for (const i of [...identifications].sort((a, b) => a.createdAt.localeCompare(b.createdAt))) {
    if (i.plantId != null) scansByPlant[i.plantId] = i.id;
  }

  const treatmentsIndex: WorldMeta['treatmentsIndex'] = {};
  for (const t of treatments) {
    const plan = t.treatmentPlanId != null ? plansById[t.treatmentPlanId] : undefined;
    const nextStep = plan?.steps
      .filter(st => st.enabled)
      .sort((a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0))[0];
    treatmentsIndex[t.id] = {
      plantId: t.plantId,
      status: t.status,
      planId: t.treatmentPlanId ?? undefined,
      nextStepId: nextStep?.id,
      nextStepOrder: nextStep?.stepOrder,
      paused: t.treatmentPlanId != null && paused.includes(t.treatmentPlanId),
    };
  }

  return {
    syncedAt: now,
    reminders,
    dueReminders,
    plantsIndex: plants.map(p => ({
      id: p.id,
      nickname: p.nickname,
      lastScanId: p.lastScanId ?? scansByPlant[p.id],
    })),
    treatmentsIndex,
    scansByPlant,
    // only an explicitly pending description on a course still running is worth
    // polling for — a finished or dismissed course will never write one again
    hasPendingDescription: treatments.some(
      t =>
        t.descriptionStatus === 'PENDING' &&
        t.status !== 'COMPLETED' &&
        t.status !== 'DISMISSED',
    ),
    failures,
  };
}

// ── the assembly ─────────────────────────────────────────────────────────────

/**
 * Assemble the world from live PlantPal data — the round-1 spine of the
 * mission's coverage scope: plants, species, identifications (async), auth,
 * platform. Deterministic in its inputs: stable ids, density collapse, BFS cell
 * layout (C7). Deferred families render as honest deferred panels, never blanks.
 */
export function assembleWorld(sources: WorldSources): WorldData {
  const { plants, species, identifications, user } = sources;
  const nodes: DraftNode[] = [];
  const edges: Edge[] = [];
  const add = (n: DraftNode) => nodes.push(n);
  const link = (a: string, b: string) => edges.push([a, b]);

  const issues = plants.filter(p => p.healthStatus === 'ISSUES_DETECTED').length;
  const needWater = plants.filter(p => (p.nextWaterDays ?? 99) <= 0).length;
  const latestScan = [...identifications].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const hasPendingScan = latestScan.some(i => i.status === 'PENDING' || i.status === 'PROCESSING');
  const latestFailedScanId = latestScan.find(i => i.status === 'FAILED')?.id;

  // what will actually be drawn (density rule) — known up front so every row
  // in every body can be a travelling doc-link to a real node
  const rankedPlantsAll = [...plants].sort(plantByOwed);
  const drawnPlants = rankedPlantsAll.length < DENSITY_CAP ? rankedPlantsAll : rankedPlantsAll.slice(0, 2);
  const drawnScans = latestScan.length < DENSITY_CAP ? latestScan : latestScan.slice(0, 2);

  const overdueByReminder: Record<number, number> = {};
  for (const r of [
    ...(sources.dashboard?.overdueReminders ?? []),
    ...(sources.dashboard?.todayReminders ?? []),
  ]) {
    if (r.daysOverdue != null) overdueByReminder[r.id] = r.daysOverdue;
  }
  const logs = Object.values(sources.careLogsByPlant)
    .flat()
    .sort((a, b) => b.performedAt.localeCompare(a.performedAt) || b.id - a.id);

  const ctx: Ctx = {
    now: sources.now,
    settings: sources.settings,
    drawn: new Set<string>(),
    reminders: sources.reminders,
    logs,
    treatments: sources.treatments,
    plansById: sources.plansById,
    plants,
    paused: sources.paused,
    snoozed: sources.snoozed,
    rateLimited: sources.rateLimited,
    overdueByReminder,
    logsPartial: Object.keys(sources.careLogsByPlant).length < plants.length,
  };

  const rankedTreatments = [...sources.treatments].sort(
    (a, b) => treatmentRank(ctx, a) - treatmentRank(ctx, b) || a.id - b.id,
  );
  const drawnTreatments =
    rankedTreatments.length < DENSITY_CAP ? rankedTreatments : rankedTreatments.slice(0, 2);
  const drawnLogs = logs.length < DENSITY_CAP ? logs : logs.slice(0, 2);
  const collapsedLogs = logs.length >= DENSITY_CAP ? logs.length - 2 : 0;

  // every drawn id is known before a single body is built, so a row can only
  // ever link somewhere that exists (a doc-link never points at a missing node)
  const drawn = new Set<string>([
    ...drawnPlants.map(p => `n-plant-${p.id}`),
    ...drawnScans.map(i => `n-scan-${i.id}`),
    ...drawnTreatments.map(t => `n-treatment-${t.id}`),
    ...drawnLogs.map(l => `n-log-${l.id}`),
    'n-care',
    ...(issues > 0 ? ['n-problems'] : []),
  ]);
  ctx.drawn = drawn;

  // hub
  add({ id: 'n-garden', glyph: '♣', kind: 'collection', kindLabel: 'Garden', name: 'My garden',
    recap: `${plants.length} plants · ${needWater} need water`, body: gardenBody(plants, drawn, ctx) });

  add({ id: 'n-account', glyph: '◉', kind: 'platform', kindLabel: 'Account', name: user ? `${user.firstName}'s account` : 'Your account',
    recap: user ? user.email : 'Signed in', body: accountBody(user, sources.now, sources.settings) });
  link('n-account', 'n-garden');

  add({ id: 'n-platform', glyph: '◈', kind: 'platform', kindLabel: 'Platform', name: 'Platform link',
    recap: 'Health · 2 feeds', body: platformBody(sources.settings) });
  link('n-account', 'n-platform');

  add({ id: 'n-ident', glyph: '◎', kind: 'platform', kindLabel: 'Identification', name: 'Identification',
    recap: latestScan[0] ? `Last scan · ${latestScan[0].status.toLowerCase()}` : 'No scans yet',
    state: latestScan[0]?.status === 'FAILED' ? 'failed' : undefined,
    body: identBody(latestScan, drawn, sources.settings) });
  link('n-garden', 'n-ident');

  add({ id: 'n-species', glyph: '❋', kind: 'collection', kindLabel: 'Collection', name: 'Species',
    recap: `${species.length} species`, state: species.length === 0 ? 'empty' : undefined,
    body: recapWrap(`${species.length} species`) + full(`
      <section class="state" data-brief-item="action:/api/v1/species/**">
        <div class="state__head"><h4 class="state__title">The species index</h4>${stateId('action · /api/v1/species/**', sources.settings)}</div>
        <p class="state__note">Everything you have identified or added by hand. A species is a reference thing — nothing here can be watered.</p>
        <div class="btn-row"><button class="stake" type="button">Add a species</button></div>
      </section>`) });
  link('n-garden', 'n-species');
  link('n-ident', 'n-species'); // the identify → species → plant path

  if (issues > 0) {
    add({ id: 'n-problems', glyph: '⚠', kind: 'problem', kindLabel: 'Problems', name: 'Problems',
      recap: `${issues} plant${issues === 1 ? '' : 's'} need attention`,
      body: recapWrap(`${issues} active`, undefined, `Last synced ${timeLabel(ctx.now)}`, ['sk--row', 'sk--row']) + full(`
        <section class="state" data-brief-item="action:/api/v1/plants/**">
          <div class="state__head"><h4 class="state__title">Plants needing attention</h4>${stateId('data · healthStatus', sources.settings)}</div>
          <dl class="rows">${plants.filter(p => p.healthStatus === 'ISSUES_DETECTED').slice(0, 3)
            .map(p => {
              const t = ctx.treatments.find(x => x.plantId === p.id && (x.status === 'DRAFT' || x.status === 'IN_PROGRESS'));
              const course = t ? ` · ${linkTo(drawn, `n-treatment-${t.id}`, courseRecap(ctx, t))}` : '';
              return `<div class="row"><dt>${linkTo(drawn, `n-plant-${p.id}`, esc(p.nickname))}</dt><dd>${healthTag(p.healthStatus)}${course}</dd></div>`;
            }).join('')}</dl>
          ${drawnTreatments.filter(t => plants.some(p => p.id === t.plantId && p.healthStatus === 'ISSUES_DETECTED'))
            .map(t => `<button class="hop hop--block" type="button" data-goto="n-treatment-${t.id}" style="margin-top:var(--hbm-space-4)">Open the treatment plan <small>${stepsOf(ctx, t).length} steps</small></button>`).join('')}
        </section>`) });
    link('n-garden', 'n-problems');
  }

  // each scan is a node of its own (the classic scan-detail modal, as geography)
  emitCollapsed(latestScan, 'n-ident', {
    kind: 'platform', kindLabel: 'Scan', aggregateId: 'n-scans-more', aggregateName: 'more scans',
    toNode: i => ({ id: `n-scan-${i.id}`, glyph: '◎', kind: 'platform', kindLabel: 'Scan',
      name: i.commonName ?? i.species ?? `Scan #${i.id}`, recap: `${i.status.toLowerCase()} · ${i.createdAt.slice(0, 10)}`,
      state: i.status === 'FAILED' ? 'failed' : undefined, body: scanBody(i, drawn, sources.settings) }),
  }, add, link);
  // a scan with a plant in the garden veins to it (identify → plant path)
  for (const i of drawnScans) {
    if (i.plantId != null && drawn.has(`n-plant-${i.plantId}`)) link(`n-scan-${i.id}`, `n-plant-${i.plantId}`);
  }

  // the remaining classic pages, as nodes (chat + home dashboard + treatments)
  add({ id: 'n-ask', glyph: '✎', kind: 'guide', kindLabel: 'Companion', name: 'Ask PlantPal',
    recap: 'Coming soon', state: 'empty',
    body: deferredBody('Ask PlantPal', '/api/v1/chat/**', 'The companion arrives in a later round — it will answer about the plants on this board.', sources.settings) });
  link('n-garden', 'n-ask');

  add({ id: 'n-today', glyph: '◷', kind: 'guide', kindLabel: 'Dashboard', name: 'Today',
    recap: 'Counts land in round 3', state: 'empty',
    body: deferredBody("Today's summary", '/api/v1/dashboard/**', 'Counts land in round 3 — the reminders they count are already on this board.', sources.settings) });
  link('n-garden', 'n-today');
  link('n-today', 'n-reminders');

  // ── the care loop ──────────────────────────────────────────────────────────
  const routine = routineReminders(ctx);
  const dueToday = routine.filter(
    r => !isSnoozed(ctx, r) && daysUntil(r.nextDueAt, ctx.now, ctx.settings.dueWindow) === 0,
  ).length;
  add({ id: 'n-reminders', glyph: '◷', kind: 'journal', kindLabel: 'Reminders', name: 'Reminders',
    recap: dueToday > 0 ? `${dueToday} due today` : 'Nothing due today',
    state: routine.length === 0 ? 'empty' : undefined,
    body: reminderHubBody(ctx) });
  link('n-garden', 'n-reminders');

  add({ id: 'n-care', glyph: '☂', kind: 'guide', kindLabel: 'Guide', name: 'Care',
    recap: sources.settings.careLogPageSize === 0 ? 'Care history not fetched' : loggedLine(ctx),
    state: sources.settings.careLogPageSize === 0 ? 'empty' : undefined,
    body: careHubBody(ctx) });
  link('n-garden', 'n-care');

  add({ id: 'n-journal', glyph: '▤', kind: 'journal', kindLabel: 'Journal', name: 'Journal',
    recap: logs.length === 0 ? 'Nothing written yet' : entriesLine(ctx),
    state: logs.length === 0 ? 'empty' : undefined,
    body: journalHubBody(ctx, collapsedLogs) });
  link('n-garden', 'n-journal');
  link('n-care', 'n-journal');

  emitCollapsed(logs, 'n-journal', {
    kind: 'journal', kindLabel: 'Entry', aggregateId: 'n-journal-more', aggregateName: 'more entries',
    toNode: l => ({ id: `n-log-${l.id}`, glyph: '▤', kind: 'journal', kindLabel: 'Entry',
      name: `${dateLabel(l.performedAt)} · ${careLabel(l.careType).toLowerCase()}`,
      recap: `${careLabel(l.careType)} · ${l.plantNickname ?? plantName(ctx, l.plantId)}`,
      body: logBody(l, ctx) }),
  }, add, link);
  for (const l of drawnLogs) {
    if (drawn.has(`n-plant-${l.plantId}`)) link(`n-log-${l.id}`, `n-plant-${l.plantId}`);
  }

  const running = sources.treatments.filter(t => t.status === 'IN_PROGRESS').length;
  const drafts = sources.treatments.filter(t => t.status === 'DRAFT').length;
  add({ id: 'n-treatments', glyph: '◈', kind: 'problem', kindLabel: 'Treatment', name: 'Treatments',
    recap: running + drafts === 0 ? 'No course running' : `${running} running${drafts ? ` · ${drafts} waiting for a plan` : ''}`,
    state: sources.treatments.length === 0 ? 'empty' : undefined,
    body: treatmentsHubBody(ctx) });
  link('n-garden', 'n-treatments');

  emitCollapsed(rankedTreatments, 'n-treatments', {
    kind: 'problem', kindLabel: 'Course', aggregateId: 'n-treatments-more', aggregateName: 'more courses',
    toNode: t => ({ id: `n-treatment-${t.id}`, glyph: '◈', kind: 'problem', kindLabel: 'Course',
      name: t.diseaseName, recap: courseRecap(ctx, t),
      recapNote: t.plantNickname ?? plantName(ctx, t.plantId),
      state:
        t.descriptionStatus === 'PENDING' && t.status !== 'COMPLETED' && t.status !== 'DISMISSED'
          ? 'loading'
          : t.status === 'COMPLETED' || t.status === 'DISMISSED'
            ? 'archived'
            : undefined,
      body: treatmentBody(t, ctx) }),
  }, add, link);
  for (const t of drawnTreatments) {
    if (drawn.has(`n-plant-${t.plantId}`)) link(`n-treatment-${t.id}`, `n-plant-${t.plantId}`);
    link(`n-treatment-${t.id}`, 'n-care');
    const p = plants.find(x => x.id === t.plantId);
    if (issues > 0 && p?.healthStatus === 'ISSUES_DETECTED') link('n-problems', `n-treatment-${t.id}`);
  }

  // plants under the garden, density-collapsed
  const rankedPlants = [...plants].sort(plantByOwed);
  emitCollapsed(rankedPlants, 'n-garden', {
    kind: 'plant', kindLabel: 'Plant', aggregateId: 'n-garden-more', aggregateName: 'more plants',
    toNode: p => ({ id: `n-plant-${p.id}`, glyph: '♠', kind: 'plant', kindLabel: 'Plant', name: p.nickname,
      recap: plantRecap(p), recapNote: p.commonName ?? p.species ?? undefined,
      state: p.healthStatus === 'UNKNOWN' || p.healthStatus == null ? 'unknown' : undefined, body: plantBody(p, ctx) }),
  }, add, link);

  // species under the collection, density-collapsed; each links to its plants
  const bySpecies = (s: SpeciesDto) => plants.filter(p => p.species === s.scientificName || p.commonName === s.commonName);
  const rankedSpecies = [...species].sort((a, b) => bySpecies(b).length - bySpecies(a).length || a.id - b.id);
  emitCollapsed(rankedSpecies, 'n-species', {
    kind: 'species', kindLabel: 'Species', aggregateId: 'n-species-more', aggregateName: 'more species',
    toNode: s => ({ id: `n-species-${s.id}`, glyph: '♣', kind: 'species', kindLabel: 'Species',
      name: s.commonName ?? s.scientificName, recap: `${bySpecies(s).length} of your plants`,
      recapNote: s.commonName ? s.scientificName : undefined, body: speciesBody(s, bySpecies(s), drawn, sources.settings) }),
  }, add, link);
  // vein each drawn species to its drawn plants (cross-entity traversal)
  for (const s of rankedSpecies.slice(0, DENSITY_CAP - 1 < rankedSpecies.length ? 2 : rankedSpecies.length)) {
    for (const p of bySpecies(s)) {
      if (nodes.some(n => n.id === `n-plant-${p.id}`) && nodes.some(n => n.id === `n-species-${s.id}`)) {
        link(`n-species-${s.id}`, `n-plant-${p.id}`);
      }
    }
  }

  // a family that did not come back wears its own failure — the rest stays live
  for (const f of sources.failures) {
    for (const id of failureNodeIds(f, sources.treatments)) {
      const node = nodes.find(n => n.id === id);
      if (!node) continue;
      const extraWay = id === 'n-today';
      node.state = 'failed';
      node.recap = 'Did not come back';
      node.failure = {
        fact: `${failureName(f)} did not come back (${f.status}).`,
        time: timeLabel(f.at),
        dataNote: 'Everything already drawn is kept; nothing moved.',
        waysForward: extraWay ? ['Fetch this region', 'Count again'] : ['Fetch this region'],
      };
      node.body = failureBody(f, extraWay, sources.settings);
    }
  }

  layoutCells(nodes, edges, 'n-garden', sources.priorCells);
  return {
    nodes: nodes as WorldNode[],
    edges,
    initialFocus: 'n-garden',
    hasPendingScan,
    latestFailedScanId,
    meta: buildMeta(sources),
  };
}

/**
 * The plants the board actually draws, under the density rule — the single
 * source for both the geography and the loader's care-history fan-out, so a
 * request is never spent on a plant folded into "+N more".
 */
export function drawnPlantsOf(plants: PlantDto[]): PlantDto[] {
  const ranked = [...plants].sort(plantByOwed);
  return ranked.length < DENSITY_CAP ? ranked : ranked.slice(0, 2);
}

export function plantByOwed(a: PlantDto, b: PlantDto): number {
  const score = (p: PlantDto) => (p.healthStatus === 'ISSUES_DETECTED' ? -1000 : 0) + (p.nextWaterDays ?? 99);
  return score(a) - score(b) || a.id - b.id;
}

function plantRecap(p: PlantDto): string {
  if (p.healthStatus === 'ISSUES_DETECTED') return 'Needs attention';
  const d = p.nextWaterDays;
  if (d == null) return 'Healthy';
  if (d <= 0) return `Needs water · ${-d}d overdue`;
  return `Water in ${d}d`;
}

interface CollapseSpec<T> {
  kind: NodeKind;
  kindLabel: string;
  aggregateId: string;
  aggregateName: string;
  toNode: (item: T) => DraftNode;
}

/** Density rule: <4 draw all; ≥4 draw the two highest-ranked + one "+N more" (C4). */
function emitCollapsed<T>(
  ranked: T[],
  parentId: string,
  spec: CollapseSpec<T>,
  add: (n: DraftNode) => void,
  link: (a: string, b: string) => void,
): void {
  const drawn = ranked.length < DENSITY_CAP ? ranked : ranked.slice(0, 2);
  for (const item of drawn) {
    const node = spec.toNode(item);
    add(node);
    link(parentId, node.id);
  }
  if (ranked.length >= DENSITY_CAP) {
    const rest = ranked.length - 2;
    add({ id: spec.aggregateId, glyph: '⋯', kind: 'collection', kindLabel: 'Collection',
      name: `${rest} ${spec.aggregateName}`, recap: `+${rest} more` });
    link(parentId, spec.aggregateId);
  }
}

/**
 * Deterministic cell layout: breadth-first from the root; col = 2 × depth, rows
 * centred per layer. Same graph → same cells (C7). Unreachable nodes are parked.
 *
 * With `prior` cells given, insertion stability becomes real (C8): a node already on
 * the board keeps the exact cell it had, and a new node takes the first free row of
 * its own depth column — "a new node takes a free cell, nothing else moves" then
 * holds across reloads, not only within one. With `prior` absent or empty the
 * centred algorithm runs unchanged, so the shipped geography is untouched.
 */
export function layoutCells(
  nodes: DraftNode[],
  edges: Edge[],
  rootId: string,
  prior?: Record<string, Cell>,
): void {
  const ids = nodes.map(n => n.id);
  const adjacency = buildAdjacency(edges, ids);
  const depth = rank(rootId, adjacency);

  const byDepth = new Map<number, string[]>();
  for (const id of ids) {
    const d = depth[id];
    if (d === undefined) continue;
    (byDepth.get(d) ?? byDepth.set(d, []).get(d)!).push(id);
  }
  const cellFor: Record<string, WorldNode['cell']> = {};
  if (prior && Object.keys(prior).length > 0) {
    const taken = new Map<number, Set<number>>();
    const rowsOf = (col: number) => taken.get(col) ?? taken.set(col, new Set()).get(col)!;
    for (const [, layer] of byDepth) {
      for (const id of layer) {
        const kept = prior[id];
        if (!kept) continue;
        cellFor[id] = { col: kept.col, row: kept.row };
        rowsOf(kept.col).add(kept.row);
      }
    }
    for (const [d, layer] of byDepth) {
      const col = 2 * d;
      for (const id of [...layer].sort()) {
        if (cellFor[id]) continue;
        const rows = rowsOf(col);
        let row = CENTER_ROW;
        for (let step = 0; rows.has(row); step++) {
          const spread = Math.floor(step / 2) + 1;
          row = step % 2 === 0 ? CENTER_ROW - spread : CENTER_ROW + spread;
        }
        cellFor[id] = { col, row };
        rows.add(row);
      }
    }
  } else {
    for (const [d, layer] of byDepth) {
      layer.sort();
      const mid = Math.floor((layer.length - 1) / 2);
      layer.forEach((id, i) => {
        cellFor[id] = { col: 2 * d, row: CENTER_ROW + i - mid };
      });
    }
  }
  let parked = 0;
  for (const n of nodes) {
    if (cellFor[n.id]) n.cell = cellFor[n.id];
    else n.cell = { col: 20, row: parked++ };
  }
}
