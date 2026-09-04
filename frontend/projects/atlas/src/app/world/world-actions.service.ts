import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { forkJoin, Observable, of } from 'rxjs';
import { API_BASE_URL, ApiResponse } from '@plantpal/shared-core';
import { MOCK_MODE } from '../core/mock-mode';
import { DataSource, DeviceStore } from '../settings/device.store';
import { SettingsStore } from '../settings/settings.store';
import type { CareLogDto, CareType, ReminderDto, TreatmentDto } from './world.dto';
import { WorldStore } from './world.store';

/** An open in-world form (design-system material). */
export type ActiveForm =
  | { kind: 'add-plant' }
  | { kind: 'add-note'; plantId: number; plantName: string }
  | { kind: 'identify' }
  | { kind: 'add-reminder'; plantId?: number; plantName?: string; careType?: CareType }
  | {
      kind: 'log-care';
      reminderId?: number;
      plantId?: number;
      plantName?: string;
      careType?: CareType;
    }
  | {
      kind: 'change-schedule';
      reminderId: number;
      plantId: number;
      plantName: string;
      careType: CareType;
      frequencyDays: number;
    }
  | { kind: 'start-treatment'; plantId?: number; plantName?: string; identificationId?: number };

/** `data-arg="reminder:701"` — the one convention every stake uses to name its row. */
export interface StakeRef {
  kind: 'plant' | 'reminder' | 'treatment' | 'plan';
  id: number;
}

const DAY_MS = 86_400_000;

/**
 * Every stake's real behaviour (H6). A mutation writes through the real endpoint,
 * announces what changed, and asks for a reload — it NEVER moves the camera or the
 * focus (C15/C16): there is deliberately no call to store.go / frameFocus / camera
 * in this file. Behaviours PlantPal has no endpoint for (snooze, pause) are device
 * local and say so in their own words; anything else is refused honestly.
 */
@Injectable({ providedIn: 'root' })
export class WorldActionsService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  private readonly store = inject(WorldStore);
  private readonly settings = inject(SettingsStore);
  private readonly device = inject(DeviceStore);
  private readonly mock = inject(MOCK_MODE, { optional: true });

  /** The currently open form, if any. */
  readonly activeForm = signal<ActiveForm | null>(null);

  /** Bumped when a mutation succeeded and the world should re-assemble. */
  readonly reloadRequested = signal(0);

  /** treatment id → the AI limit it hit, so its node can say so (read by the loader). */
  readonly rateLimited = signal<Record<number, { retryAfterSeconds: number; at: string }>>({});

  private get source(): DataSource {
    return this.mock?.enabled ? 'mock' : 'live';
  }

  /** Dispatch a stake/action button press for the given node. */
  dispatch(nodeId: string, label: string, arg?: string): void {
    if (this.store.probeOffline()) {
      this.store.say(`Offline: “${label}” is queued. It will run when you are back.`);
      return;
    }
    const l = label.toLowerCase();
    const ref = this.refOf(arg);
    const plant = /^n-plant-(\d+)$/.exec(nodeId);
    const care = this.settings.settings().care;

    if (/add (a |new )?plant/.test(l)) {
      this.activeForm.set({ kind: 'add-plant' });
      return;
    }
    if (l === 'add note' && (plant || ref?.kind === 'plant')) {
      const id = ref?.kind === 'plant' ? ref.id : Number(plant?.[1]);
      this.activeForm.set({ kind: 'add-note', plantId: id, plantName: this.plantName(id) });
      return;
    }

    // ── the care loop ─────────────────────────────────────────────────────────
    if (/^done$|^mark as done$|^mark today done$|^mark step \d+ as done$/.test(l)) {
      const id = ref?.kind === 'reminder' ? ref.id : this.nextStepOf(nodeId);
      if (id == null) {
        this.store.say('Nothing is due on this course today.');
        return;
      }
      this.completeReminder(id, undefined, 'Step marked done. The camera did not move.');
      return;
    }
    if (/^water plant$/.test(l)) {
      this.careFor(this.plantIdOf(nodeId, ref), 'WATERING', 'Watered. The camera did not move.');
      return;
    }
    if (/^fertilize$/.test(l)) {
      this.careFor(this.plantIdOf(nodeId, ref), 'FERTILIZING', 'Fed. The camera did not move.');
      return;
    }
    if (/^water (all|everything due)$/.test(l)) {
      this.waterAll();
      return;
    }
    if (/^set a watering schedule$|^add a reminder$|^fertilize schedule$/.test(l)) {
      const id = this.plantIdOf(nodeId, ref);
      this.activeForm.set({
        kind: 'add-reminder',
        plantId: id,
        plantName: id == null ? undefined : this.plantName(id),
        careType: /fertilize/.test(l) ? 'FERTILIZING' : 'WATERING',
      });
      return;
    }
    if (/^change the schedule$/.test(l)) {
      this.openChangeSchedule(ref);
      return;
    }
    if (/^log a watering$/.test(l)) {
      const id = this.plantIdOf(nodeId, ref);
      if (id == null || care.askForNotes) {
        this.activeForm.set({
          kind: 'log-care',
          plantId: id,
          plantName: id == null ? undefined : this.plantName(id),
          careType: 'WATERING',
        });
        return;
      }
      this.careFor(id, 'WATERING', 'Watering logged. The camera did not move.');
      return;
    }
    if (/^snooze/.test(l)) {
      this.onSnooze(ref);
      return;
    }
    if (/^stop this reminder$|^turn watering off$/.test(l)) {
      if (ref?.kind !== 'reminder') {
        this.store.say('That reminder is not named here.');
        return;
      }
      this.stopReminder(ref.id);
      return;
    }
    if (/^start a treatment plan$/.test(l)) {
      const id = this.plantIdOf(nodeId, ref);
      this.activeForm.set({
        kind: 'start-treatment',
        plantId: id,
        plantName: id == null ? undefined : this.plantName(id),
        identificationId: id == null ? undefined : this.store.meta()?.scansByPlant[id],
      });
      return;
    }
    if (/^craft the treatment plan$/.test(l)) {
      const id = ref?.kind === 'treatment' ? ref.id : this.treatmentIdOf(nodeId);
      if (id != null) this.craftPlan(id);
      return;
    }
    if (/^write it up again$/.test(l)) {
      const id = ref?.kind === 'treatment' ? ref.id : this.treatmentIdOf(nodeId);
      if (id != null) this.regenerateDescription(id);
      return;
    }
    if (/^finish this course$|^mark the treatment finished$/.test(l)) {
      const id = ref?.kind === 'treatment' ? ref.id : this.treatmentIdOf(nodeId);
      if (id != null) this.completeTreatment(id);
      return;
    }
    if (/^(pause|resume) this course$/.test(l)) {
      const planId = ref?.kind === 'plan' ? ref.id : this.planIdOf(nodeId);
      if (planId != null) this.togglePause(planId);
      return;
    }
    if (/^add the steps by hand$/.test(l)) {
      const treatmentId = ref?.kind === 'treatment' ? ref.id : this.treatmentIdOf(nodeId);
      const plantId =
        treatmentId == null ? undefined : this.store.meta()?.treatmentsIndex[treatmentId]?.plantId;
      this.activeForm.set({
        kind: 'add-reminder',
        plantId,
        plantName: plantId == null ? undefined : this.plantName(plantId),
        careType: 'PEST',
      });
      return;
    }

    if (/try the scan again|retry/.test(l)) {
      this.retryLatestScan();
      return;
    }
    if (/check health/.test(l)) {
      this.healthCheck();
      return;
    }
    if (/identify|scan leaf/.test(l)) {
      this.activeForm.set({ kind: 'identify' });
      return;
    }
    if (/add a species|import a list/.test(l)) {
      // a species is born from an identification — the identify flow lives HERE
      this.store.say('A species is born from an identification — scan the plant.');
      this.activeForm.set({ kind: 'identify' });
      return;
    }
    if (/^fetch this region$|^count again$/.test(l)) {
      this.store.say('Fetching the region. Nothing else moves while it arrives.');
      this.reloadRequested.update(v => v + 1);
      return;
    }
    this.store.say(`“${label}” is not something PlantPal can do from here yet.`);
  }

  // ── reminders and care ──────────────────────────────────────────────────────

  /** Completes a reminder through the verb the settings chose; a 400 that says it is
   *  already done is the truth catching up, not a failure. */
  completeReminder(id: number, notes?: string, message = 'Done. The camera did not move.'): void {
    const verb = this.settings.settings().care.completeVerb;
    const req$: Observable<ApiResponse<unknown>> =
      verb === 'care/done'
        ? this.http.post<ApiResponse<CareLogDto>>(`${this.base}/care/done`, {
            reminderId: id,
            ...(notes ? { notes } : {}),
          })
        : this.http.post<ApiResponse<ReminderDto>>(`${this.base}/reminders/${id}/complete`, {});
    req$.subscribe({
      next: () => this.settled(message),
      error: (err: HttpErrorResponse) => {
        const msg = (err.error as { message?: string } | undefined)?.message ?? '';
        if (err.status === 400 && /already been completed/i.test(msg)) {
          this.settled('Already done — the board is catching up.');
          return;
        }
        this.store.say(msg || 'That could not be recorded. Nothing was lost — try again.');
      },
    });
  }

  /** One press of care on a plant: complete its nearest schedule of that kind, or
   *  follow care.logWithoutReminder when it has none. */
  careFor(
    plantId: number | undefined,
    careType: CareType,
    message: string,
    opts?: { notes?: string; direct?: boolean },
  ): void {
    if (plantId == null) {
      this.store.say('That plant is not named here.');
      return;
    }
    const hit = this.remindersOf(plantId, careType)[0];
    if (hit) {
      if (this.settings.settings().care.askForNotes && !opts?.direct) {
        this.activeForm.set({
          kind: 'log-care',
          reminderId: hit.id,
          plantId,
          plantName: this.plantName(plantId),
          careType,
        });
        return;
      }
      this.completeReminder(hit.id, opts?.notes, message);
      return;
    }
    const name = this.plantName(plantId);
    if (this.settings.settings().care.logWithoutReminder === 'refuse') {
      this.store.say(`No ${careType.toLowerCase()} schedule on ${name} yet — set one and this becomes one press.`);
      this.activeForm.set({ kind: 'add-reminder', plantId, plantName: name, careType });
      return;
    }
    // create the schedule this care is measured against, then log against it
    this.http
      .post<ApiResponse<ReminderDto>>(`${this.base}/reminders`, {
        plantId,
        careType,
        frequencyDays: this.settings.settings().care.defaultFrequencyDays,
        firstDueAt: new Date().toISOString(),
      })
      .subscribe({
        next: res => this.completeReminder(res.data.id, opts?.notes, message),
        error: (err: HttpErrorResponse) =>
          this.store.say(
            (err.error as { message?: string } | undefined)?.message ??
              'The schedule could not be created — try again.',
          ),
      });
  }

  /** Completes every watering the scope covers, and counts them out loud. */
  waterAll(): void {
    const scope = this.settings.settings().care.waterAllScope;
    const endOfToday = new Date();
    endOfToday.setHours(23, 59, 59, 999);
    const list = (this.store.meta()?.reminders ?? []).filter(
      r =>
        r.enabled &&
        r.recurring &&
        r.careType === 'WATERING' &&
        (scope === 'all-watering' || Date.parse(r.nextDueAt) <= endOfToday.getTime()),
    );
    if (!list.length) {
      this.store.say('Nothing is due for water.');
      return;
    }
    const verb = this.settings.settings().care.completeVerb;
    const calls: Observable<ApiResponse<unknown>>[] = list.map(r =>
      verb === 'care/done'
        ? this.http.post<ApiResponse<CareLogDto>>(`${this.base}/care/done`, { reminderId: r.id })
        : this.http.post<ApiResponse<ReminderDto>>(`${this.base}/reminders/${r.id}/complete`, {}),
    );
    // forkJoin over an empty array never emits — the guard above keeps it non-empty
    (calls.length ? forkJoin(calls) : of([])).subscribe({
      next: () =>
        this.settled(
          `Watered ${list.length} ${list.length === 1 ? 'plant' : 'plants'}. The camera did not move.`,
        ),
      error: () => this.store.say('Some of those could not be recorded — the board keeps what it knows.'),
    });
  }

  createReminder(req: {
    plantId: number;
    careType: CareType;
    frequencyDays: number;
    firstDueAt: string;
  }): void {
    this.http.post<ApiResponse<ReminderDto>>(`${this.base}/reminders`, req).subscribe({
      next: () =>
        this.settled('The reminder is set. A new node takes a free cell — nothing else moves.'),
      error: (err: HttpErrorResponse) =>
        this.store.say(
          (err.error as { message?: string } | undefined)?.message ??
            'The reminder could not be set — try again.',
        ),
    });
  }

  /** A new schedule replaces the old reminder: create first, retire second, and say
   *  so plainly if only the retiring failed. */
  changeSchedule(
    oldId: number,
    req: { plantId: number; careType: CareType; frequencyDays: number; firstDueAt: string },
  ): void {
    this.http.post<ApiResponse<ReminderDto>>(`${this.base}/reminders`, req).subscribe({
      next: () => {
        this.http
          .delete(`${this.base}/reminders/${oldId}`, { observe: 'response' })
          .subscribe({
            next: () => this.settled('The schedule is changed. Nothing else was touched.'),
            error: () =>
              this.settled(
                'The new schedule is set but the old one is still there — retire it from Reminders.',
              ),
          });
      },
      error: (err: HttpErrorResponse) =>
        this.store.say(
          (err.error as { message?: string } | undefined)?.message ??
            'The schedule could not be changed — try again.',
        ),
    });
  }

  /** Soft-disable: the row leaves GET /reminders, and the loader keeps the last one
   *  it saw so the node stays readable rather than vanishing. */
  stopReminder(id: number): void {
    this.http.delete(`${this.base}/reminders/${id}`, { observe: 'response' }).subscribe({
      next: () => this.settled('Stopped. It stays readable here until you leave.'),
      error: () => this.store.say('That reminder could not be stopped — try again.'),
    });
  }

  /** Device-local, and named as such: PlantPal's server keeps the original date. */
  snooze(ids: number[]): void {
    if (!ids.length) {
      this.store.say('Nothing overdue to snooze.');
      return;
    }
    const until = new Date();
    until.setDate(until.getDate() + 1);
    until.setHours(9, 0, 0, 0);
    this.device.snooze(this.source, ids, until.toISOString());
    this.settled(
      "Snoozed until tomorrow, on this device — PlantPal's server keeps the original date.",
    );
  }

  // ── treatments ──────────────────────────────────────────────────────────────

  startTreatment(req: { plantId: number; diseaseName: string; identificationId?: number }): void {
    this.http.post<ApiResponse<TreatmentDto>>(`${this.base}/treatments`, req).subscribe({
      next: res => {
        this.activeForm.set(null);
        if (this.settings.settings().ai.craftPlanOnStart) {
          this.craftPlan(res.data.id);
          return;
        }
        this.settled(`“${req.diseaseName}” started as a draft. Its plan is written when you ask.`);
      },
      error: (err: HttpErrorResponse) =>
        this.store.say(
          (err.error as { message?: string } | undefined)?.message ??
            'The treatment could not be started — try again.',
        ),
    });
  }

  craftPlan(id: number): void {
    this.store.say('Crafting the plan — this can take a while; the answer lands in this node.');
    this.http.post<ApiResponse<TreatmentDto>>(`${this.base}/treatments/${id}/craft-plan`, {}).subscribe({
      next: () => this.settled('The plan is written. Its steps are on this course.'),
      error: (err: HttpErrorResponse) => {
        const body = err.error as { message?: string; retryAfterSeconds?: number } | undefined;
        if (err.status === 429) {
          const seconds = body?.retryAfterSeconds ?? 900;
          this.rateLimited.update(v => ({ ...v, [id]: { retryAfterSeconds: seconds, at: new Date().toISOString() } }));
          this.store.say(
            `You have used today's AI plans. They come back in ${Math.max(1, Math.round(seconds / 60))} minutes, and everything else still works.`,
          );
          this.reloadRequested.update(v => v + 1);
          return;
        }
        this.store.say(body?.message ?? 'The plan could not be written — the course is unchanged.');
      },
    });
  }

  completeTreatment(id: number): void {
    this.http.patch<ApiResponse<TreatmentDto>>(`${this.base}/treatments/${id}/complete`, {}).subscribe({
      next: () => this.settled('The course is finished. It stays on the plant as part of its story.'),
      error: (err: HttpErrorResponse) =>
        this.store.say(
          (err.error as { message?: string } | undefined)?.message ??
            'The course could not be finished — try again.',
        ),
    });
  }

  regenerateDescription(id: number): void {
    this.http
      .post<ApiResponse<TreatmentDto>>(`${this.base}/treatments/${id}/regenerate-description`, {})
      .subscribe({
        next: () => this.settled('Describing it again. The answer arrives into this node.'),
        error: (err: HttpErrorResponse) =>
          this.store.say(
            (err.error as { message?: string } | undefined)?.message ??
              'The write-up could not be started again — try again.',
          ),
      });
  }

  /** Pausing lives on this device only — PlantPal has no pause. */
  togglePause(planId: number): void {
    const paused = this.device.care(this.source).pausedPlanIds.includes(planId);
    if (paused) {
      this.device.resumePlan(this.source, planId);
      this.settled('Resumed on this device. The due dates never changed.');
    } else {
      this.device.pausePlan(this.source, planId);
      this.settled('Paused on this device — PlantPal keeps the original dates.');
    }
  }

  // ── round-1 verbs (unchanged) ───────────────────────────────────────────────

  createPlant(req: { nickname: string; species?: string; location?: string; notes?: string }): void {
    this.http.post<ApiResponse<{ id: number; nickname: string }>>(`${this.base}/plants`, req).subscribe({
      next: res => {
        this.activeForm.set(null);
        this.store.say(`“${res.data.nickname}” planted. A new node takes a free cell — nothing else moves.`);
        this.reloadRequested.update(v => v + 1);
      },
      error: err => this.store.say(err?.error?.message ?? 'The plant could not be saved. Nothing was lost — try again.'),
    });
  }

  addNote(plantId: number, note: string): void {
    this.http.put<ApiResponse<unknown>>(`${this.base}/plants/${plantId}`, { notes: note }).subscribe({
      next: () => {
        this.activeForm.set(null);
        this.store.say('Note recorded. The camera did not move.');
        this.reloadRequested.update(v => v + 1);
      },
      error: err => this.store.say(err?.error?.message ?? 'The note could not be saved — try again.'),
    });
  }

  retryLatestScan(): void {
    const id = this.store.latestFailedScanId();
    if (id == null) {
      this.activeForm.set({ kind: 'identify' });
      this.store.say('No failed scan to retry — start a new one.');
      return;
    }
    this.http.post<ApiResponse<unknown>>(`${this.base}/identifications/${id}/retry`, {}).subscribe({
      next: () => {
        this.store.say('The scan is running again. The answer arrives into this node.');
        this.reloadRequested.update(v => v + 1);
      },
      error: err => this.store.say(err?.error?.message ?? 'The retry could not start — try again.'),
    });
  }

  /** In-atlas identification: multipart analyze → async pipeline → polling. */
  identify(images: File[], organ: string, userContext?: string): void {
    const form = new FormData();
    images.forEach(img => form.append('images', img, img.name));
    form.append('organs', organ);
    if (userContext?.trim()) form.append('userContext', userContext.trim());
    this.http.post<ApiResponse<{ identificationId: number }>>(`${this.base}/identifications/analyze`, form).subscribe({
      next: () => {
        this.activeForm.set(null);
        this.store.say('The scan is running. The answer arrives into the Identification node — nothing moves while it does.');
        this.reloadRequested.update(v => v + 1);
      },
      error: err => this.store.say(err?.error?.message ?? 'The scan could not start. Your photo was not lost — try again.'),
    });
  }

  /** app.health — a real end-to-end call, timed, reported in the node's words. */
  healthCheck(): void {
    const t0 = performance.now();
    this.http.get<ApiResponse<unknown>>(`${this.base}/plants`, { params: { size: 1 } }).subscribe({
      next: () => this.store.say(`Backend answered in ${Math.round(performance.now() - t0)}ms · UP.`),
      error: () => this.store.say('The backend did not answer. The board keeps what it already knows.'),
    });
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  /** Every successful mutation ends the same way: close, announce, ask for a reload. */
  private settled(message: string): void {
    this.activeForm.set(null);
    this.store.say(message);
    this.reloadRequested.update(v => v + 1);
  }

  private refOf(arg: string | undefined): StakeRef | null {
    const m = /^(plant|reminder|treatment|plan):(\d+)$/.exec(arg ?? '');
    return m ? { kind: m[1] as StakeRef['kind'], id: Number(m[2]) } : null;
  }

  private plantIdOf(nodeId: string, ref: StakeRef | null): number | undefined {
    if (ref?.kind === 'plant') return ref.id;
    if (ref?.kind === 'reminder') {
      return (this.store.meta()?.reminders ?? []).find(r => r.id === ref.id)?.plantId;
    }
    const m = /^n-plant-(\d+)$/.exec(nodeId);
    if (m) return Number(m[1]);
    const t = this.treatmentIdOf(nodeId);
    return t == null ? undefined : this.store.meta()?.treatmentsIndex[t]?.plantId;
  }

  private plantName(id: number): string {
    return (
      this.store.meta()?.plantsIndex.find(p => p.id === id)?.nickname ??
      this.store.nodes().find(n => n.id === `n-plant-${id}`)?.name ??
      'this plant'
    );
  }

  private treatmentIdOf(nodeId: string): number | undefined {
    const m = /^n-treatment-(\d+)$/.exec(nodeId);
    return m ? Number(m[1]) : undefined;
  }

  private planIdOf(nodeId: string): number | undefined {
    const t = this.treatmentIdOf(nodeId);
    return t == null ? undefined : this.store.meta()?.treatmentsIndex[t]?.planId;
  }

  /** The first open step of the course this node is (the rail has no data-arg). */
  private nextStepOf(nodeId: string): number | undefined {
    const t = this.treatmentIdOf(nodeId);
    return t == null ? undefined : this.store.meta()?.treatmentsIndex[t]?.nextStepId;
  }

  /** This plant's enabled routine schedules of one kind, nearest due first. */
  private remindersOf(plantId: number, careType: CareType): ReminderDto[] {
    return (this.store.meta()?.reminders ?? [])
      .filter(r => r.enabled && r.recurring && r.plantId === plantId && r.careType === careType)
      .sort((a, b) => Date.parse(a.nextDueAt) - Date.parse(b.nextDueAt));
  }

  private openChangeSchedule(ref: StakeRef | null): void {
    const r =
      ref?.kind === 'reminder'
        ? (this.store.meta()?.reminders ?? []).find(x => x.id === ref.id)
        : undefined;
    if (!r) {
      this.store.say('That schedule is not named here.');
      return;
    }
    this.activeForm.set({
      kind: 'change-schedule',
      reminderId: r.id,
      plantId: r.plantId,
      plantName: r.plantNickname ?? this.plantName(r.plantId),
      careType: r.careType as CareType,
      frequencyDays: r.frequencyDays || this.settings.settings().care.defaultFrequencyDays,
    });
  }

  private onSnooze(ref: StakeRef | null): void {
    if (this.settings.settings().reminders.snooze === 'off') {
      this.store.say('Snoozing is not something PlantPal keeps yet.');
      return;
    }
    if (ref?.kind === 'reminder') {
      this.snooze([ref.id]);
      return;
    }
    const now = Date.now();
    const overdue = (this.store.meta()?.reminders ?? [])
      .filter(r => r.enabled && r.recurring && Date.parse(r.nextDueAt) <= now + DAY_MS)
      .map(r => r.id);
    this.snooze(overdue);
  }
}
