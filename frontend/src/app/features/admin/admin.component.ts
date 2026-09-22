import { CommonModule } from "@angular/common";
import { Component, DestroyRef, inject, OnInit } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute, Router, RouterModule } from "@angular/router";
import { AdminIconComponent } from "./admin-icon.component";
import { HttpErrorResponse } from "@angular/common/http";
import {
  catchError,
  EMPTY,
  finalize,
  forkJoin,
  Observable,
  Subject,
  switchMap,
  tap,
} from "rxjs";
import {
  AdminAccess,
  AdminPlant,
  AdminActivity,
  AdminModel,
  AdminOverview,
  AdminPage,
  AdminService,
  AdminUser,
} from "./admin.service";

@Component({
  selector: "app-admin",
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, AdminIconComponent],
  templateUrl: "./admin.component.html",
  styleUrls: [
    "./admin.component.scss",
    "./admin-overview.scss",
    "./admin-people.scss",
    "./admin-models.scss",
    "./admin-responsive.scss",
    "./admin-gardens.scss",
  ],
})
export class AdminComponent implements OnInit {
  private readonly api = inject(AdminService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload$ = new Subject<void>();
  readonly view = this.route.snapshot.data["view"] as string;
  readonly navigation = [
    { path: "/admin", label: "Overview", icon: "space_dashboard", exact: true },
    {
      path: "/admin/users",
      label: "People",
      icon: "people_outline",
      exact: false,
    },
    {
      path: "/admin/ai",
      label: "AI studio",
      icon: "auto_awesome",
      exact: false,
    },
    {
      path: "/admin/activity",
      label: "Activity log",
      icon: "history",
      exact: false,
    },
  ];
  readonly titles: Record<string, string> = {
    overview: "A little perspective. A lot of growth.",
    users: "The people behind the plants.",
    detail: "Make room for every grower.",
    ai: "Thoughtful intelligence, by design.",
    activity: "Every change has a story.",
  };
  readonly descriptions: Record<string, string> = {
    overview:
      "A living picture of your PlantPal community, from first scans to ongoing care.",
    users: "Find a grower, manage access, and keep your community flourishing.",
    detail:
      "Manage this account’s profile, membership, and access to PlantPal.",
    ai: "Curate the models your community discovers in AI Settings.",
    activity: "A transparent record of administrative changes across PlantPal.",
  };
  access?: AdminAccess;
  overview?: AdminOverview;
  people?: AdminPage<AdminUser>;
  events?: AdminPage<AdminActivity>;
  user?: AdminUser;
  original?: AdminUser;
  models: AdminModel[] = [];
  savedVisibility: Record<string, boolean> = {};
  query = "";
  status = "";
  page = 0;
  loading = true;
  saving = false;
  savingModel = "";
  error = "";
  notice = "";
  confirming = false;
  plants?: AdminPage<AdminPlant>;
  plantStatus = "ACTIVE";
  plantPage = 0;
  plantsLoading = false;
  editingPlant?: AdminPlant;
  plantAction?: AdminPlant;
  savingPlant = false;

  ngOnInit(): void {
    this.api
      .access()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (a) => (this.access = a) });
    this.reload$
      .pipe(
        switchMap(() => {
          this.loading = true;
          this.error = "";
          return this.load().pipe(
            catchError((error: HttpErrorResponse) => {
              this.error = this.message(error);
              return EMPTY;
            }),
            finalize(() => (this.loading = false)),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        this.query = params.get("q") ?? "";
        this.status = params.get("status") ?? "";
        this.page = Math.max(0, Number(params.get("page")) || 0);
        this.reload$.next();
      });
  }

  refresh(): void {
    this.reload$.next();
  }

  search(page = 0): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: this.query || null,
        status: this.status || null,
        page: page || null,
      },
    });
  }

  requestSave(): void {
    this.notice = "";
    if (
      this.user?.role !== this.original?.role ||
      this.user?.status !== this.original?.status
    ) {
      this.confirming = true;
    } else this.saveUser();
  }

  saveUser(): void {
    if (!this.user || this.saving) return;
    this.saving = true;
    this.error = "";
    this.api
      .saveUser(this.user)
      .pipe(
        finalize(() => (this.saving = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (user) => {
          this.user = { ...user };
          this.original = { ...user };
          this.confirming = false;
          this.notice = "Changes saved. Account access is up to date.";
        },
        error: (e: HttpErrorResponse) => {
          this.confirming = false;
          this.error = this.message(e);
        },
      });
  }

  changeAccess(status: AdminUser["status"]): void {
    if (!this.user || this.user.id === this.access?.userId) return;
    this.user.status = status;
    this.requestSave();
  }

  loadPlants(page = 0): void {
    if (!this.user) return;
    this.plantPage = page;
    this.plantsLoading = true;
    this.api
      .plants(this.user.id, this.plantStatus, page)
      .pipe(
        finalize(() => (this.plantsLoading = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (plants) => (this.plants = plants),
        error: (e: HttpErrorResponse) => (this.error = this.message(e)),
      });
  }

  savePlant(): void {
    if (!this.user || !this.editingPlant || this.savingPlant) return;
    this.savingPlant = true;
    this.api
      .savePlant(this.user.id, this.editingPlant)
      .pipe(
        finalize(() => (this.savingPlant = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.editingPlant = undefined;
          this.notice = "Plant details saved.";
          this.loadPlants(this.plantPage);
        },
        error: (e: HttpErrorResponse) => (this.error = this.message(e)),
      });
  }

  confirmPlantAction(): void {
    if (!this.user || !this.plantAction || this.savingPlant) return;
    this.savingPlant = true;
    const action: Observable<unknown> =
      this.plantAction.status === "ACTIVE"
        ? this.api.archivePlant(this.user.id, this.plantAction.id)
        : this.api.restorePlant(this.user.id, this.plantAction.id);
    action
      .pipe(
        finalize(() => (this.savingPlant = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.plantAction = undefined;
          this.notice = "Garden updated.";
          this.loadPlants();
          this.refresh();
        },
        error: (e: HttpErrorResponse) => {
          this.plantAction = undefined;
          this.error = this.message(e);
        },
      });
  }

  editPlant(plant: AdminPlant): void {
    this.editingPlant = { ...plant };
  }

  resetUser(): void {
    if (this.original) this.user = { ...this.original };
    this.confirming = false;
  }

  saveModel(model: AdminModel): void {
    if (this.savingModel) return;
    this.savingModel = model.id;
    this.error = "";
    this.notice = "";
    this.api
      .saveModel(model)
      .pipe(
        finalize(() => (this.savingModel = "")),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (saved) => {
          Object.assign(model, saved);
          this.savedVisibility[saved.id] = saved.visible;
          this.notice = `${this.modelName(saved.model)} ${saved.visible ? "is now offered" : "is now hidden"} in ${saved.capability.toLowerCase()} Settings.`;
        },
        error: (e: HttpErrorResponse) => (this.error = this.message(e)),
      });
  }

  modelName(model: string): string {
    return (
      (
        {
          DEEPSEEK_FLASH: "DeepSeek V4.1 Flash",
          ANTHROPIC_CLAUDE: "Claude",
          GITHUB_GPT4O: "GPT-4o",
          GITHUB_GPT41: "GPT-4.1",
          OLLAMA_GEMMA3: "Gemma 3",
          PLANTNET: "PlantNet",
          DEEPSEEK_R1: "DeepSeek R1",
          GITHUB_O4_MINI: "o4-mini",
          GITHUB_GPT41_MINI: "GPT-4.1 mini",
        } as Record<string, string>
      )[model] ?? model
    );
  }

  provider(model: string): string {
    if (model === "DEEPSEEK_FLASH") return "DeepSeek · direct API";
    if (model.startsWith("ANTHROPIC")) return "Anthropic";
    if (model.startsWith("OLLAMA")) return "Ollama · local";
    if (model === "PLANTNET") return "Botanical identification";
    return "GitHub Models";
  }

  modelGroup(capability: string): AdminModel[] {
    return this.models.filter((m) => m.capability === capability);
  }
  get visibleModels(): number {
    return this.models.filter((m) => this.savedVisibility[m.id]).length;
  }
  get dirty(): boolean {
    return JSON.stringify(this.user) !== JSON.stringify(this.original);
  }
  get totalPages(): number {
    return (this.view === "users" ? this.people : this.events)?.totalPages ?? 0;
  }
  get maxScans(): number {
    return Math.max(
      1,
      ...(this.overview?.scans.map((d) => d.completed + d.failed + d.pending) ??
        []),
    );
  }
  get scanTotal(): number {
    return (
      this.overview?.scans.reduce(
        (sum, d) => sum + d.completed + d.failed + d.pending,
        0,
      ) ?? 0
    );
  }
  get successRate(): string {
    const total =
      this.overview?.scans.reduce((s, d) => s + d.completed + d.failed, 0) ?? 0;
    const completed =
      this.overview?.scans.reduce((s, d) => s + d.completed, 0) ?? 0;
    return total ? `${Math.round((completed / total) * 100)}%` : "—";
  }
  initials(user: AdminUser): string {
    return `${user.firstName[0] ?? ""}${user.lastName[0] ?? ""}`;
  }

  private load(): Observable<unknown> {
    if (this.view === "overview")
      return forkJoin({
        overview: this.api.overview(),
        activity: this.api.activity(0),
      }).pipe(
        tap((data) => {
          this.overview = data.overview;
          this.events = data.activity;
        }),
      );
    if (this.view === "users")
      return this.api
        .users(this.query, this.status, this.page)
        .pipe(tap((p) => (this.people = p)));
    if (this.view === "detail")
      return this.api.user(this.route.snapshot.paramMap.get("id")!).pipe(
        tap((user) => {
          this.user = { ...user };
          this.original = { ...user };
          this.loadPlants();
        }),
      );
    if (this.view === "activity")
      return this.api.activity(this.page).pipe(tap((p) => (this.events = p)));
    return this.api.models().pipe(
      tap((p) => {
        this.models = p.content;
        this.savedVisibility = Object.fromEntries(
          p.content.map((m) => [m.id, m.visible]),
        );
      }),
    );
  }

  private message(error: HttpErrorResponse): string {
    if (error.status === 403)
      return "Your account no longer has administrator access.";
    return (
      error.error?.message ?? "We couldn’t reach PlantPal. Please try again."
    );
  }
}
