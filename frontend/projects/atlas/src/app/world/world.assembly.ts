import { buildAdjacency, Edge, rank } from '@plantpal/rhizome-engine';
import { careLabel, isDue, timeLabel } from './dates';
import {
  Cell,
  FamilyFailure,
  IdentificationDto,
  PlantDto,
  SpeciesDto,
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

const recapWrap = (line: string, note?: string) =>
  `<div class="n__recap"><p class="n__recap-line">${line}</p>${note ? `<p class="n__recap-note">${note}</p>` : ''}</div>
   <div class="n__skel" aria-hidden="true"><div class="sk sk--name"></div><div class="sk sk--line"></div><div class="sk sk--line is-short"></div></div>`;

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

// ── per-node body builders (the prototype's material language, live data) ────

function plantBody(p: PlantDto): string {
  const binomial = p.species ? `<span class="plate__binomial">${esc(p.species)}</span>` : '';
  return (
    recapWrap(`${waterLine(p)} · water`, esc(p.commonName ?? p.species ?? '')) +
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
        <div class="state__head"><h4 class="state__title">This plant</h4><span class="state__id">action · /api/v1/plants/${p.id}</span></div>
        <dl class="rows">
          <div class="row"><dt>Species</dt><dd>${esc(p.commonName ?? p.species ?? 'Not identified yet')}</dd></div>
          <div class="row"><dt>Next water</dt><dd>${waterLine(p)}</dd></div>
          ${p.location ? `<div class="row"><dt>Location</dt><dd>${esc(p.location)}</dd></div>` : ''}
          ${p.activeTreatmentId ? '<div class="row"><dt>Treatment</dt><dd>Active plan running</dd></div>' : ''}
        </dl>
        <div class="btn-row"><button class="stake" type="button">Water plant</button><button class="stake stake--quiet" type="button">Add note</button></div>
      </section>`)
  );
}

function speciesBody(s: SpeciesDto, plantsOfSpecies: PlantDto[], drawn: Set<string>): string {
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
        <div class="state__head"><h4 class="state__title">This species</h4><span class="state__id">action · /api/v1/species/${s.id}</span></div>
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

function identBody(idents: IdentificationDto[], drawn: Set<string>): string {
  const latest = idents[0];
  const feed = idents
    .slice(0, 4)
    .map(i => `<div class="feed__row"><span class="feed__when">${esc(i.createdAt.slice(0, 10))}</span><span>${linkTo(drawn, `n-scan-${i.id}`, esc(i.commonName ?? i.species ?? 'Scan #' + i.id))}</span><span class="feed__val">${esc(i.status)}</span></div>`)
    .join('');
  const failedPanel =
    latest && latest.status === 'FAILED'
      ? `<section class="state state--error">
           <div class="state__head"><h4 class="state__title">The last scan did not come back</h4><span class="state__id">action · /api/v1/identifications/**</span></div>
           <p class="state__note">Your photo is kept — nothing was lost. Retry sits here, in the node.</p>
           <div class="btn-row"><button class="stake" type="button">Try the scan again</button><button class="stake stake--quiet" type="button">Identify by hand</button></div>
         </section>`
      : '';
  const pendingPanel =
    latest && (latest.status === 'PENDING' || latest.status === 'PROCESSING')
      ? `<section class="state state--loading">
           <div class="state__head"><h4 class="state__title">A scan is being analysed</h4><span class="state__id">data · polling</span></div>
           <p class="state__note">The answer arrives into this node — the geography holds while it does.</p>
         </section>`
      : '';
  return (
    recapWrap(latest ? scanStatusLine(latest) : 'No scans yet') +
    full(`
      ${failedPanel}${pendingPanel}
      <section class="state" data-brief-item="action:/api/v1/identifications/**">
        <div class="state__head"><h4 class="state__title">Your identifications</h4><span class="state__id">action · /api/v1/identifications/**</span></div>
        ${feed ? `<div class="feed">${feed}</div>` : '<p class="state__note">Photograph a plant and the answer lands here.</p>'}
        <div class="btn-row"><button class="stake" type="button">Identify a plant</button></div>
      </section>`)
  );
}

/** Wrap a label in a travelling doc-link when the target node is drawn. */
function linkTo(drawn: Set<string>, id: string, label: string): string {
  return drawn.has(id) ? `<a class="doc-link" href="#${id}" data-goto="${id}">${label}</a>` : label;
}

function scanBody(i: IdentificationDto, drawn: Set<string>): string {
  const name = esc(i.commonName ?? i.species ?? 'Unknown plant');
  const plantNode = i.plantId != null ? `n-plant-${i.plantId}` : null;
  const plantRow = plantNode
    ? `<div class="row"><dt>Plant</dt><dd>${linkTo(drawn, plantNode, 'Open the plant')}</dd></div>`
    : '<div class="row"><dt>Plant</dt><dd>Not added to the garden yet</dd></div>';
  return (
    recapWrap(scanStatusLine(i), esc(i.createdAt.slice(0, 10))) +
    full(`
      <section class="state" data-brief-item="action:/api/v1/identifications/**">
        <div class="state__head"><h4 class="state__title">This scan</h4><span class="state__id">action · /api/v1/identifications/${i.id}</span></div>
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

function gardenBody(plants: PlantDto[], drawn: Set<string>): string {
  const ranked = [...plants].sort(plantByOwed);
  const rows = ranked
    .slice(0, 3)
    .map(p => `<div class="row"><dt>${linkTo(drawn, `n-plant-${p.id}`, esc(p.nickname))}</dt><dd>${waterLine(p)}</dd></div>`)
    .join('');
  return (
    recapWrap(`${plants.length} plants`) +
    full(`
      <section class="state" data-brief-item="action:/api/v1/plants/**">
        <div class="state__head"><h4 class="state__title">Your plants</h4><span class="state__id">action · /api/v1/plants/**</span></div>
        ${rows ? `<dl class="rows">${rows}</dl>` : '<p class="state__note">No plants yet — add the first one.</p>'}
        <div class="btn-row"><button class="stake" type="button">Add a plant</button></div>
      </section>`)
  );
}

function accountBody(user: WorldSources['user']): string {
  const who = user ? `${esc(user.firstName)} ${esc(user.lastName)}` : 'Signed in';
  return (
    recapWrap(user ? esc(user.email) : 'Your session') +
    full(`
      <section class="state" data-brief-item="action:POST /api/v1/auth/login">
        <div class="state__head"><h4 class="state__title">Signing in</h4><span class="state__id">action · POST /api/v1/auth/login</span></div>
        <p class="state__note">Sign-in lives on the classic PlantPal page — the session it issues is the one this atlas is using now.</p>
      </section>
      <section class="state" data-brief-item="action:/api/v1/users/**">
        <div class="state__head"><h4 class="state__title">You, as PlantPal holds you</h4><span class="state__id">action · /api/v1/users/**</span></div>
        <dl class="rows">
          <div class="row"><dt>Name</dt><dd>${who}</dd></div>
          ${user ? `<div class="row"><dt>Email</dt><dd class="v mono">${esc(user.email)}</dd></div>` : ''}
        </dl>
      </section>`)
  );
}

function platformBody(): string {
  return (
    recapWrap('Health · feeds') +
    full(`
      <section class="state" data-brief-item="action:\`app.health\`">
        <div class="state__head"><h4 class="state__title">Health check</h4><span class="state__id">action · app.health</span></div>
        <p class="state__note">The backend behind this world. A check runs end-to-end and reports here.</p>
        <div class="btn-row"><button class="stake stake--quiet" type="button">Check health again</button></div>
      </section>
      <section class="state state--unknown" data-brief-item="data:dimension.event">
        <div class="state__head"><h4 class="state__title">Dimension events</h4><span class="state__id">data · dimension.event</span></div>
        <p class="state__note">Not fetched yet — the platform feed lands in a later round.</p>
      </section>
      <section class="state state--unknown" data-brief-item="data:state.event">
        <div class="state__head"><h4 class="state__title">State events</h4><span class="state__id">data · state.event</span></div>
        <p class="state__note">Not fetched yet.</p>
      </section>`)
  );
}

/** A deferred-family node body (coverage-scope: rounds 2/3). */
function deferredBody(title: string, id: string, note: string): string {
  return (
    recapWrap('Coming with the care loop') +
    full(`
      <section class="state state--empty" data-brief-item="action:${id}">
        <div class="state__head"><h4 class="state__title">${title}</h4><span class="state__id">action · ${id}</span></div>
        <div class="empty-plot"><span aria-hidden="true">◌</span></div>
        <p class="state__note">${note}</p>
      </section>`)
  );
}

/** Which node wears a family's failure — degradation is per-node material (C25). */
const FAILURE_NODE: Record<string, string> = {
  reminders: 'n-reminders',
  dashboard: 'n-today',
  care: 'n-care',
  treatments: 'n-treatments',
  users: 'n-account',
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

function failureNodeId(f: FamilyFailure): string | null {
  if (f.family === 'treatment-plans') return f.ref != null ? `n-treatment-${f.ref}` : null;
  return FAILURE_NODE[f.family] ?? null;
}

/** The failure, written inside the node it belongs to: fact, time, fate, ways on. */
function failureBody(f: FamilyFailure, extraWay: boolean): string {
  const note = `PlantPal answered with ${f.status} at ${timeLabel(f.at)}. Everything already drawn is kept; nothing moved.`;
  return (
    recapWrap('Did not come back', esc(f.message ?? undefined)) +
    full(`
      <section class="state state--error" data-brief-item="state:error">
        <div class="state__head"><h4 class="state__title">${esc(failureName(f))} did not come back</h4><span class="state__id">state · error</span></div>
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
  const drawn = new Set<string>([
    ...drawnPlants.map(p => `n-plant-${p.id}`),
    ...drawnScans.map(i => `n-scan-${i.id}`),
  ]);

  // hub
  add({ id: 'n-garden', glyph: '♣', kind: 'collection', kindLabel: 'Garden', name: 'My garden',
    recap: `${plants.length} plants · ${needWater} need water`, body: gardenBody(plants, drawn) });

  add({ id: 'n-account', glyph: '◉', kind: 'platform', kindLabel: 'Account', name: user ? `${user.firstName}'s account` : 'Your account',
    recap: user ? user.email : 'Signed in', body: accountBody(user) });
  link('n-account', 'n-garden');

  add({ id: 'n-platform', glyph: '◈', kind: 'platform', kindLabel: 'Platform', name: 'Platform link',
    recap: 'Health · 2 feeds', body: platformBody() });
  link('n-account', 'n-platform');

  add({ id: 'n-ident', glyph: '◎', kind: 'platform', kindLabel: 'Identification', name: 'Identification',
    recap: latestScan[0] ? `Last scan · ${latestScan[0].status.toLowerCase()}` : 'No scans yet',
    state: latestScan[0]?.status === 'FAILED' ? 'failed' : undefined,
    body: identBody(latestScan, drawn) });
  link('n-garden', 'n-ident');

  add({ id: 'n-species', glyph: '❋', kind: 'collection', kindLabel: 'Collection', name: 'Species',
    recap: `${species.length} species`, state: species.length === 0 ? 'empty' : undefined,
    body: recapWrap(`${species.length} species`) + full(`
      <section class="state" data-brief-item="action:/api/v1/species/**">
        <div class="state__head"><h4 class="state__title">The species index</h4><span class="state__id">action · /api/v1/species/**</span></div>
        <p class="state__note">Everything you have identified or added by hand. A species is a reference thing — nothing here can be watered.</p>
        <div class="btn-row"><button class="stake" type="button">Add a species</button></div>
      </section>`) });
  link('n-garden', 'n-species');
  link('n-ident', 'n-species'); // the identify → species → plant path

  if (issues > 0) {
    add({ id: 'n-problems', glyph: '⚠', kind: 'problem', kindLabel: 'Problems', name: 'Problems',
      recap: `${issues} plant${issues === 1 ? '' : 's'} need attention`,
      body: recapWrap(`${issues} active`) + full(`
        <section class="state" data-brief-item="action:/api/v1/plants/**">
          <div class="state__head"><h4 class="state__title">Plants needing attention</h4><span class="state__id">data · healthStatus</span></div>
          <dl class="rows">${plants.filter(p => p.healthStatus === 'ISSUES_DETECTED').slice(0, 3)
            .map(p => `<div class="row"><dt>${linkTo(drawn, `n-plant-${p.id}`, esc(p.nickname))}</dt><dd>${healthTag(p.healthStatus)}</dd></div>`).join('')}</dl>
        </section>`) });
    link('n-garden', 'n-problems');
  }

  // each scan is a node of its own (the classic scan-detail modal, as geography)
  emitCollapsed(drawnScans.length === latestScan.length ? latestScan : latestScan, 'n-ident', {
    kind: 'platform', kindLabel: 'Scan', aggregateId: 'n-scans-more', aggregateName: 'more scans',
    toNode: i => ({ id: `n-scan-${i.id}`, glyph: '◎', kind: 'platform', kindLabel: 'Scan',
      name: i.commonName ?? i.species ?? `Scan #${i.id}`, recap: `${i.status.toLowerCase()} · ${i.createdAt.slice(0, 10)}`,
      state: i.status === 'FAILED' ? 'failed' : undefined, body: scanBody(i, drawn) }),
  }, add, link);
  // a scan with a plant in the garden veins to it (identify → plant path)
  for (const i of drawnScans) {
    if (i.plantId != null && drawn.has(`n-plant-${i.plantId}`)) link(`n-scan-${i.id}`, `n-plant-${i.plantId}`);
  }

  // the remaining classic pages, as nodes (chat + home dashboard + treatments)
  add({ id: 'n-ask', glyph: '✎', kind: 'guide', kindLabel: 'Companion', name: 'Ask PlantPal',
    recap: 'Coming soon', state: 'empty',
    body: deferredBody('Ask PlantPal', '/api/v1/chat/**', 'The companion arrives in a later round — it will answer about the plants on this board.') });
  link('n-garden', 'n-ask');

  add({ id: 'n-today', glyph: '◷', kind: 'guide', kindLabel: 'Dashboard', name: 'Today',
    recap: 'Coming with the care loop', state: 'empty',
    body: deferredBody("Today's summary", '/api/v1/dashboard/**', 'The dashboard aggregates the care loop — it lands once reminders and treatments do.') });
  link('n-garden', 'n-today');
  link('n-today', 'n-reminders');

  add({ id: 'n-treatments', glyph: '◈', kind: 'problem', kindLabel: 'Treatment', name: 'Treatments',
    recap: 'Coming with the care loop', state: 'empty',
    body: deferredBody('Treatment plans', '/api/v1/treatment-plans/**', 'Per-disease treatment courses arrive with the care loop.') });
  link('n-garden', 'n-treatments');

  // deferred families — honest panels, still traversable (coverage-scope rounds 2/3)
  add({ id: 'n-reminders', glyph: '◷', kind: 'journal', kindLabel: 'Reminders', name: 'Reminders',
    recap: 'Coming with the care loop', state: 'empty',
    body: deferredBody('Reminders', '/api/v1/reminders/**', 'Reminders arrive with the care loop — the next round of this atlas.') });
  link('n-garden', 'n-reminders');

  add({ id: 'n-care', glyph: '☂', kind: 'guide', kindLabel: 'Guide', name: 'Care', recap: 'Coming with the care loop', state: 'empty',
    body: deferredBody('Care, and what you did', '/api/v1/care/**', 'Care logging arrives with reminders and treatment plans.') });
  link('n-garden', 'n-care');

  // plants under the garden, density-collapsed
  const rankedPlants = [...plants].sort(plantByOwed);
  emitCollapsed(rankedPlants, 'n-garden', {
    kind: 'plant', kindLabel: 'Plant', aggregateId: 'n-garden-more', aggregateName: 'more plants',
    toNode: p => ({ id: `n-plant-${p.id}`, glyph: '♠', kind: 'plant', kindLabel: 'Plant', name: p.nickname,
      recap: plantRecap(p), recapNote: p.commonName ?? p.species ?? undefined,
      state: p.healthStatus === 'UNKNOWN' ? 'unknown' : undefined, body: plantBody(p) }),
  }, add, link);

  // species under the collection, density-collapsed; each links to its plants
  const bySpecies = (s: SpeciesDto) => plants.filter(p => p.species === s.scientificName || p.commonName === s.commonName);
  const rankedSpecies = [...species].sort((a, b) => bySpecies(b).length - bySpecies(a).length || a.id - b.id);
  emitCollapsed(rankedSpecies, 'n-species', {
    kind: 'species', kindLabel: 'Species', aggregateId: 'n-species-more', aggregateName: 'more species',
    toNode: s => ({ id: `n-species-${s.id}`, glyph: '♣', kind: 'species', kindLabel: 'Species',
      name: s.commonName ?? s.scientificName, recap: `${bySpecies(s).length} of your plants`,
      recapNote: s.commonName ? s.scientificName : undefined, body: speciesBody(s, bySpecies(s), drawn) }),
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
    const id = failureNodeId(f);
    const node = id ? nodes.find(n => n.id === id) : undefined;
    if (!node || !id) continue;
    const extraWay = id === 'n-today';
    node.state = 'failed';
    node.recap = 'Did not come back';
    node.failure = {
      fact: `${failureName(f)} did not come back (${f.status}).`,
      time: timeLabel(f.at),
      dataNote: 'Everything already drawn is kept; nothing moved.',
      waysForward: extraWay ? ['Fetch this region', 'Count again'] : ['Fetch this region'],
    };
    node.body = failureBody(f, extraWay);
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
