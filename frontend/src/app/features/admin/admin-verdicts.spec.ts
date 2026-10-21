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

describe("AdminService.verdictCounts", () => {
  it("requests the period and unwraps the counts", () => {
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
      .verdictCounts("2026-10-01", "2026-10-31")
      .subscribe((c) => (result = c));
    const req = http.expectOne(
      (r) => r.url === "/api/v1/admin/identification-verdicts",
    );
    expect(req.request.params.get("from")).toBe("2026-10-01");
    expect(req.request.params.get("to")).toBe("2026-10-31");
    req.flush({ success: true, data: { right: 2, wrong: 1, unsure: 0 } });
    expect(result).toEqual({ right: 2, wrong: 1, unsure: 0 });
  });
});

describe("AdminComponent.loadVerdicts", () => {
  function ctx(verdictCounts: jest.Mock, from = "2026-10-01", to = "2026-10-31") {
    return {
      api: { verdictCounts },
      destroyRef: { onDestroy: () => () => undefined } as unknown as DestroyRef,
      verdictFrom: from,
      verdictTo: to,
      verdictError: true,
      verdicts: undefined,
    };
  }

  it("stores the counts", () => {
    const c = ctx(jest.fn().mockReturnValue(of({ right: 1, wrong: 2, unsure: 3 })));
    AdminComponent.prototype.loadVerdicts.call(c);
    expect(c.verdicts).toEqual({ right: 1, wrong: 2, unsure: 3 });
    expect(c.verdictError).toBe(false);
  });

  it("flags an error", () => {
    const c = ctx(jest.fn().mockReturnValue(throwError(() => new Error("x"))));
    AdminComponent.prototype.loadVerdicts.call(c);
    expect(c.verdictError).toBe(true);
  });

  it("skips the call when a date is missing", () => {
    const fn = jest.fn();
    AdminComponent.prototype.loadVerdicts.call(ctx(fn, ""));
    expect(fn).not.toHaveBeenCalled();
  });
});
