import { test, expect, Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const grower = {
  id: 2,
  firstName: "Sofia",
  lastName: "Bennett",
  email: "sofia@example.test",
  status: "ACTIVE",
  role: "USER",
  businessTier: false,
  createdAt: "2026-07-15T10:00:00Z",
  visionModel: "ANTHROPIC_CLAUDE",
  reasoningModel: "OLLAMA_GEMMA3",
  version: 0,
  maxPlants: 100,
  dailyScanLimit: 20,
  dailyAiLimit: 100,
  usage: { plants: 1, scansToday: 2, aiToday: 3 },
};
const names = [
  ["Sofia", "Bennett"],
  ["Oliver", "Chen"],
  ["Amara", "Okafor"],
  ["Noah", "Williams"],
  ["Lina", "Hassan"],
  ["James", "Park"],
];
const catalog = [
  "VISION:ANTHROPIC_CLAUDE",
  "VISION:GITHUB_GPT4O",
  "VISION:GITHUB_GPT41",
  "VISION:OLLAMA_GEMMA3",
  "VISION:PLANTNET",
  "REASONING:ANTHROPIC_CLAUDE",
  "REASONING:DEEPSEEK_R1",
  "REASONING:GITHUB_GPT41_MINI",
  "REASONING:GITHUB_O4_MINI",
  "REASONING:OLLAMA_GEMMA3",
];

async function setup(page: Page, administrator = true) {
  await page.addInitScript(() => {
    const payload = btoa(
      JSON.stringify({
        sub: "owner@example.test",
        userId: 1,
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    );
    localStorage.setItem("plantpal_token", `test.${payload}.test`);
    localStorage.setItem(
      "plantpal_user",
      JSON.stringify({ id: 1, firstName: "Alex", email: "owner@example.test" }),
    );
    localStorage.setItem("plantpal_notifications_prompted", "true");
  });
  let person = { ...grower };
  let plant = {
    id: 7,
    nickname: "Office fern",
    commonName: "Boston fern",
    species: "",
    location: "Desk",
    notes: "",
    status: "ACTIVE",
  };
  const models = catalog.map((id) => ({
    id,
    capability: id.split(":")[0],
    model: id.split(":")[1],
    visible: !id.includes("GITHUB"),
    configured: true,
    version: 0,
  }));
  const paged = (content: unknown[]) => ({
    content,
    totalElements: content.length,
    totalPages: 1,
    number: 0,
  });
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    let data: unknown = {};
    if (path.endsWith("/users/me/access"))
      data = { userId: 1, name: "Alex", administrator };
    else if (path.endsWith("/auth/session"))
      data = { active: true, enforcementActive: false };
    else if (path.endsWith("/plants/7/restore")) {
      plant.status = "ACTIVE";
      data = plant;
    } else if (path.endsWith("/plants/7")) {
      if (request.method() === "DELETE") plant.status = "ARCHIVED";
      else plant = { ...plant, ...request.postDataJSON() };
      data = plant;
    } else if (path.endsWith("/admin/users/2/plants"))
      data = paged(
        plant.status === url.searchParams.get("status") ? [plant] : [],
      );
    else if (path.endsWith("/admin/overview"))
      data = {
        totals: {
          users: 1284,
          activeUsers: 1196,
          newUsers: 86,
          plants: 4862,
          species: 342,
          treatments: 128,
          scans: 2318,
          failedScans: 12,
          pendingScans: 3,
          overdue: 24,
        },
        refreshedAt: "2026-09-21T10:42:00Z",
        scans: Array.from({ length: 14 }, (_, index) => ({
          date: `2026-09-${String(index + 8).padStart(2, "0")}`,
          completed: [35, 48, 31, 62, 55, 70, 46, 59, 68, 44, 73, 86, 67, 80][
            index
          ],
          failed: index % 4,
          pending: index === 13 ? 3 : 0,
        })),
      };
    else if (path.endsWith("/admin/users")) {
      const all = names.map(([firstName, lastName], index) => ({
        ...person,
        id: index + 2,
        firstName,
        lastName,
        email: `${firstName.toLowerCase()}@example.test`,
        role: index === 3 ? "ADMIN" : "USER",
        status: index === 4 ? "SUSPENDED" : "ACTIVE",
        businessTier: index % 3 === 0,
      }));
      data = paged(
        all.filter((u) =>
          `${u.firstName} ${u.lastName} ${u.email}`
            .toLowerCase()
            .includes((url.searchParams.get("query") ?? "").toLowerCase()),
        ),
      );
    } else if (path.endsWith("/admin/users/2")) {
      if (request.method() === "PUT")
        person = {
          ...person,
          ...request.postDataJSON(),
          version: person.version + 1,
        };
      data = person;
    } else if (path.endsWith("/admin/models")) data = paged(models);
    else if (path.includes("/admin/models/")) {
      const model = models.find(
        (m) => m.id === decodeURIComponent(path.split("/").pop()!),
      )!;
      Object.assign(model, request.postDataJSON(), {
        version: model.version + 1,
      });
      data = model;
    } else if (path.endsWith("/admin/activity"))
      data = paged([
        {
          id: 3,
          actorId: 1,
          action: "Model shown in Settings",
          target: "VISION:ANTHROPIC_CLAUDE",
          createdAt: "2026-09-21T10:35:00Z",
        },
        {
          id: 2,
          actorId: 1,
          action: "User updated: role USER → ADMIN, status ACTIVE → ACTIVE",
          target: "user:5",
          createdAt: "2026-09-21T09:20:00Z",
        },
        {
          id: 1,
          actorId: 1,
          action: "Initial administrator provisioned by server configuration",
          target: "user:1",
          createdAt: "2026-09-20T18:00:00Z",
        },
      ]);
    await route.fulfill({ json: { success: true, data } });
  });
}

test("overview, navigation and responsive layout", async ({
  page,
}, testInfo) => {
  await setup(page);
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page.goto("/admin");
  await expect(
    page.getByRole("heading", { name: "Growing better, together." }),
  ).toBeVisible();
  await expect(page.getByText("1,284", { exact: true })).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("admin-overview-desktop.png"),
    fullPage: true,
  });
  await page
    .getByRole("link", { name: "People", exact: false })
    .first()
    .click();
  await expect(
    page.getByRole("heading", { name: "The people behind the plants." }),
  ).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("admin-people-desktop.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/admin");
  await expect(
    page.getByRole("heading", { name: "Growing better, together." }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: testInfo.outputPath("admin-overview-mobile.png"),
    fullPage: true,
  });
});

test("search, review and persist account access changes", async ({ page }) => {
  await setup(page);
  await page.goto("/admin/users");
  await page
    .getByRole("textbox", { name: "Search by name or email" })
    .fill("Sofia");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await expect(page.getByText("Oliver Chen")).toHaveCount(0);
  await page.getByRole("link", { name: "Manage Sofia" }).click();
  await page
    .getByLabel("Account status", { exact: true })
    .selectOption("SUSPENDED");
  await page.getByRole("button", { name: "Save changes", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Confirm access changes" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Confirm changes" }).click();
  await expect(page.getByRole("status")).toContainText("Changes saved");
  await page.reload();
  await expect(page.getByLabel("Account status", { exact: true })).toHaveValue(
    "SUSPENDED",
  );
});

test("model visibility persists and AI studio is responsive", async ({
  page,
}, testInfo) => {
  await setup(page);
  await page.goto("/admin/ai");
  await expect(
    page.getByRole("heading", { name: "Vision models", exact: true }),
  ).toBeVisible();
  const toggle = page.getByRole("checkbox", {
    name: "Offer PlantNet for vision",
  });
  await toggle.uncheck();
  const card = page.locator("article").filter({ has: toggle });
  await card.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText(
    "PlantNet is now hidden",
  );
  await page.reload();
  await expect(toggle).not.toBeChecked();
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page.screenshot({
    path: testInfo.outputPath("admin-ai-desktop.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

test("ordinary users cannot open the administration area", async ({ page }) => {
  await setup(page, false);
  await page.goto("/admin");
  await expect(page).toHaveURL(/\/home$/);
  await expect(page.locator(".console")).toHaveCount(0);
});

test("failed overview shows a retry and recovers", async ({ page }) => {
  await setup(page);
  let fail = true;
  await page.route("**/api/v1/admin/overview", async (route) => {
    if (fail)
      await route.fulfill({
        status: 503,
        json: { message: "Database temporarily unavailable." },
      });
    else await route.fallback();
  });
  await page.goto("/admin");
  await expect(page.getByRole("alert")).toContainText(
    "Database temporarily unavailable",
  );
  fail = false;
  await page.getByRole("button", { name: "Try again" }).click();
  await expect(
    page.getByRole("heading", { name: "Growing better, together." }),
  ).toBeVisible();
});

test("administration pages meet accessibility checks", async ({ page }) => {
  await setup(page);
  const violations: unknown[] = [];
  for (const path of [
    "/admin",
    "/admin/users",
    "/admin/users/2",
    "/admin/ai",
    "/admin/activity",
  ]) {
    await page.goto(path);
    await expect(page.locator(".loading")).toHaveCount(0);
    await expect(page.locator(".console")).toBeVisible();
    const result = await new AxeBuilder({ page }).include(".console").analyze();
    violations.push(
      ...result.violations.map((v) => ({
        path,
        id: v.id,
        nodes: v.nodes.map((n) => ({
          target: n.target,
          summary: n.failureSummary,
        })),
      })),
    );
  }
  expect(violations).toEqual([]);
});

test("manage allowances, delete and restore an account", async ({ page }) => {
  await setup(page);
  await page.goto("/admin/users/2");
  await page.getByRole("spinbutton", { name: "Maximum plants" }).fill("5");
  await page.getByRole("spinbutton", { name: "Scans per day" }).fill("3");
  await page.getByRole("button", { name: "Save changes", exact: true }).click();
  await page.reload();
  await expect(
    page.getByRole("spinbutton", { name: "Maximum plants" }),
  ).toHaveValue("5");
  await page
    .getByRole("button", { name: "Delete account", exact: true })
    .click();
  await page.getByRole("button", { name: "Confirm changes" }).click();
  await expect(
    page.getByRole("button", { name: "Restore account" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Restore account" }).click();
  await page.getByRole("button", { name: "Confirm changes" }).click();
  await expect(
    page.getByRole("button", { name: "Disable account" }),
  ).toBeVisible();
});

test("edit, archive and restore a grower's plant", async ({
  page,
}, testInfo) => {
  await setup(page);
  await page.goto("/admin/users/2");
  await page.getByRole("button", { name: "Edit", exact: true }).click();
  await page
    .getByRole("textbox", { name: "Nickname", exact: true })
    .fill("Living room fern");
  await page.getByRole("button", { name: "Save plant", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Living room fern", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Archive", exact: true }).click();
  await page.getByRole("button", { name: "Confirm archive" }).click();
  await page.getByLabel("Show plants").selectOption("ARCHIVED");
  await page.getByRole("button", { name: "Restore", exact: true }).click();
  await page.getByRole("button", { name: "Confirm restore" }).click();
  await page.getByLabel("Show plants").selectOption("ACTIVE");
  await expect(
    page.getByRole("heading", { name: "Living room fern", exact: true }),
  ).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("admin-grower-garden.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});
