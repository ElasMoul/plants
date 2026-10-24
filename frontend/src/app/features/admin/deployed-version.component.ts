import { HttpClient } from "@angular/common/http";
import { Component, inject, OnInit } from "@angular/core";
import { catchError, map, Observable, of } from "rxjs";
import { TranslatePipe } from "../../shared/i18n/translate.pipe";
import { AdminService } from "./admin.service";

const FULL_SHA = /^[0-9a-f]{40}$/;

/** A reported revision, or null when the side is unreported or its read failed. */
function revisionOrNull(value: unknown): string | null {
  return typeof value === "string" && FULL_SHA.test(value) ? value : null;
}

/**
 * "Deployed version" panel (PLA-50). The backend revision comes from the admin API, the
 * frontend revision from this origin's own /version.json; each side fails independently
 * and null or a failed read is shown as "not reported".
 */
@Component({
  selector: "app-deployed-version",
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <article class="panel" data-testid="deployed-version">
      <div class="panel-heading">
        <div>
          <p class="eyebrow">{{ "Deployed version" | t }}</p>
          <h2>{{ "What is running" | t }}</h2>
        </div>
      </div>
      @for (side of sides; track side.key) {
        <p>
          {{ side.label | t }}:
          @if (side.revision; as sha) {
            <code [attr.data-testid]="side.key + '-revision'" [title]="sha">{{
              sha.slice(0, 7)
            }}</code>
          } @else {
            <span [attr.data-testid]="side.key + '-revision'">{{
              "not reported" | t
            }}</span>
          }
        </p>
      }
    </article>
  `,
})
export class DeployedVersionComponent implements OnInit {
  private readonly api = inject(AdminService);
  private readonly http = inject(HttpClient);
  readonly sides: { key: string; label: string; revision: string | null }[] = [
    { key: "backend", label: "Backend", revision: null },
    { key: "frontend", label: "Frontend", revision: null },
  ];

  ngOnInit(): void {
    this.load(this.backendRevision(), 0);
    this.load(this.frontendRevision(), 1);
  }

  private backendRevision(): Observable<string | null> {
    return this.api.deployment().pipe(
      map((identity) => revisionOrNull(identity?.["revision"])),
      catchError(() => of(null)),
    );
  }

  private frontendRevision(): Observable<string | null> {
    // A relative URL keeps the read on this app's own origin; the cache-buster and
    // no-store make sure a redeploy is never masked by a cached version.json.
    return this.http
      .get<{ revision?: unknown }>("/version.json", {
        params: { t: Date.now() },
        headers: { "Cache-Control": "no-cache" },
      })
      .pipe(
        map((body) => revisionOrNull(body?.revision)),
        catchError(() => of(null)),
      );
  }

  private load(source: Observable<string | null>, index: number): void {
    source.subscribe((revision) => (this.sides[index].revision = revision));
  }
}
