import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import {
  HttpTestingController,
  provideHttpClientTesting,
} from "@angular/common/http/testing";
import { API_BASE_URL } from "@plantpal/shared-core";
import { DeployedVersionComponent } from "./deployed-version.component";

const BE = "a".repeat(40);
const FE = "b".repeat(40);

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
  const api = http.expectOne("/api/v1/admin/deployment");
  const ver = http.expectOne((r) => r.url.startsWith("/version.json"));
  const text = (id: string) =>
    fixture.nativeElement.querySelector(`[data-testid=${id}]`);
  return { api, ver, fixture, text };
}

describe("DeployedVersionComponent", () => {
  it("shows both revisions, full SHA on hover", () => {
    const { api, ver, fixture, text } = setup();
    api.flush({ success: true, data: { revision: BE } });
    ver.flush({ revision: FE });
    fixture.detectChanges();
    expect(text("backend-revision").textContent.trim()).toBe("aaaaaaa");
    expect(text("backend-revision").title).toBe(BE);
    expect(text("frontend-revision").title).toBe(FE);
  });

  it("shows not reported for null revisions", () => {
    const { api, ver, fixture, text } = setup();
    api.flush({ success: true, data: { revision: null } });
    ver.flush({ revision: null });
    fixture.detectChanges();
    expect(text("backend-revision").textContent).toContain("not reported");
    expect(text("frontend-revision").textContent).toContain("not reported");
  });

  it("keeps the backend value when the version.json fetch fails", () => {
    const { api, ver, fixture, text } = setup();
    api.flush({ success: true, data: { revision: BE } });
    ver.flush("nope", { status: 404, statusText: "Not Found" });
    fixture.detectChanges();
    expect(text("backend-revision").title).toBe(BE);
    expect(text("frontend-revision").textContent).toContain("not reported");
  });

  it("keeps the frontend value when the admin API fails", () => {
    const { api, ver, fixture, text } = setup();
    api.flush("no", { status: 403, statusText: "Forbidden" });
    ver.flush({ revision: FE });
    fixture.detectChanges();
    expect(text("backend-revision").textContent).toContain("not reported");
    expect(text("frontend-revision").title).toBe(FE);
  });
});
