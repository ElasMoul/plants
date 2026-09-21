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
5. Sign in to Classic and open **Administration** from the account menu, or visit
   `/admin` directly. Roles are read from the database on each authenticated request,
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
| `/admin/users/:id` | Edit first/last name, personal/business membership, account status, and role; inspect current AI preferences |
| `/admin/ai` | Independently show/hide each vision and reasoning model in Settings |
| `/admin/activity` | Paginated record of user changes, model visibility changes, and initial administrator provisioning |

User role/status changes have an explicit review step before saving. Administrators
cannot suspend or demote themselves. Permission changes serialize across backend
instances and re-check the acting account, preventing two administrators from
concurrently removing each other's access. User and model versions reject stale
edits. Audit records commit in the same transaction as the change.

Suspended and inactive accounts are rejected on the next authenticated request,
even with an already-issued JWT. Reactivating an account allows any otherwise-valid
session to work again; suspension is not permanent token revocation. A request that
already passed authentication may finish. No accounts, plants, or scan records are
deleted by this feature. Email/password changes are deliberately outside this console.

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

The catalog contains the ten existing non-deprecated model/capability combinations.
Migration 034 preserves their current visibility. It excludes the legacy
`OLLAMA_LLAVA` alias. Claude's optional API-key configuration is shown separately;
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
