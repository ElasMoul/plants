import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import {
  HttpTestingController,
  provideHttpClientTesting,
} from "@angular/common/http/testing";
import { API_BASE_URL } from "@plantpal/shared-core";
import { DeployedVersionComponent } from "./deployed-version.component";

const BACKEND = "0123456789abcdef0123456789abcdef01234567";
const FRONTEND = "fedcba9876543210fedcba9876543210fedcba98";

function setup() {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: API_BASE_URL, useValue: "/api/v1" },
    ],
  });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(DeployedVersionComponent);
  fixture.detectChanges();
  const backend = () => http.expectOne("/api/v1/admin/deployment");
  const frontend = () => http.expectOne((r) => r.url === "/version.json");
  const text = (key: string) => {
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector(
      `[data-testid="${key}-revision"]`,
    );
    return { text: el.textContent.trim(), title: el.getAttribute("title") };
  };
  return { http, backend, frontend, text };
}

describe("DeployedVersionComponent", () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it("shows the backend value from the API and the frontend value from /version.json", () => {
    const c = setup();
    c.backend().flush({
      success: true,
      data: { appIdentity: "plantpal", revision: BACKEND },
    });
    c.frontend().flush({ revision: FRONTEND });
    expect(c.text("backend")).toEqual({
      text: BACKEND.slice(0, 7),
      title: BACKEND,
    });
    expect(c.text("frontend")).toEqual({
      text: FRONTEND.slice(0, 7),
      title: FRONTEND,
    });
  });

  it("shows 'not reported' for null revisions", () => {
    const c = setup();
    c.backend().flush({ success: true, data: { revision: null } });
    c.frontend().flush({ revision: null });
    expect(c.text("backend").text).toBe("not reported");
    expect(c.text("frontend").text).toBe("not reported");
  });

  it("shows 'not reported' for a malformed revision", () => {
    const c = setup();
    c.backend().flush({ success: true, data: { revision: "abc123" } });
    c.frontend().flush({ revision: BACKEND.toUpperCase() });
    expect(c.text("backend").text).toBe("not reported");
    expect(c.text("frontend").text).toBe("not reported");
  });

  it("keeps the frontend value when the backend call fails", () => {
    const c = setup();
    c.backend().flush({ message: "boom" }, { status: 500, statusText: "err" });
    c.frontend().flush({ revision: FRONTEND });
    expect(c.text("backend").text).toBe("not reported");
    expect(c.text("frontend").title).toBe(FRONTEND);
  });

  it("keeps the backend value when /version.json fails", () => {
    const c = setup();
    c.backend().flush({ success: true, data: { revision: BACKEND } });
    c.frontend().flush("missing", { status: 404, statusText: "Not Found" });
    expect(c.text("backend").title).toBe(BACKEND);
    expect(c.text("frontend").text).toBe("not reported");
  });
});
