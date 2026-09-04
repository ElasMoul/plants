/**
 * The subset of PlantPal backend DTOs the atlas world assembles from — rounds
 * 1 to 3 of the coverage scope: plants, species, identifications, reminders,
 * care logs, treatment plans and treatments, the dashboard, and the user's
 * preferences.
 *
 * Every field the server omits (Jackson `default-property-inclusion: non_null`)
 * is optional here — `field === null` would never fire, so absent is modelled as
 * absent.
 */

import type { UserPreferences } from '@plantpal/shared-core';

export interface PlantDto {
  id: number;
  nickname: string;
  species: string | null;
  commonName: string | null;
  healthStatus?: string;
  nextWaterDays?: number | null;
  activeTreatmentId?: number | null;
  location?: string | null;
  photoUrl?: string | null;
  notes?: string | null;
  status?: string;
  speciesId?: number | null;
  lastScanId?: number | null;
}

export interface SpeciesDto {
  id: number;
  scientificName: string;
  commonName: string | null;
}

export interface IdentificationDto {
  id: number;
  species: string | null;
  commonName: string | null;
  healthStatus: string | null;
  status: string; // PENDING | PROCESSING | COMPLETED | FAILED (backend enum)
  createdAt: string;
  plantId?: number | null;
}

/** The ten backend CareType values (reminder/entity/CareType.java). */
export type CareType =
  | 'WATERING'
  | 'LIGHT'
  | 'HUMIDITY'
  | 'TEMPERATURE'
  | 'FERTILIZING'
  | 'REPOTTING'
  | 'PRUNING'
  | 'PEST'
  | 'SEASONAL'
  | 'BEGINNER_TIP';

export const CARE_TYPES: CareType[] = [
  'WATERING',
  'FERTILIZING',
  'REPOTTING',
  'PRUNING',
  'LIGHT',
  'HUMIDITY',
  'TEMPERATURE',
  'PEST',
  'SEASONAL',
  'BEGINNER_TIP',
];

/**
 * A reminder row. A treatment step IS a reminder: recurring=false,
 * frequencyDays=0, treatmentPlanId/stepOrder/instruction set.
 */
export interface ReminderDto {
  id: number;
  plantId: number;
  plantNickname?: string;
  plantPhotoUrl?: string;
  careType: string;
  frequencyDays: number;
  nextDueAt: string;
  enabled: boolean;
  recurring: boolean;
  treatmentPlanId?: number;
  treatmentPlanTitle?: string;
  stepOrder?: number;
  instruction?: string;
  completedAt?: string;
  stepDetail?: string;
  stepDiagramFormat?: string;
  stepDiagramContent?: string;
}

export interface CareLogDto {
  id: number;
  plantId: number;
  plantNickname?: string;
  careType: string;
  notes?: string;
  performedAt: string;
}

export interface TreatmentPlanDto {
  id: number;
  plantId: number;
  title: string;
  diagramFormat?: string;
  diagramContent?: string;
  status: string; // ACTIVE | COMPLETED | ABANDONED
  createdAt: string;
  steps: ReminderDto[];
}

export interface TreatmentDto {
  id: number;
  plantId: number;
  plantNickname?: string;
  diseaseName: string;
  status: string; // DRAFT | IN_PROGRESS | COMPLETED | DISMISSED
  descriptionStatus?: string; // PENDING | READY | FAILED
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

/**
 * The garden's health count. The server (HealthSummaryDto.java) sends
 * healthyCount/issuesCount/unknownCount/totalPlants; the mock backend sends the
 * short names. Both are optional here and read defensively at the call site.
 */
export interface HealthSummaryDto {
  healthy?: number;
  issues?: number;
  unknown?: number;
  healthyCount?: number;
  issuesCount?: number;
  unknownCount?: number;
  totalPlants?: number;
}

/**
 * A dashboard reminder. The server's ReminderSummaryDto carries only
 * reminderId/plantId/plantNickname/plantPhotoUrl/careType/nextDueAt/daysOverdue
 * — no id, enabled or treatmentPlanId — so everything else is optional and is
 * resolved by joining against GET /reminders.
 */
export interface ReminderSummaryDto extends Partial<ReminderDto> {
  reminderId?: number;
  plantId: number;
  plantNickname?: string;
  plantPhotoUrl?: string;
  careType: string;
  nextDueAt: string;
  daysOverdue?: number;
}

export interface PlantHealthTrendDto {
  plantId: number;
  plantNickname: string;
  trend: string; // IMPROVING | WORSENING | STABLE
}

export interface RecentScanDto {
  id: number;
  species?: string;
  commonName?: string;
  healthStatus?: string;
  status: string;
  createdAt: string;
  plantId?: number;
}

export interface DashboardDto {
  healthSummary: HealthSummaryDto;
  overdueReminders: ReminderSummaryDto[];
  todayReminders: ReminderSummaryDto[];
  healthTrends: PlantHealthTrendDto[];
  recentScans: RecentScanDto[];
  /** Not sent by the server's DashboardResponse — fall back to the plants we hold. */
  plantCount?: number;
  speciesCount: number;
}

/** Server-backed preferences (GET/PUT /users/me/preferences). */
export type UserPreferencesDto = UserPreferences;

export interface PushSubscriptionRequest {
  endpoint: string;
  keyP256dh: string;
  keyAuth: string;
}

export interface CreateReminderRequest {
  plantId: number;
  careType: string;
  frequencyDays: number;
  firstDueAt: string;
}

export interface CreateTreatmentRequest {
  plantId: number;
  diseaseName: string;
  identificationId?: number;
}

/** One family's fetch failing is written into that family's own node (C25). */
export interface FamilyFailure {
  family: string;
  ref?: number;
  status: number;
  at: string;
  message?: string;
}

/**
 * The plain (JSON round-trip equal) snapshot of the settings the assembly reads.
 * Keeping it plain keeps assembleWorld a pure function of its argument.
 */
export interface AssemblySettings {
  dueWindow: 'server-day' | 'rolling-24h';
  dateStyle: 'relative' | 'absolute';
  stepReminders: 'under-course' | 'also-in-reminders';
  snooze: 'local' | 'off';
  pause: 'local' | 'off';
  keepFinished: 'session' | 'hide';
  careLogPageSize: number;
  /** Turns the n-ask feed draws before "Read the whole thread" is offered. */
  chatTurnsKept: number;
  /** Threads kept per source, newest-touched first. */
  chatThreadsKept: number;
  displayName: string;
  units: 'metric' | 'imperial';
  quietHours: string;
  openInClassic: 'hide' | 'show';
  showApiIds: boolean;
  /** The device-local acknowledgement 'Mark all read' wrote, if any. */
  seenAt: string | null;
  /** What the bell counts — the notifications panel names it in the node's words. */
  bellCounts: 'due' | 'overdue' | 'none';
}

export type PushState = 'on' | 'off' | 'blocked' | 'unsupported' | 'unconfigured';

/** The signed-in user, for the account node (entry stays on the classic app). */
export interface WorldUser {
  firstName: string;
  lastName: string;
  email: string;
}

export interface Cell {
  col: number;
  row: number;
}

/** Everything the assembly needs, gathered from the endpoints. */
export interface WorldSources {
  /** The instant the load was made — every "due" word is measured from here. */
  now: string;
  plants: PlantDto[];
  species: SpeciesDto[];
  identifications: IdentificationDto[];
  user: WorldUser | null;
  reminders: ReminderDto[];
  careLogsByPlant: Record<number, CareLogDto[]>;
  treatments: TreatmentDto[];
  plansById: Record<number, TreatmentPlanDto>;
  dashboard: DashboardDto | null;
  preferences: UserPreferencesDto | null;
  failures: FamilyFailure[];
  settings: AssemblySettings;
  priorCells?: Record<string, Cell>;
  /** Device-local state (never server truth) — see DeviceStore. */
  paused: number[];
  snoozed: Record<number, string>;
  /** Reminders last known before they left GET /reminders (no GET-by-id exists). */
  stoppedReminders: ReminderDto[];
  rateLimited: Record<number, { retryAfterSeconds: number; at: string }>;
  push: PushState;
  /** The companion's own threads — the atlas keeps them, the server keeps none. */
  chatThreads?: ChatThreadDto[];
  /** Why the last ask on a thread did not answer, keyed by thread key. */
  chatFailures?: Record<string, ChatFailure>;
  /** Which threads the reader asked to read whole ("Read the whole thread"). */
  chatExpanded?: Record<string, boolean>;
  sessionTimes?: { issuedAt?: string; expiresAt?: string; mock?: boolean };
}

export const DEFAULT_ASSEMBLY_SETTINGS: AssemblySettings = {
  dueWindow: 'server-day',
  dateStyle: 'relative',
  stepReminders: 'under-course',
  snooze: 'local',
  pause: 'local',
  keepFinished: 'session',
  careLogPageSize: 5,
  chatTurnsKept: 3,
  chatThreadsKept: 8,
  displayName: '',
  units: 'metric',
  quietHours: '21:00-07:30',
  openInClassic: 'hide',
  showApiIds: true,
  seenAt: null,
  bellCounts: 'due',
};

/** Every field defaulted, so a caller only states what it is testing. */
export function emptySources(over: Partial<WorldSources> = {}): WorldSources {
  return {
    now: new Date().toISOString(),
    plants: [],
    species: [],
    identifications: [],
    user: null,
    reminders: [],
    careLogsByPlant: {},
    treatments: [],
    plansById: {},
    dashboard: null,
    preferences: null,
    failures: [],
    settings: { ...DEFAULT_ASSEMBLY_SETTINGS },
    paused: [],
    snoozed: {},
    stoppedReminders: [],
    rateLimited: {},
    push: 'off',
    chatThreads: [],
    chatFailures: {},
    chatExpanded: {},
    ...over,
  };
}

// ---------------------------------------------------------------------------
// Chat — the companion's wire shapes and the atlas's own thread.
//
// The server keeps no conversation: POST /api/v1/chat and /api/v1/chat/stream
// are both single-shot, and history travels in the request. So the thread is
// the atlas's own object, held on the device beside the care slice.
// ---------------------------------------------------------------------------

/** One message on the wire. The server takes any string; the classic app sends these two. */
export interface ChatMessageDto {
  role: 'user' | 'assistant';
  content: string;
}

export interface ChatRequestDto {
  message: string;
  plantId?: number;
  history?: ChatMessageDto[];
}

export interface ChatResponseDto {
  reply: string;
}

/** How a turn ended — a truncated answer wears its own line rather than pretending. */
export type ChatOutcome = 'answered' | 'truncated' | 'refused';

export interface ChatTurnDto {
  id: string;
  askedAt: string;
  question: string;
  reply: string;
  plantId?: number;
  outcome: ChatOutcome;
}

export interface ChatThreadDto {
  /** 'garden' or 'plant:<id>' — the only two shapes. */
  key: string;
  plantId?: number;
  plantName?: string;
  turns: ChatTurnDto[];
  updatedAt: string;
}

/**
 * Why an ask did not answer. `retryAfterSeconds` is null unless the body
 * actually carried it — chat's 429 comes from the generic PlantPalException
 * handler, which does not, so no wait is ever invented.
 */
export interface ChatFailure {
  kind: 'rate-limited' | 'unavailable' | 'too-long' | 'not-found' | 'blocked' | 'offline' | 'unknown';
  retryAfterSeconds: number | null;
}

/** The server's @Size(max = 2000) on ChatRequest.message, mirrored client-side. */
export const MAX_MESSAGE_CHARS = 2000;

/** The thread a question belongs to: a plant's own, or the garden's. */
export function threadKey(plantId?: number): string {
  return plantId === undefined || plantId === null ? 'garden' : `plant:${plantId}`;
}
