import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { API_BASE_URL, ApiResponse, AuthService, PageResponse } from '@plantpal/shared-core';
import { catchError, forkJoin, map, Observable, of, switchMap } from 'rxjs';
import { MOCK_MODE } from '../core/mock-mode';
import { PushService } from '../push/push.service';
import { DeviceStore } from '../settings/device.store';
import { SettingsStore } from '../settings/settings.store';
import { WorldActionsService } from './world-actions.service';
import { sessionTimes } from './interop';
import { assembleWorld, drawnPlantsOf } from './world.assembly';
import {
  CareLogDto,
  DashboardDto,
  emptySources,
  FamilyFailure,
  IdentificationDto,
  PlantDto,
  ReminderDto,
  SpeciesDto,
  TreatmentDto,
  TreatmentPlanDto,
  UserPreferencesDto,
  WorldSources,
} from './world.dto';
import { WorldData } from './world.model';
import { WorldStore } from './world.store';

/** A family's fetch, wrapped so its failure cannot take the rest of the world down. */
type Fetched<T> = { ok: T; fail?: undefined } | { ok?: undefined; fail: FamilyFailure };


/**
 * Assembles the world from the backend behind PlantPal — live, or the in-memory
 * mock garden, which answers on the same HTTP seam so nothing here knows which.
 * Rounds 1 to 3: the spine (plants, species, identifications) plus the care loop —
 * reminders, the dashboard, preferences, the active treatments of each treated
 * plant, their plans, and the care history of the plants that are drawn.
 *
 * Every family is fetched under its own tolerance: one endpoint answering 503 marks
 * that family failed and the rest of the board still arrives live, because
 * degradation is per-node material and the geography never degrades (C25).
 */
@Injectable({ providedIn: 'root' })
export class WorldGraphService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  private readonly auth = inject(AuthService);
  private readonly settings = inject(SettingsStore);
  private readonly device = inject(DeviceStore);
  private readonly store = inject(WorldStore);
  /** AI limits are learned by the action that hit one; the node says so on reload. */
  private readonly actions = inject(WorldActionsService);
  private readonly mock = inject(MOCK_MODE, { optional: true });
  /** Where this device stands with push knocks — a readout the account/reminders wear. */
  private readonly push = inject(PushService);

  /** The last sources assembled — read by the account node's export. */
  readonly lastSources = signal<WorldSources | null>(null);

  /** The fixture's cells are not the live board's — prior cells start after load one. */
  private assembled = false;

  /** Reminders last seen before they left GET /reminders (no GET-by-id exists), so a
   *  stopped one stays readable for the session instead of vanishing under the reader. */
  private readonly stoppedReminders = new Map<number, ReminderDto>();
  private lastReminders: ReminderDto[] = [];

  private get source(): 'live' | 'mock' {
    return this.mock?.enabled ? 'mock' : 'live';
  }

  /** Wrap one family's call: its failure becomes a fact, never an empty board. */
  private fam<T>(family: string, obs$: Observable<T>, ref?: number): Observable<Fetched<T>> {
    return obs$.pipe(
      map(ok => ({ ok }) as Fetched<T>),
      catchError((err: HttpErrorResponse) =>
        of({
          fail: {
            family,
            ref,
            status: err.status ?? 0,
            at: new Date().toISOString(),
            message: (err.error as { message?: string } | undefined)?.message,
          },
        } as Fetched<T>),
      ),
    );
  }

  load(): Observable<WorldData> {
    const settings = this.settings.settings();
    const page = { params: { size: String(settings.data.pageSize) } };
    const source = this.source;

    return forkJoin({
      plants: this.http
        .get<ApiResponse<PageResponse<PlantDto>>>(`${this.base}/plants`, page)
        .pipe(map(r => r.data.content)),
      species: this.http
        .get<ApiResponse<PageResponse<SpeciesDto>>>(`${this.base}/species/mine`, page)
        .pipe(map(r => r.data.content)),
      identifications: this.http
        .get<ApiResponse<PageResponse<IdentificationDto>>>(`${this.base}/identifications`, page)
        .pipe(map(r => r.data.content)),
      // GET /reminders is a plain array, not a page — read r.data directly
      reminders: this.fam(
        'reminders',
        this.http
          .get<ApiResponse<ReminderDto[]>>(`${this.base}/reminders`)
          .pipe(map(r => r.data ?? [])),
      ),
      dashboard: this.fam(
        'dashboard',
        this.http.get<ApiResponse<DashboardDto>>(`${this.base}/dashboard`).pipe(map(r => r.data)),
      ),
      preferences: this.fam(
        'users',
        this.http
          .get<ApiResponse<UserPreferencesDto>>(`${this.base}/users/me/preferences`)
          .pipe(map(r => r.data)),
      ),
    }).pipe(
      switchMap(stage1 => {
        const failures: FamilyFailure[] = [];
        const collect = <T>(f: Fetched<T>, fallback: T): T => {
          if (f.fail) {
            failures.push(f.fail);
            return fallback;
          }
          return f.ok as T;
        };
        const reminders = collect(stage1.reminders, [] as ReminderDto[]);
        const dashboard = collect(stage1.dashboard, null as DashboardDto | null);
        const preferences = collect(stage1.preferences, null as UserPreferencesDto | null);
        const plants = stage1.plants;

        // a reminder that left the list stopped — keep the last row we saw of it.
        // A family that did not answer says nothing about what stopped, so a failed
        // fetch leaves this memory untouched (only n-reminders wears the failure, C25).
        if (!stage1.reminders.fail) {
          const live = new Set(reminders.map(r => r.id));
          for (const r of this.lastReminders) if (!live.has(r.id)) this.stoppedReminders.set(r.id, r);
          for (const id of live) this.stoppedReminders.delete(id);
          this.lastReminders = reminders;
        }

        const treated = plants.filter(p => p.activeTreatmentId != null);
        const active$ = treated.map(p =>
          this.fam(
            'treatments',
            this.http
              .get<ApiResponse<TreatmentDto[]>>(`${this.base}/plants/${p.id}/active-treatments`)
              .pipe(map(r => r.data ?? [])),
          ),
        );

        const rankedPlants = drawnPlantsOf(plants);
        const careSize = settings.data.careLogPageSize;
        const care$ =
          careSize > 0
            ? rankedPlants.map(p =>
                this.fam(
                  'care',
                  this.http
                    .get<
                      ApiResponse<PageResponse<CareLogDto>>
                    >(`${this.base}/care/plant/${p.id}`, { params: { size: String(careSize) } })
                    .pipe(map(r => ({ plantId: p.id, logs: r.data.content }))),
                  p.id,
                ),
              )
            : [];

        return forkJoin({
          active: active$.length ? forkJoin(active$) : of([]),
          care: care$.length ? forkJoin(care$) : of([]),
        }).pipe(
          switchMap(stage2 => {
            const treatments: TreatmentDto[] = [];
            for (const f of stage2.active) {
              for (const t of collect(f, [] as TreatmentDto[])) {
                if (!treatments.some(x => x.id === t.id)) treatments.push(t);
                this.device.rememberTreatment(source, t.id);
              }
            }

            // a remembered course is fetched by id only when /active-treatments did
            // NOT return it — a finished course stays readable for the session
            const known =
              settings.general.keepFinished === 'session'
                ? this.device.care(source).knownTreatmentIds
                : [];
            const missing = known.filter(id => !treatments.some(t => t.id === id));
            const remembered$ = missing.map(id =>
              this.fam(
                'treatments',
                this.http.get<ApiResponse<TreatmentDto>>(`${this.base}/treatments/${id}`).pipe(
                  map(r => r.data as TreatmentDto | null),
                ),
                id,
              ).pipe(
                map(f => {
                  // a course the backend no longer has is forgotten, not mourned
                  if (f.fail?.status === 404) {
                    this.device.forgetTreatment(source, id);
                    return { ok: null } as Fetched<TreatmentDto | null>;
                  }
                  return f;
                }),
              ),
            );

            const careLogsByPlant: Record<number, CareLogDto[]> = {};
            for (const f of stage2.care) {
              const got = collect(f, null as { plantId: number; logs: CareLogDto[] } | null);
              if (got) careLogsByPlant[got.plantId] = got.logs;
            }

            return (
              remembered$.length ? forkJoin(remembered$) : of([] as Fetched<TreatmentDto | null>[])
            ).pipe(
              switchMap(fetchedRemembered => {
                for (const f of fetchedRemembered) {
                  const t = collect(f, null as TreatmentDto | null);
                  if (t && !treatments.some(x => x.id === t.id)) treatments.push(t);
                }

                const planIds = [
                  ...new Set(
                    treatments
                      .map(t => t.treatmentPlanId)
                      .filter((id): id is number => typeof id === 'number'),
                  ),
                ];
                const plans$ = planIds.map(id =>
                  this.fam(
                    'treatment-plans',
                    this.http
                      .get<ApiResponse<TreatmentPlanDto>>(`${this.base}/treatment-plans/${id}`)
                      .pipe(map(r => r.data)),
                    id,
                  ),
                );

                return (plans$.length ? forkJoin(plans$) : of([])).pipe(
                  map(fetchedPlans => {
                const plansById: Record<number, TreatmentPlanDto> = {};
                for (const f of fetchedPlans) {
                  const plan = collect(f, null as TreatmentPlanDto | null);
                  if (plan) plansById[plan.id] = plan;
                }
                const now = new Date().toISOString();
                this.device.pruneSnoozed(source, now);
                const local = this.device.care(source);
                const sources = emptySources({
                  now,
                  plants,
                  species: stage1.species,
                  identifications: stage1.identifications,
                  user: this.auth.getCurrentUser(),
                  reminders,
                  careLogsByPlant,
                  treatments,
                  plansById,
                  dashboard,
                  preferences,
                  failures,
                  settings: this.settings.assemblySnapshot(),
                  priorCells: this.assembled ? this.store.cellsSnapshot() : this.store.storedCells(),
                  paused: local.pausedPlanIds,
                  snoozed: local.snoozed,
                  stoppedReminders: [...this.stoppedReminders.values()],
                  rateLimited: this.actions.rateLimited(),
                  push: this.push.state(),
                  sessionTimes: this.mock?.enabled
                    ? { mock: true }
                    : sessionTimes(this.auth.getToken()),
                });
                this.lastSources.set(sources);
                this.store.lastSources.set(sources);
                this.assembled = true;
                return assembleWorld(sources);
                  }),
                );
              }),
            );
          }),
        );
      }),
    );
  }
}
