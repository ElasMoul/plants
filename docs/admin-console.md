# PlantPal administration

The administration console is part of the Classic Angular app at **`/admin`**.
It uses the real PlantPal database. The personal garden dashboard remains at `/home`.
The account menu shows **Administration** only to administrators.

## First administrator

1. Start the backend normally so Liquibase applies migration `034_admin_console.sql`.
   Existing and newly registered accounts receive the `USER` role.
2. Register your account normally, if it does not exist yet.
3. Set **`APP_ADMIN_BOOTSTRAP_EMAIL`** to that existing account's exact email in the
   backend process environment, then restart the backend. Alternatively, pass the
   Spring property `--app.admin.bootstrap-email=your-existing-email` at startup.
4. Remove the bootstrap setting after the successful restart. It is intended for
   initial provisioning, not permanent role enforcement. An unknown or inactive
   account causes startup to fail with an explanatory message.
5. Sign in to Classic: administrators land directly at `/admin`. The account menu
   also offers **Administration**. Roles are read from the database on each authenticated request,
   so an existing valid session also sees the promotion on its next navigation.

Set the variable in the **backend process/container**, not just the frontend or
an unreferenced Compose `.env` file. No default administrator or default password
is created, and registration cannot grant administrator privileges.
The existing Compose service reads `backend/.env`; add the value there and recreate
the backend container to apply it. Native runs need the variable exported in the
shell that starts Java. A blank entry is included in `backend/.env.example`.

## Pages

| Route | Purpose |
|---|---|
| `/admin` | Live community, plant, species, treatment, and scan totals; overdue reminders; 14 UTC calendar days of scan activity; recent administrative changes |
| `/admin/users` | Paginated name/email search and account-status filtering |
| `/admin/users/:id` | Edit profile, membership, status, role and usage limits; inspect AI preferences and manage the user’s plants |
| `/admin/ai` | Independently show/hide each vision and reasoning model in Settings |
| `/admin/activity` | Paginated record of user changes, model visibility changes, and initial administrator provisioning |

User role/status changes have an explicit review step before saving. Administrators
cannot suspend or demote themselves. Permission changes serialize across backend
instances and re-check the acting account, preventing two administrators from
concurrently removing each other's access. User and model versions reject stale
edits. Audit records commit in the same transaction as the change.

Archived, suspended and inactive accounts are rejected on the next authenticated request,
even with an already-issued JWT. Reactivating an account allows any otherwise-valid
session to work again; suspension is not permanent token revocation. A request that
already passed authentication may finish. Deletion is a recoverable `ARCHIVED` state; account, plant and scan history is retained.
Reminder delivery excludes inactive accounts. Email/password changes are deliberately outside this console.

## AI visibility semantics

Visibility controls **new selections in Settings**, independently of provider
configuration. Both Classic and Atlas consume `visionModelVisibility` and
`reasoningModelVisibility` from `/api/v1/users/me/preferences`. Classic now fetches
preferences on each read instead of trusting its session cache.

An unselected hidden model disappears. A hidden model that is already selected
remains visible as a disabled current choice, preserving its name and the user's
existing setting. The backend rejects a new selection of a hidden or unknown model.
Previously selected models continue to run. Visibility is **not an AI kill switch**,
does not change registration defaults, and does not migrate existing preferences.
Existing care plans and scans are unaffected. Users already on an open Settings page
receive changes when they reopen/refresh it; stale writes are still validated by the server.

The catalog contains twelve non-deprecated model/capability combinations, including
DeepSeek Flash for vision and reasoning. Migrations 034–035 seed these choices. It excludes the legacy
`OLLAMA_LLAVA` alias. Claude and native DeepSeek optional API-key configuration is shown separately;
other provider availability follows PlantPal's existing configuration assumptions.
The console does not probe providers, edit credentials, or claim that a visible
provider is healthy. Check any retired GitHub-hosted routes before offering them.

## Data and API

Every `/api/v1/admin/**` endpoint requires `ROLE_ADMIN`. The authenticated
`GET /api/v1/users/me/access` endpoint reports only the caller's own access.
Administrative list endpoints return `ApiResponse<Page<...>>`, cap page size at 100,
and apply deterministic server-controlled sorting. User searches use bound parameters.

Migration 034 adds `users.role`, `users.version`, audited `ai_model_settings` with
optimistic versions, and append-only `admin_activity` records. The JDBC reporting
repository returns aggregate counts and a fixed 14-day series, not user records.
Database server time defines rolling 30-day counts; scan-chart calendar boundaries
are UTC. The success percentage uses only completed and failed scans in the chart's
14-day range, excluding pending scans. “Active users” means accounts with ACTIVE
status, not recent sign-ins. All figures are refreshed on demand; no synthetic
trends, revenue, token usage, or provider uptime are shown.

## Verification

- Backend: `mvn verify` (unit tests, Docker-backed integration tests, Checkstyle,
  Spotless, and the existing JaCoCo gate).
- Both frontend production builds: `npm run build:prod`, `npm run build:atlas:prod`.
- Frontend unit suite: `npm test -- --runInBand`.
- Browser checks: start Classic, then run
  `npx playwright test e2e/journeys/admin.spec.ts --project=chromium --workers=1`.
  Override `PLAYWRIGHT_BASE_URL` if using another local port.

The new browser suite uses explicit API fixtures for repeatable screenshots and
tests overview navigation, mobile overflow, searchable users, confirmation and
persistence of account edits, AI visibility persistence, denial/recovery states,
and axe accessibility checks on all five routes. Backend integration tests exercise
actual authentication, authorization, migration/query execution, pagination,
account suspension/reactivation, stale writes, visibility persistence, and auditing
against isolated PostgreSQL/Redis containers. Screenshot numbers are fixture data;
the shipped application always reads the server.


## Account and garden management (migration 035)

The account page offers Disable, Enable, Delete and Restore with a confirmation step.
Delete archives the account and immediately blocks subsequent authenticated requests.
Self-disable, self-delete and self-demotion are rejected. Plant management is scoped
by both user ID and plant ID: list active/archived plants, edit nickname/common name/
location/notes, archive, and restore. Archiving disables the plant's reminders; restoring
does not re-enable them automatically. The existing PlantService preserves caches,
audit fields and plant-count events. All administrative plant changes are audited.

Per-user defaults are **100 active plants**, **20 scan attempts/day**, and **100 AI
action attempts/day**. Administrators can change each to an integer from 0–1,000,000;
0 blocks new usage. Lowering a limit preserves existing records. Creating a plant
manually, saving a scan as a plant and restoring an archived plant all share the same
capacity check. A user-row database lock serializes competing creations.

Daily counters reset by UTC calendar date, persist across server restarts, and are
shared by all backend instances. Each scan/retry costs one scan and one AI action.
Chat (buffered/streaming), cure advice, treatment creation/plan generation/description
regeneration, care-card additions, species confirmation and species regeneration
cost one AI action. These are **action-attempt limits, not tokens or currency**;
background stages belong to their triggering action. Attempts admitted to the service
count even if later validation, provider or queue work fails; quota-rejected attempts
do not increment counters. Existing hourly abuse limits continue to apply.

`UsageService.consumeAi` uses an independent transaction before the action transaction,
locks the user, and atomically checks/increments the UTC usage row. `AiUsageAspect`
marks user-triggered entry points; background provider calls are not charged again.
Account status and configured limits are checked on every admission. Full quotas
return 429 (AI) or 409 (plants). Role information in login responses drives navigation;
server-side authorization remains authoritative. Deferred chat responses re-authenticate
on async dispatch so a quota error does not become an erroneous sign-out.

## Native DeepSeek Flash

Add `DEEPSEEK_API_KEY=your-key` to `backend/.env`, export it for native launches (the
local preview launcher reads this file), and restart/recreate the backend. No key goes
to the browser, database catalog, or Git. `DEEPSEEK_DIRECT_MODEL` optionally overrides
the default **`deepseek-flash`**, currently DeepSeek V4.1 Flash. This is independent of
the legacy GitHub-hosted `DEEPSEEK_R1` and its `DEEPSEEK_MODEL` variable.

The native client uses `https://api.deepseek.com/chat/completions`, Bearer authentication,
8,192 maximum output tokens and thinking disabled for bounded structured-output calls.
It supports plant identification, image annotation, cure advice, disease descriptions,
species enrichment and chat. Select DeepSeek independently in Vision and Reasoning
Settings; chat uses the reasoning selection. Chat uses a buffered response, including
through the SSE endpoint. Native requests go directly to DeepSeek in standalone
PlantPal and are not dependent on the Platform gateway. Missing keys disable selection;
visibility remains an independent admin choice. Existing selections are not migrated.

The adapter rejects empty/truncated output and translates provider failures without
exposing credentials or upstream response bodies. Availability means a nonblank key is
configured, not that billing or provider health has been verified. Request shape and
failure behavior were tested with a local HTTP server; no paid live DeepSeek call was made.

Official references: [vision](https://api-docs.deepseek.com/guides/vision/) and
[models](https://api-docs.deepseek.com/quick_start/pricing/).

## Local preview handoff — 2026-09-22

The existing local administrator account (ID 3, formerly `a@a.a`) now signs in as
`admin@plantpal.com`, with its password and garden preserved. The change was recorded
in admin activity. This is local database provisioning, not a role grant for everyone
who registers that email on another installation. Use the bootstrap process above on
other installations. The old email no longer signs in; old JWTs require a fresh login.

Preview: `http://127.0.0.1:4210/login`; updated native backend: `127.0.0.1:8190`.
Migration 035 is applied locally. Docker services on their existing ports were preserved.

Verification: 477 backend unit tests passed; 45 integration cases completed with 3
existing skips and no failures; coverage, Checkstyle and Spotless passed. Both frontend
production builds passed; 555 Jest tests and 8 Chromium administration journeys passed,
including desktop/mobile and axe checks. Existing non-fatal build warnings remain.
The garden screenshot in `screenshots/admin-grower-garden.png` uses test fixtures.
