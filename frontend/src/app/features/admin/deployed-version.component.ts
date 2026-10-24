import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
  signal,
} from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { catchError, map, of } from "rxjs";
import { AdminService } from "./admin.service";

const FULL_SHA = /^[0-9a-f]{40}$/;

/** Shows the commit each deployed half was built from; each side fails independently. */
@Component({
  selector: "app-deployed-version",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="deployed-version" aria-label="Deployed version">
      <h3>Deployed version</h3>
      <dl>
        <dt>Backend</dt>
        <dd data-testid="backend-revision" [title]="backend() ?? ''">
          {{ backend() ? backend()!.slice(0, 7) : "not reported" }}
        </dd>
        <dt>Frontend</dt>
        <dd data-testid="frontend-revision" [title]="frontend() ?? ''">
          {{ frontend() ? frontend()!.slice(0, 7) : "not reported" }}
        </dd>
      </dl>
    </section>
  `,
  styles: [
    ".deployed-version{margin:1rem 0;font-size:.85rem}.deployed-version h3{margin:0 0 .25rem;font-size:.95rem}dl{display:grid;grid-template-columns:max-content 1fr;gap:.15rem 1rem;margin:0}dd{margin:0;font-family:monospace}",
  ],
})
export class DeployedVersionComponent implements OnInit {
  private readonly api = inject(AdminService);
  private readonly http = inject(HttpClient);
  readonly backend = signal<string | null>(null);
  readonly frontend = signal<string | null>(null);

  ngOnInit(): void {
    this.api
      .deployment()
      .pipe(
        map((d) => valid(d?.revision)),
        catchError(() => of(null)),
      )
      .subscribe((r) => this.backend.set(r));
    this.http
      .get<{ revision?: unknown }>(`/version.json?t=${Date.now()}`, {
        headers: { "Cache-Control": "no-store" },
      })
      .pipe(
        map((v) => valid(v?.revision)),
        catchError(() => of(null)),
      )
      .subscribe((r) => this.frontend.set(r));
  }
}

function valid(value: unknown): string | null {
  return typeof value === "string" && FULL_SHA.test(value) ? value : null;
}
