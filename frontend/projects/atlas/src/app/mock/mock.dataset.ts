import { PlantDto, SpeciesDto, IdentificationDto, WorldSources, WorldUser } from '../world/world.dto';

/**
 * The mock garden's seed data. One dataset per scenario, every date an offset
 * from `now`, so the garden is always "today" and a fixed `now` makes the whole
 * thing deterministic (spec-asserted).
 *
 * Shapes mirror the backend wire format exactly: enums are strings, instants are
 * ISO-8601, and absent fields are ABSENT (Jackson non_null) — never null.
 */

export type MockScenario = 'garden' | 'day-zero' | 'outage';

export interface MockUser {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  status: 'ACTIVE' | 'INACTIVE';
}

/** Stored plant row — healthStatus/nextWaterDays/activeTreatmentId are DERIVED on read. */
export interface MockPlant {
  id: number;
  nickname: string;
  species?: string;
  commonName?: string;
  speciesId?: number;
  location?: string;
  notes?: string;
  lastScanId?: number;
  /** Fallback health when no completed scan names this plant (a real column server-side). */
  healthStatus?: string;
  status: 'ACTIVE' | 'ARCHIVED';
}

export interface MockSpecies {
  id: number;
  scientificName: string;
  commonName?: string;
}

export interface MockIdentification {
  id: number;
  species?: string;
  commonName?: string;
  healthStatus?: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  createdAt: string;
  plantId?: number;
}

export interface MockReminder {
  id: number;
  plantId: number;
  plantNickname?: string;
  careType: string;
  frequencyDays: number;
  nextDueAt: string;
  enabled: boolean;
  recurring: boolean;
  updatedAt: string;
  treatmentPlanId?: number;
  treatmentPlanTitle?: string;
  stepOrder?: number;
  instruction?: string;
  stepDetail?: string;
  stepDiagramFormat?: string;
  stepDiagramContent?: string;
}

export interface MockCareLog {
  id: number;
  plantId: number;
  plantNickname?: string;
  careType: string;
  notes?: string;
  performedAt: string;
}

export interface MockTreatmentPlan {
  id: number;
  plantId: number;
  title: string;
  diagramFormat?: string;
  diagramContent?: string;
  status: 'ACTIVE' | 'COMPLETED' | 'ABANDONED';
  createdAt: string;
}

export interface MockTreatment {
  id: number;
  plantId: number;
  diseaseName: string;
  status: 'DRAFT' | 'IN_PROGRESS' | 'COMPLETED' | 'DISMISSED';
  descriptionStatus?: 'PENDING' | 'READY' | 'FAILED';
  diseaseDescription?: string;
  diseaseDescriptionModel?: string;
  treatmentPlanModel?: string;
  identificationId?: number;
  treatmentPlanId?: number;
  needsReview?: boolean;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
}

export interface MockPreferences {
  aiModelPreference: string;
  visionModelPreference: string;
  reasoningModelPreference: string;
  visionModelAvailability: Record<string, boolean>;
  reasoningModelAvailability: Record<string, boolean>;
  plantnetProject: string;
  plantnetLang: string;
  businessTier: boolean;
}

export interface MockPushSubscription {
  endpoint: string;
  keyP256dh: string;
  keyAuth: string;
}

export interface MockSeed {
  user: MockUser;
  plants: MockPlant[];
  species: MockSpecies[];
  identifications: MockIdentification[];
  reminders: MockReminder[];
  careLogs: MockCareLog[];
  treatmentPlans: MockTreatmentPlan[];
  treatments: MockTreatment[];
  preferences: MockPreferences;
  pushSubscriptions: MockPushSubscription[];
  pausedPlanIds: number[];
  failing: { method: string; re: RegExp }[];
  flags: { rateLimitOnceTreatmentIds: number[] };
  nextId: Record<'plant' | 'identification' | 'reminder' | 'plan' | 'treatment' | 'careLog', number>;
}

/** A step template used when the mock crafts a plan for a disease. */
export interface PlanTemplateStep {
  instruction: string;
  dueOffsetDays: number;
  detail?: string;
}

export const PLAN_TEMPLATES: Record<string, PlanTemplateStep[]> = {
  default: [
    { instruction: 'Isolate the plant from its neighbours', dueOffsetDays: 0 },
    { instruction: 'Remove the affected leaves', dueOffsetDays: 1, detail: '1. Sterilise the blade. 2. Cut back to healthy tissue.' },
    { instruction: 'Treat the remaining foliage', dueOffsetDays: 4 },
    { instruction: 'Evaluate recovery', dueOffsetDays: 8 },
  ],
  'Leaf miner': [
    { instruction: 'Pick off the mined leaves', dueOffsetDays: 0 },
    { instruction: 'Check the undersides for fresh trails', dueOffsetDays: 1, detail: '1. Hold the leaf to the light. 2. Look for pale winding lines.' },
    { instruction: 'Apply a horticultural oil', dueOffsetDays: 4 },
    { instruction: 'Evaluate recovery', dueOffsetDays: 8 },
  ],
};

const DAY = 86400000;

/** Local wall-clock hour on the day `now` falls in — "today at 18:00" in any zone. */
export function todayAt(now: number, hour: number): string {
  const d = new Date(now);
  d.setHours(hour, 0, 0, 0);
  return d.toISOString();
}

/** Offset helper — every seeded instant is relative to `now`. */
export function atFrom(now: number) {
  return (days: number, hours = 0): string => new Date(now + days * DAY + hours * 3600000).toISOString();
}

function garden(now: number): MockSeed {
  const at = atFrom(now);
  const species: MockSpecies[] = [
    { id: 1, scientificName: 'Ficus lyrata', commonName: 'Fiddle-leaf fig' },
    { id: 2, scientificName: 'Monstera deliciosa', commonName: 'Swiss cheese plant' },
    { id: 3, scientificName: 'Epipremnum aureum', commonName: 'Pothos' },
    { id: 4, scientificName: 'Dracaena trifasciata', commonName: 'Snake plant' },
    { id: 5, scientificName: 'Citrus × limon', commonName: 'Lemon tree' },
  ];

  const plants: MockPlant[] = [
    { id: 1, nickname: 'Office Fig', species: 'Ficus lyrata', commonName: 'Fiddle-leaf fig', speciesId: 1, location: 'Office · south window', lastScanId: 501, healthStatus: 'ISSUES_DETECTED', status: 'ACTIVE' },
    { id: 2, nickname: 'Studio Fig', species: 'Ficus lyrata', commonName: 'Fiddle-leaf fig', speciesId: 1, location: 'Studio', lastScanId: 504, healthStatus: 'HEALTHY', status: 'ACTIVE' },
    { id: 3, nickname: 'Monstera', species: 'Monstera deliciosa', commonName: 'Swiss cheese plant', speciesId: 2, location: 'Living room', lastScanId: 502, healthStatus: 'HEALTHY', status: 'ACTIVE' },
    { id: 4, nickname: 'Hallway Pothos', species: 'Epipremnum aureum', commonName: 'Pothos', speciesId: 3, location: 'Hallway', status: 'ACTIVE' },
    { id: 5, nickname: 'Terrace Lemon', species: 'Citrus × limon', commonName: 'Lemon tree', speciesId: 5, location: 'Terrace', lastScanId: 503, healthStatus: 'ISSUES_DETECTED', status: 'ACTIVE' },
    { id: 6, nickname: 'Bedroom Snake Plant', species: 'Dracaena trifasciata', commonName: 'Snake plant', speciesId: 4, location: 'Bedroom', healthStatus: 'HEALTHY', status: 'ACTIVE' },
  ];

  const identifications: MockIdentification[] = [
    { id: 505, status: 'PENDING', createdAt: at(0, -0.02) },
    { id: 503, status: 'FAILED', createdAt: at(-0.04) },
    { id: 504, species: 'Ficus lyrata', commonName: 'Fiddle-leaf fig', healthStatus: 'HEALTHY', status: 'COMPLETED', createdAt: at(-3), plantId: 2 },
    { id: 501, species: 'Ficus lyrata', commonName: 'Fiddle-leaf fig', healthStatus: 'ISSUES_DETECTED', status: 'COMPLETED', createdAt: at(-5), plantId: 1 },
    { id: 502, species: 'Monstera deliciosa', commonName: 'Swiss cheese plant', healthStatus: 'HEALTHY', status: 'COMPLETED', createdAt: at(-12), plantId: 3 },
  ];

  const routine: MockReminder[] = [
    { id: 601, plantId: 1, plantNickname: 'Office Fig', careType: 'WATERING', frequencyDays: 7, nextDueAt: at(-2), enabled: true, recurring: true, updatedAt: at(-9) },
    { id: 602, plantId: 2, plantNickname: 'Studio Fig', careType: 'WATERING', frequencyDays: 5, nextDueAt: todayAt(now, 18), enabled: true, recurring: true, updatedAt: at(-5) },
    { id: 603, plantId: 3, plantNickname: 'Monstera', careType: 'WATERING', frequencyDays: 7, nextDueAt: at(3), enabled: true, recurring: true, updatedAt: at(-4) },
    { id: 604, plantId: 3, plantNickname: 'Monstera', careType: 'FERTILIZING', frequencyDays: 30, nextDueAt: at(12), enabled: true, recurring: true, updatedAt: at(-18) },
    { id: 605, plantId: 6, plantNickname: 'Bedroom Snake Plant', careType: 'WATERING', frequencyDays: 14, nextDueAt: at(14), enabled: true, recurring: true, updatedAt: at(-2) },
    { id: 606, plantId: 5, plantNickname: 'Terrace Lemon', careType: 'PRUNING', frequencyDays: 60, nextDueAt: at(-5), enabled: true, recurring: true, updatedAt: at(-65) },
    { id: 607, plantId: 2, plantNickname: 'Studio Fig', careType: 'REPOTTING', frequencyDays: 365, nextDueAt: at(40), enabled: true, recurring: true, updatedAt: at(-325) },
  ];

  const step = (
    id: number,
    plantId: number,
    plantNickname: string,
    planId: number,
    planTitle: string,
    order: number,
    instruction: string,
    nextDueAt: string,
    enabled: boolean,
    updatedAt: string,
    extra: Partial<MockReminder> = {},
  ): MockReminder => ({
    id,
    plantId,
    plantNickname,
    careType: 'PEST',
    frequencyDays: 0,
    nextDueAt,
    enabled,
    recurring: false,
    updatedAt,
    treatmentPlanId: planId,
    treatmentPlanTitle: planTitle,
    stepOrder: order,
    instruction,
    ...extra,
  });

  const steps: MockReminder[] = [
    step(701, 1, 'Office Fig', 201, 'Root rot', 1, 'Deep water and let it drain', at(-3), false, at(-1)),
    step(702, 1, 'Office Fig', 201, 'Root rot', 2, 'Check the crown for soft tissue', at(0), true, at(-6), {
      stepDetail: '1. Tip the pot on its side. 2. Ease the root ball out. 3. Press the crown — firm is well, soft is rot.',
    }),
    step(703, 1, 'Office Fig', 201, 'Root rot', 3, 'Repot into a gritty mix', at(4), true, at(-6), {
      stepDiagramFormat: 'MERMAID',
      stepDiagramContent: 'graph TD; A[Old pot] --> B[Trim rot]; B --> C[Gritty mix];',
    }),
    step(704, 1, 'Office Fig', 201, 'Root rot', 4, 'Evaluate recovery', at(8), true, at(-6)),
    step(711, 6, 'Bedroom Snake Plant', 202, 'Mealybugs', 1, 'Wipe the leaves with alcohol', at(-24), false, at(-24)),
    step(712, 6, 'Bedroom Snake Plant', 202, 'Mealybugs', 2, 'Repeat the wipe', at(-22), false, at(-22)),
    step(713, 6, 'Bedroom Snake Plant', 202, 'Mealybugs', 3, 'Evaluate recovery', at(-20), false, at(-20)),
    step(801, 3, 'Monstera', 203, 'Spider mites', 1, 'Shower the foliage', at(-6), false, at(-6)),
    step(802, 3, 'Monstera', 203, 'Spider mites', 2, 'Apply a horticultural oil', at(2), true, at(-8)),
    step(803, 3, 'Monstera', 203, 'Spider mites', 3, 'Evaluate recovery', at(6), true, at(-8)),
  ];

  const treatmentPlans: MockTreatmentPlan[] = [
    { id: 201, plantId: 1, title: 'Root rot', status: 'ACTIVE', createdAt: at(-6), diagramFormat: 'MERMAID', diagramContent: 'graph TD; A[Root rot] --> B[Dry out]; B --> C[Repot];' },
    { id: 202, plantId: 6, title: 'Mealybugs', status: 'COMPLETED', createdAt: at(-25) },
    { id: 203, plantId: 3, title: 'Spider mites', status: 'ACTIVE', createdAt: at(-8) },
  ];

  const treatments: MockTreatment[] = [
    {
      id: 301, plantId: 1, diseaseName: 'Root rot', status: 'IN_PROGRESS', descriptionStatus: 'READY',
      diseaseDescription: 'Root rot sets in when the mix stays wet longer than the roots can breathe. The crown softens first; the leaves follow.',
      diseaseDescriptionModel: 'ANTHROPIC_CLAUDE', treatmentPlanModel: 'ANTHROPIC_CLAUDE',
      identificationId: 501, treatmentPlanId: 201, needsReview: false, createdAt: at(-6), startedAt: at(-6),
    },
    { id: 302, plantId: 2, diseaseName: 'Underwatering', status: 'DRAFT', descriptionStatus: 'PENDING', identificationId: 504, needsReview: false, createdAt: at(-3) },
    { id: 303, plantId: 5, diseaseName: 'Leaf miner', status: 'DRAFT', descriptionStatus: 'FAILED', identificationId: 503, needsReview: false, createdAt: at(-1) },
    { id: 304, plantId: 6, diseaseName: 'Mealybugs', status: 'COMPLETED', descriptionStatus: 'READY', diseaseDescription: 'Mealybugs cluster in leaf axils and drink sap.', treatmentPlanId: 202, needsReview: false, createdAt: at(-25), startedAt: at(-25), completedAt: at(-20) },
    { id: 305, plantId: 3, diseaseName: 'Spider mites', status: 'IN_PROGRESS', descriptionStatus: 'READY', diseaseDescription: 'Spider mites thrive in dry air and stipple the leaf surface.', treatmentPlanId: 203, needsReview: false, createdAt: at(-8), startedAt: at(-8) },
  ];

  const careLogs: MockCareLog[] = [
    { id: 901, plantId: 1, plantNickname: 'Office Fig', careType: 'WATERING', notes: 'Full soak, drained', performedAt: at(-9) },
    { id: 902, plantId: 1, plantNickname: 'Office Fig', careType: 'PEST', notes: 'Deep water · drained fully', performedAt: at(-1) },
    { id: 903, plantId: 2, plantNickname: 'Studio Fig', careType: 'WATERING', performedAt: at(-5) },
    { id: 904, plantId: 3, plantNickname: 'Monstera', careType: 'WATERING', performedAt: at(-4) },
    { id: 905, plantId: 3, plantNickname: 'Monstera', careType: 'FERTILIZING', notes: 'half strength', performedAt: at(-18) },
    { id: 906, plantId: 6, plantNickname: 'Bedroom Snake Plant', careType: 'WATERING', performedAt: at(-2) },
    { id: 907, plantId: 6, plantNickname: 'Bedroom Snake Plant', careType: 'PEST', performedAt: at(-20) },
    { id: 908, plantId: 6, plantNickname: 'Bedroom Snake Plant', careType: 'PEST', performedAt: at(-22) },
    { id: 909, plantId: 6, plantNickname: 'Bedroom Snake Plant', careType: 'PEST', performedAt: at(-24) },
    { id: 910, plantId: 5, plantNickname: 'Terrace Lemon', careType: 'PRUNING', performedAt: at(-65) },
  ];

  return {
    user: { id: 1, email: 'sam@example.org', firstName: 'Sam', lastName: 'Okafor', status: 'ACTIVE' },
    plants,
    species,
    identifications,
    reminders: [...routine, ...steps],
    careLogs,
    treatmentPlans,
    treatments,
    preferences: defaultPreferences(),
    pushSubscriptions: [],
    pausedPlanIds: [203],
    failing: [],
    flags: { rateLimitOnceTreatmentIds: [303] },
    nextId: { plant: 7, identification: 506, reminder: 900, plan: 204, treatment: 306, careLog: 911 },
  };
}

function defaultPreferences(): MockPreferences {
  return {
    aiModelPreference: 'GITHUB_GPT4O',
    visionModelPreference: 'GITHUB_GPT4O',
    reasoningModelPreference: 'DEEPSEEK_R1',
    visionModelAvailability: {
      GITHUB_GPT4O: true, GITHUB_GPT41: true, OLLAMA_GEMMA3: true, PLANTNET: true, ANTHROPIC_CLAUDE: false,
    },
    reasoningModelAvailability: {
      DEEPSEEK_R1: true, GITHUB_O4_MINI: true, GITHUB_GPT41_MINI: true, OLLAMA_GEMMA3: true, ANTHROPIC_CLAUDE: false,
    },
    plantnetProject: 'all',
    plantnetLang: 'en',
    businessTier: false,
  };
}

function dayZero(now: number): MockSeed {
  const g = garden(now);
  return {
    ...g,
    plants: [],
    species: [],
    identifications: [],
    reminders: [],
    careLogs: [],
    treatmentPlans: [],
    treatments: [],
    pausedPlanIds: [],
    flags: { rateLimitOnceTreatmentIds: [] },
  };
}

/** The seed for a scenario, at a given instant. Pure — same `now`, same seed. */
export function buildMockSeed(scenario: MockScenario, now: number): MockSeed {
  if (scenario === 'day-zero') return dayZero(now);
  if (scenario === 'outage') {
    return {
      ...garden(now),
      failing: [
        { method: 'GET', re: /^\/reminders$/ },
        { method: 'GET', re: /^\/dashboard$/ },
        { method: 'GET', re: /^\/treatment-plans\/201$/ },
      ],
    };
  }
  return garden(now);
}

/** Days until the earliest enabled recurring WATERING reminder is due. */
function nextWaterDays(seed: MockSeed, plantId: number, now: number): number | undefined {
  const due = seed.reminders
    .filter(r => r.plantId === plantId && r.enabled && r.recurring && r.careType === 'WATERING')
    .map(r => Date.parse(r.nextDueAt))
    .sort((a, b) => a - b)[0];
  if (due === undefined) return undefined;
  return Math.floor((due - now) / DAY);
}

/** The plant as the server would answer it: derived health, water and treatment. */
export function derivePlant(seed: MockSeed, p: MockPlant, now: number): PlantDto {
  const latestScan = seed.identifications
    .filter(i => i.plantId === p.id && i.status === 'COMPLETED' && i.healthStatus)
    .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))[0];
  const active = seed.treatments
    .filter(t => t.plantId === p.id && (t.status === 'DRAFT' || t.status === 'IN_PROGRESS'))
    .sort((a, b) => b.id - a.id)[0];
  return {
    id: p.id,
    nickname: p.nickname,
    species: p.species ?? null,
    commonName: p.commonName ?? null,
    healthStatus: latestScan?.healthStatus ?? p.healthStatus,
    nextWaterDays: nextWaterDays(seed, p.id, now),
    activeTreatmentId: active?.id,
    location: p.location,
  };
}

/**
 * The seed as WorldSources — the assembly's own input, so specs (and S8's
 * constitution suite) can assemble a mock board without any HTTP at all.
 * Round-1 subset today; grows with WorldSources in S2.
 */
export function seedToSources(seed: MockSeed, now: string): WorldSources {
  const t = Date.parse(now);
  const user: WorldUser | null = seed.user
    ? { firstName: seed.user.firstName, lastName: seed.user.lastName, email: seed.user.email }
    : null;
  const species: SpeciesDto[] = seed.species.map(s => ({
    id: s.id,
    scientificName: s.scientificName,
    commonName: s.commonName ?? null,
  }));
  const identifications: IdentificationDto[] = seed.identifications.map(i => ({
    id: i.id,
    species: i.species ?? null,
    commonName: i.commonName ?? null,
    healthStatus: i.healthStatus ?? null,
    status: i.status,
    createdAt: i.createdAt,
    plantId: i.plantId,
  }));
  return {
    plants: seed.plants.filter(p => p.status === 'ACTIVE').map(p => derivePlant(seed, p, t)),
    species,
    identifications,
    user,
  };
}
