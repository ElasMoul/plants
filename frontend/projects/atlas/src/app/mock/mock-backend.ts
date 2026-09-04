import { inject, Injectable } from '@angular/core';
import { MOCK_MODE } from '../core/mock-mode';
import { environment } from '../../environments/environment';
import type { DashboardDto } from '../world/world.dto';
import {
  buildMockSeed,
  derivePlant,
  MockCareLog,
  MockReminder,
  MockScenario,
  MockSeed,
  MockTreatment,
  PLAN_TEMPLATES,
  seedDashboard,
} from './mock.dataset';

const DAY = 86400000;

export interface MockReply {
  status: number;
  body: unknown;
}

/** The backend's ApiResponse<T> envelope, minus the nulls Jackson omits. */
export function envelope(data: unknown, message?: string): unknown {
  return stripNulls({ success: true, data, message, timestamp: new Date().toISOString() });
}

function errorBody(message: string, errorCode: number, extra: Record<string, unknown> = {}): unknown {
  return stripNulls({ success: false, message, errorCode, timestamp: new Date().toISOString(), ...extra });
}

/** Spring's Page<T> shape, as the classic PageResponse reads it. */
export function page<T>(list: T[], size: number, pageIdx = 0): unknown {
  const from = pageIdx * size;
  const content = list.slice(from, from + size);
  const totalPages = size > 0 ? Math.max(1, Math.ceil(list.length / size)) : 1;
  return {
    content,
    totalElements: list.length,
    totalPages,
    size,
    number: pageIdx,
    first: pageIdx === 0,
    last: pageIdx >= totalPages - 1,
    empty: content.length === 0,
    numberOfElements: content.length,
  };
}

/** Recursively drop null/undefined — the backend never serialises them. */
export function stripNulls<T>(value: T): T {
  if (Array.isArray(value)) {
    return value.map(v => stripNulls(v)) as unknown as T;
  }
  if (value && typeof value === 'object') {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      if (v === null || v === undefined) continue;
      out[k] = stripNulls(v);
    }
    return out as T;
  }
  return value;
}

class MockHttpError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly extra: Record<string, unknown> = {},
  ) {
    super(message);
  }
}

export function notFound(message: string): MockHttpError {
  return new MockHttpError(404, message);
}
export function badRequest(message: string): MockHttpError {
  return new MockHttpError(400, message);
}

/**
 * The server's completion cascade: a recurring reminder moves forward, a step
 * disables itself, and the last step of a plan completes the plan, the wrapping
 * treatment, and clears the plant's active-treatment pointer.
 */
export function applyCompletion(seed: MockSeed, reminder: MockReminder, performedAt: string): void {
  if (!reminder.enabled) {
    throw badRequest('This reminder has already been completed');
  }
  if (reminder.recurring) {
    reminder.nextDueAt = new Date(Date.parse(performedAt) + reminder.frequencyDays * DAY).toISOString();
    reminder.updatedAt = performedAt;
    return;
  }
  reminder.enabled = false;
  reminder.updatedAt = performedAt;

  const planId = reminder.treatmentPlanId;
  if (planId === undefined) return;
  const remaining = seed.reminders.some(r => r.treatmentPlanId === planId && r.enabled);
  if (remaining) return;

  const plan = seed.treatmentPlans.find(p => p.id === planId);
  if (plan) plan.status = 'COMPLETED';
  // The plant's activeTreatmentId is derived from its treatments on every read,
  // so completing the treatment IS clearing the pointer.
  for (const t of seed.treatments) {
    if (t.treatmentPlanId === planId && t.status === 'IN_PROGRESS') {
      t.status = 'COMPLETED';
      t.completedAt = performedAt;
    }
  }
}

interface Route {
  method: string;
  re: RegExp;
  run: (m: RegExpExecArray, body: unknown, query: URLSearchParams) => MockReply;
}

/**
 * An in-memory PlantPal, answering in the backend's exact wire shape. Held for
 * the page lifetime; a reload rebuilds it. Never touched in live mode — the
 * interceptor only calls it when MOCK_MODE.enabled.
 */
@Injectable({ providedIn: 'root' })
export class MockBackend {
  private seed!: MockSeed;
  private readonly ticks: Record<string, number> = {};

  /** Set by the Advanced pane's "Make the next change fail" stake (mock only). */
  failNext = false;

  private readonly routes: Route[] = this.buildRoutes();

  constructor() {
    const mode = inject(MOCK_MODE, { optional: true });
    this.reset(mode?.scenario ?? 'garden', Date.now());
  }

  get state(): MockSeed {
    return this.seed;
  }

  reset(scenario: MockScenario, now: number): void {
    this.seed = buildMockSeed(scenario, now);
    this.ticks['identifications'] = 2;
    this.ticks['treatment:302'] = 2;
    this.failNext = false;
    if (!environment.production && typeof window !== 'undefined') {
      (window as unknown as { __atlasMock?: unknown }).__atlasMock = this;
    }
  }

  /** `path` is everything after API_BASE_URL, query included. */
  handle(method: string, pathWithQuery: string, body: unknown): MockReply {
    const qIdx = pathWithQuery.indexOf('?');
    const path = qIdx >= 0 ? pathWithQuery.slice(0, qIdx) : pathWithQuery;
    const query = new URLSearchParams(qIdx >= 0 ? pathWithQuery.slice(qIdx + 1) : '');
    const verb = method.toUpperCase();

    for (const f of this.seed.failing) {
      if (f.method === verb && f.re.test(path)) {
        return { status: 503, body: errorBody('The service behind PlantPal did not answer (503). The board keeps what it already knows.', 503) };
      }
    }
    if (verb !== 'GET' && this.failNext) {
      this.failNext = false;
      return { status: 503, body: errorBody('The service behind PlantPal did not answer (503). Nothing was changed.', 503) };
    }

    for (const route of this.routes) {
      if (route.method !== verb) continue;
      const m = route.re.exec(path);
      if (!m) continue;
      try {
        const reply = route.run(m, body, query);
        return { status: reply.status, body: reply.body === null ? null : stripNulls(reply.body) };
      } catch (e) {
        if (e instanceof MockHttpError) {
          return { status: e.status, body: errorBody(e.message, e.status, e.extra) };
        }
        throw e;
      }
    }
    return { status: 404, body: errorBody(`No such place in the mock backend: ${verb} ${path}`, 404) };
  }

  // ---------- helpers over the seed ----------

  private clock(): string {
    return new Date().toISOString();
  }

  private size(query: URLSearchParams): number {
    const raw = Number(query.get('size'));
    return Number.isFinite(raw) && raw > 0 ? raw : 20;
  }

  /** Zero-based page index from the query, as Spring reads it. */
  private pageIdx(query: URLSearchParams): number {
    const raw = Number(query.get('page'));
    return Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : 0;
  }

  private plant(id: number) {
    const p = this.seed.plants.find(x => x.id === id && x.status === 'ACTIVE');
    if (!p) throw notFound('Plant not found');
    return p;
  }

  private reminder(id: number): MockReminder {
    const r = this.seed.reminders.find(x => x.id === id);
    if (!r) throw notFound('Reminder not found');
    return r;
  }

  private treatment(id: number): MockTreatment {
    const t = this.seed.treatments.find(x => x.id === id);
    if (!t) throw notFound('Treatment not found');
    return t;
  }

  /** ReminderResponse — completedAt is derived from updatedAt when disabled. */
  private reminderOut(r: MockReminder): unknown {
    return { ...r, completedAt: r.enabled ? undefined : r.updatedAt };
  }

  private tick(key: string): boolean {
    const left = this.ticks[key];
    if (left === undefined) return false;
    const next = left - 1;
    if (next <= 0) {
      delete this.ticks[key];
      return true;
    }
    this.ticks[key] = next;
    return false;
  }

  private dashboard(): DashboardDto {
    return seedDashboard(this.seed, Date.now());
  }

  // ---------- the route table ----------

  private buildRoutes(): Route[] {
    const num = (m: RegExpExecArray, i: number) => Number(m[i]);
    return [
      // --- plants ---
      {
        method: 'GET', re: /^\/plants$/, run: (_m, _b, q) => ({
          status: 200,
          body: envelope(page(this.seed.plants.filter(p => p.status === 'ACTIVE').map(p => derivePlant(this.seed, p, Date.now())), this.size(q), this.pageIdx(q))),
        }),
      },
      { method: 'GET', re: /^\/plants\/(\d+)$/, run: m => ({ status: 200, body: envelope(derivePlant(this.seed, this.plant(num(m, 1)), Date.now())) }) },
      {
        method: 'POST', re: /^\/plants$/, run: (_m, b) => {
          const req = (b ?? {}) as Record<string, string>;
          if (!req['nickname']) throw badRequest('Nickname is required');
          const id = this.seed.nextId.plant++;
          this.seed.plants.push({
            id, nickname: req['nickname'], species: req['species'],
            location: req['location'], notes: req['notes'], status: 'ACTIVE',
          });
          return { status: 201, body: envelope(derivePlant(this.seed, this.plant(id), Date.now()), 'Plant created successfully') };
        },
      },
      {
        method: 'PUT', re: /^\/plants\/(\d+)$/, run: (m, b) => {
          const p = this.plant(num(m, 1));
          const req = (b ?? {}) as Record<string, string | undefined>;
          for (const key of ['nickname', 'species', 'location', 'notes'] as const) {
            if (req[key] !== undefined) p[key] = req[key];
          }
          return { status: 200, body: envelope(derivePlant(this.seed, p, Date.now()), 'Plant updated successfully') };
        },
      },
      {
        method: 'DELETE', re: /^\/plants\/(\d+)$/, run: m => {
          this.plant(num(m, 1)).status = 'ARCHIVED';
          return { status: 200, body: envelope(undefined, 'Plant archived successfully') };
        },
      },

      // --- species ---
      { method: 'GET', re: /^\/species\/mine$/, run: (_m, _b, q) => ({ status: 200, body: envelope(page(this.seed.species, this.size(q), this.pageIdx(q))) }) },

      // --- identifications ---
      {
        method: 'GET', re: /^\/identifications$/, run: (_m, _b, q) => {
          if (this.tick('identifications')) {
            for (const i of this.seed.identifications) {
              if (i.status === 'PENDING') {
                i.status = 'COMPLETED';
                i.species = 'Citrus × limon';
                i.commonName = 'Lemon tree';
                i.healthStatus = 'HEALTHY';
              }
            }
          }
          const list = [...this.seed.identifications].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
          return { status: 200, body: envelope(page(list, this.size(q), this.pageIdx(q))) };
        },
      },
      {
        method: 'POST', re: /^\/identifications\/analyze$/, run: () => {
          const id = this.seed.nextId.identification++;
          this.seed.identifications.push({ id, status: 'PENDING', createdAt: this.clock() });
          this.ticks['identifications'] = 2;
          return { status: 202, body: envelope({ identificationId: id }, 'Identification started') };
        },
      },
      {
        method: 'POST', re: /^\/identifications\/(\d+)\/retry$/, run: m => {
          const i = this.seed.identifications.find(x => x.id === num(m, 1));
          if (!i) throw notFound('Identification not found');
          if (i.status !== 'FAILED') throw badRequest('Only a failed identification can be retried');
          i.status = 'PENDING';
          this.ticks['identifications'] = 2;
          return { status: 200, body: envelope(i, 'Identification retry started') };
        },
      },

      // --- reminders ---
      {
        method: 'GET', re: /^\/reminders$/, run: () => {
          const list = this.seed.reminders
            .filter(r => r.enabled)
            .sort((a, b) => Date.parse(a.nextDueAt) - Date.parse(b.nextDueAt))
            .slice(0, 200)
            .map(r => this.reminderOut(r));
          return { status: 200, body: envelope(list) };
        },
      },
      {
        method: 'POST', re: /^\/reminders$/, run: (_m, b) => {
          const req = (b ?? {}) as { plantId?: number; careType?: string; frequencyDays?: number; firstDueAt?: string };
          if (!req.frequencyDays || req.frequencyDays < 1) throw badRequest('Frequency must be at least 1 day');
          const plant = this.plant(Number(req.plantId));
          const r: MockReminder = {
            id: this.seed.nextId.reminder++,
            plantId: plant.id,
            plantNickname: plant.nickname,
            careType: req.careType ?? 'WATERING',
            frequencyDays: req.frequencyDays,
            nextDueAt: req.firstDueAt ?? this.clock(),
            enabled: true,
            recurring: true,
            updatedAt: this.clock(),
          };
          this.seed.reminders.push(r);
          return { status: 201, body: envelope(this.reminderOut(r), 'Reminder created successfully') };
        },
      },
      {
        method: 'POST', re: /^\/reminders\/(\d+)\/complete$/, run: m => {
          const r = this.reminder(num(m, 1));
          applyCompletion(this.seed, r, this.clock());
          return { status: 200, body: envelope(this.reminderOut(r), 'Reminder completed') };
        },
      },
      {
        method: 'DELETE', re: /^\/reminders\/(\d+)$/, run: m => {
          const r = this.reminder(num(m, 1));
          this.seed.reminders.splice(this.seed.reminders.indexOf(r), 1);
          return { status: 204, body: null };
        },
      },

      // --- care logs ---
      {
        method: 'POST', re: /^\/care\/done$/, run: (_m, b) => {
          const req = (b ?? {}) as { reminderId?: number; notes?: string };
          const r = this.reminder(Number(req.reminderId));
          const performedAt = this.clock();
          const log: MockCareLog = {
            id: this.seed.nextId.careLog++,
            plantId: r.plantId,
            plantNickname: r.plantNickname,
            careType: r.careType,
            notes: req.notes,
            performedAt,
          };
          applyCompletion(this.seed, r, performedAt);
          this.seed.careLogs.push(log);
          return { status: 201, body: envelope(log, 'Care logged successfully') };
        },
      },
      {
        method: 'GET', re: /^\/care\/plant\/(\d+)$/, run: (m, _b, q) => {
          const p = this.plant(num(m, 1));
          const list = this.seed.careLogs
            .filter(l => l.plantId === p.id)
            .sort((a, b) => Date.parse(b.performedAt) - Date.parse(a.performedAt));
          return { status: 200, body: envelope(page(list, this.size(q), this.pageIdx(q))) };
        },
      },

      // --- treatment plans ---
      {
        method: 'POST', re: /^\/treatment-plans$/, run: (_m, b) => {
          const req = (b ?? {}) as {
            plantId?: number; title?: string; sourceCareCardType?: string;
            actionPlan?: { type?: string; steps?: { instruction?: string; dueOffsetDays?: number; detail?: string }[] };
          };
          const type = req.actionPlan?.type?.toUpperCase();
          const steps = req.actionPlan?.steps ?? [];
          const careTypes = ['WATERING', 'LIGHT', 'HUMIDITY', 'TEMPERATURE', 'FERTILIZING', 'REPOTTING', 'PRUNING', 'PEST', 'SEASONAL', 'BEGINNER_TIP'];
          if (type !== 'TREATMENT') throw badRequest('Action plan must be of type TREATMENT');
          if (!steps.length) throw badRequest('A treatment plan needs at least one step');
          if (!req.sourceCareCardType || !careTypes.includes(req.sourceCareCardType)) throw badRequest('Unknown care type');
          const plant = this.plant(Number(req.plantId));
          const planId = this.seed.nextId.plan++;
          const title = req.title ?? 'Treatment';
          this.seed.treatmentPlans.push({ id: planId, plantId: plant.id, title, status: 'ACTIVE', createdAt: this.clock() });
          steps.forEach((s, i) => this.materialiseStep(planId, plant.id, plant.nickname, title, req.sourceCareCardType as string, i + 1, s.instruction ?? '', s.dueOffsetDays ?? 0, s.detail));
          return { status: 201, body: envelope(this.planOut(planId), 'Treatment plan created successfully') };
        },
      },
      {
        method: 'GET', re: /^\/treatment-plans\/(\d+)$/, run: m => {
          const id = num(m, 1);
          if (!this.seed.treatmentPlans.some(p => p.id === id)) throw notFound('Treatment plan not found');
          return { status: 200, body: envelope(this.planOut(id)) };
        },
      },

      // --- treatments ---
      {
        method: 'POST', re: /^\/treatments$/, run: (_m, b) => {
          const req = (b ?? {}) as { plantId?: number; diseaseName?: string; identificationId?: number };
          const plant = this.plant(Number(req.plantId));
          const disease = req.diseaseName ?? 'Unnamed problem';
          const clash = this.seed.treatments.some(
            t => t.plantId === plant.id && t.diseaseName === disease && (t.status === 'DRAFT' || t.status === 'IN_PROGRESS'),
          );
          if (clash) throw badRequest('An active treatment already exists for this plant and disease');
          const id = this.seed.nextId.treatment++;
          this.seed.treatments.push({
            id, plantId: plant.id, diseaseName: disease, status: 'DRAFT', descriptionStatus: 'PENDING',
            identificationId: req.identificationId, needsReview: false, createdAt: this.clock(),
          });
          this.ticks[`treatment:${id}`] = 2;
          return { status: 201, body: envelope(this.treatment(id), 'Treatment created successfully') };
        },
      },
      {
        method: 'POST', re: /^\/treatments\/(\d+)\/craft-plan$/, run: m => {
          const id = num(m, 1);
          const t = this.treatment(id);
          if (t.status !== 'DRAFT') throw badRequest('Treatment plan can only be crafted from DRAFT status');
          const limited = this.seed.flags.rateLimitOnceTreatmentIds;
          const at = limited.indexOf(id);
          if (at >= 0) {
            limited.splice(at, 1);
            throw new MockHttpError(429, 'AI rate limit reached', { retryAfterSeconds: 900 });
          }
          const plant = this.plant(t.plantId);
          const planId = this.seed.nextId.plan++;
          const template = PLAN_TEMPLATES[t.diseaseName] ?? PLAN_TEMPLATES['default'];
          this.seed.treatmentPlans.push({ id: planId, plantId: plant.id, title: t.diseaseName, status: 'ACTIVE', createdAt: this.clock() });
          template.forEach((s, i) =>
            this.materialiseStep(planId, plant.id, plant.nickname, t.diseaseName, 'PEST', i + 1, s.instruction, s.dueOffsetDays, s.detail),
          );
          t.status = 'IN_PROGRESS';
          t.startedAt = this.clock();
          t.treatmentPlanId = planId;
          t.treatmentPlanModel = this.seed.preferences.reasoningModelPreference;
          return { status: 200, body: envelope(t, 'Treatment plan crafted') };
        },
      },
      {
        method: 'GET', re: /^\/treatments\/(\d+)$/, run: m => {
          const id = num(m, 1);
          const t = this.treatment(id);
          if (t.descriptionStatus === 'PENDING' && this.tick(`treatment:${id}`)) {
            t.descriptionStatus = 'READY';
            t.diseaseDescription = `${t.diseaseName} shows itself early; the notes below say what to look for and what to do next.`;
            t.diseaseDescriptionModel = this.seed.preferences.reasoningModelPreference;
          }
          return { status: 200, body: envelope(t) };
        },
      },
      {
        method: 'PATCH', re: /^\/treatments\/(\d+)\/complete$/, run: m => {
          const t = this.treatment(num(m, 1));
          if (t.status !== 'IN_PROGRESS') throw badRequest('Only an IN_PROGRESS treatment can be completed');
          t.status = 'COMPLETED';
          t.completedAt = this.clock();
          return { status: 200, body: envelope(t, 'Treatment completed') };
        },
      },
      {
        method: 'POST', re: /^\/treatments\/(\d+)\/regenerate-description$/, run: m => {
          const id = num(m, 1);
          const t = this.treatment(id);
          t.descriptionStatus = 'PENDING';
          t.diseaseDescription = undefined;
          this.ticks[`treatment:${id}`] = 2;
          return { status: 202, body: envelope(t, 'Description is being written again') };
        },
      },
      {
        method: 'GET', re: /^\/plants\/(\d+)\/active-treatment$/, run: m => {
          const p = this.plant(num(m, 1));
          const t = this.activeTreatments(p.id)[0];
          if (!t) throw notFound('No active treatment for this plant');
          return { status: 200, body: envelope(t) };
        },
      },
      {
        method: 'GET', re: /^\/plants\/(\d+)\/active-treatments$/, run: m => {
          const p = this.plant(num(m, 1));
          return { status: 200, body: envelope(this.activeTreatments(p.id)) };
        },
      },

      // --- dashboard, notifications, preferences ---
      { method: 'GET', re: /^\/dashboard$/, run: () => ({ status: 200, body: envelope(this.dashboard()) }) },
      {
        method: 'POST', re: /^\/notifications\/subscribe$/, run: (_m, b) => {
          const req = (b ?? {}) as { endpoint?: string; keyP256dh?: string; keyAuth?: string };
          if (!req.endpoint?.trim() || !req.keyP256dh?.trim() || !req.keyAuth?.trim()) {
            throw badRequest('A push subscription needs an endpoint and both keys');
          }
          this.seed.pushSubscriptions.push({ endpoint: req.endpoint, keyP256dh: req.keyP256dh, keyAuth: req.keyAuth });
          return { status: 201, body: envelope(undefined, 'Push subscription registered') };
        },
      },
      { method: 'GET', re: /^\/users\/me\/preferences$/, run: () => ({ status: 200, body: envelope(this.seed.preferences) }) },
      {
        method: 'PUT', re: /^\/users\/me\/preferences$/, run: (_m, b) => {
          const req = (b ?? {}) as Record<string, unknown>;
          for (const [k, v] of Object.entries(req)) {
            if (v === undefined || v === null) continue;
            (this.seed.preferences as unknown as Record<string, unknown>)[k] = v;
          }
          return { status: 200, body: envelope(this.seed.preferences, 'Preferences updated successfully') };
        },
      },
    ];
  }

  private activeTreatments(plantId: number): MockTreatment[] {
    return this.seed.treatments
      .filter(t => t.plantId === plantId && (t.status === 'DRAFT' || t.status === 'IN_PROGRESS'))
      .sort((a, b) => b.id - a.id);
  }

  private materialiseStep(
    planId: number, plantId: number, plantNickname: string, planTitle: string,
    careType: string, order: number, instruction: string, dueOffsetDays: number, detail?: string,
  ): void {
    this.seed.reminders.push({
      id: this.seed.nextId.reminder++,
      plantId,
      plantNickname,
      careType,
      frequencyDays: 0,
      nextDueAt: new Date(Date.now() + dueOffsetDays * DAY).toISOString(),
      enabled: true,
      recurring: false,
      updatedAt: this.clock(),
      treatmentPlanId: planId,
      treatmentPlanTitle: planTitle,
      stepOrder: order,
      instruction,
      stepDetail: detail,
    });
  }

  private planOut(planId: number): unknown {
    const plan = this.seed.treatmentPlans.find(p => p.id === planId);
    const steps = this.seed.reminders
      .filter(r => r.treatmentPlanId === planId)
      .sort((a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0))
      .map(r => this.reminderOut(r));
    return { ...plan, steps };
  }
}
