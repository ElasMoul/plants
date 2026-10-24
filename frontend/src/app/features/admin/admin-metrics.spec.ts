import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import {
  HttpTestingController,
  provideHttpClientTesting,
} from "@angular/common/http/testing";
import { DestroyRef } from "@angular/core";
import { of, throwError } from "rxjs";
import { API_BASE_URL } from "@plantpal/shared-core";
import { AdminService } from "./admin.service";
import { AdminComponent } from "./admin.component";

const sample = {
  from: "2026-10-01",
  to: "2026-10-31",
  scans: { total: 4, completed: 2, failed: 1, pending: 1 },
  errors: { failedScans: 1 },
  aiUsage: { totalRequests: 8, scanRequests: 3, perDay: [] },
  cost: { estimated: null, perRequestRate: null, basis: "rate not configured" },
};

describe("AdminService.metrics", () => {
  it("requests the range and unwraps all four groups", () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: "/api/v1" },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    let result: unknown;
    TestBed.inject(AdminService)
      .metrics("2026-10-01", "2026-10-31")
      .subscribe((m) => (result = m));
    const req = http.expectOne((r) => r.url === "/api/v1/admin/metrics");
    expect(req.request.params.get("from")).toBe("2026-10-01");
    expect(req.request.params.get("to")).toBe("2026-10-31");
    req.flush({ success: true, data: sample });
    expect(result).toEqual(sample);
  });
});

describe("AdminComponent.loadMetrics", () => {
  function ctx(metrics: jest.Mock, from = "2026-10-01", to = "2026-10-31") {
    return {
      api: { metrics },
      destroyRef: { onDestroy: () => () => undefined } as unknown as DestroyRef,
      metricsFrom: from,
      metricsTo: to,
      metricsError: true,
      metrics: undefined,
    };
  }
  it("stores the metrics", () => {
    const c = ctx(jest.fn().mockReturnValue(of(sample)));
    AdminComponent.prototype.loadMetrics.call(c);
    expect(c.metrics).toEqual(sample);
    expect(c.metricsError).toBe(false);
  });
  it("flags an error", () => {
    const c = ctx(jest.fn().mockReturnValue(throwError(() => new Error("x"))));
    AdminComponent.prototype.loadMetrics.call(c);
    expect(c.metricsError).toBe(true);
  });
  it("skips the call when a date is missing", () => {
    const fn = jest.fn();
    AdminComponent.prototype.loadMetrics.call(ctx(fn, ""));
    expect(fn).not.toHaveBeenCalled();
  });
});
