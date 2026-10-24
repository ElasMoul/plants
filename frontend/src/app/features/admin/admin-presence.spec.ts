import { provideHttpClient } from "@angular/common/http";
import {
  HttpTestingController,
  provideHttpClientTesting,
} from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { API_BASE_URL } from "@plantpal/shared-core";
import { of, throwError } from "rxjs";

import { AdminComponent } from "./admin.component";
import { AdminService } from "./admin.service";

describe("AdminService.presence", () => {
  it("reads the aggregate from the admin presence endpoint", () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: "/api/v1" },
      ],
    });
    let result: unknown;
    TestBed.inject(AdminService)
      .presence()
      .subscribe((r) => (result = r));
    const req = TestBed.inject(HttpTestingController).expectOne(
      "/api/v1/admin/presence",
    );
    req.flush({
      success: true,
      data: { onlineCount: 4, windowSeconds: 300, asOf: "2026-10-24T10:00:00Z" },
    });
    expect(result).toEqual({
      onlineCount: 4,
      windowSeconds: 300,
      asOf: "2026-10-24T10:00:00Z",
    });
  });
});

describe("AdminComponent.loadPresence", () => {
  const load = AdminComponent.prototype.loadPresence;
  const destroyRef = { onDestroy: () => () => undefined };

  it("stores the count and window", () => {
    const presence = { onlineCount: 2, windowSeconds: 300, asOf: "x" };
    const c: any = { api: { presence: () => of(presence) }, destroyRef };
    load.call(c);
    expect(c.presence).toEqual(presence);
    expect(AdminComponent.prototype.presenceWindow(300)).toBe("5 min");
    expect(AdminComponent.prototype.presenceWindow(90)).toBe("90 s");
  });

  it("flags an error without throwing", () => {
    const c: any = {
      api: { presence: () => throwError(() => new Error("403")) },
      destroyRef,
    };
    load.call(c);
    expect(c.presenceError).toBe(true);
  });
});
