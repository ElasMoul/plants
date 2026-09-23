# Administration architecture notes

## Roles and authentication

`users.role` defaults to USER. Public registration cannot set it. The security chain
requires ADMIN for `/api/v1/admin/**`, and the JWT filter checks current account
status and database authorities on every request. JSON security responses preserve
401 versus 403 without an error-dispatch redirect. Initial admin provisioning is
opt-in, accepts an existing active account only, and records an audit event.

## Account mutations

AdminService uses a transaction, a PostgreSQL transaction-scoped advisory lock,
fresh actor validation, and a locked target row. Self-demotion/suspension is denied.
This avoids concurrent cross-demotion removing all active administrators. User
versions reject stale edits and also prevent stale preference writes overwriting
account changes. Audit inserts participate in the mutation transaction.

## Model visibility

A bounded persisted catalog contains the twelve non-deprecated vision/reasoning
choices. ModelCatalogService owns visibility separately from provider configuration.
Preferences expose visibility maps consumed by both frontends. Hidden current
choices remain readable but disabled, and new selections are rejected server-side.
Existing routing/defaults are preserved: hiding is not a runtime kill switch.
Optimistic versions protect catalog edits. Never store provider credentials here.

## Reporting and UI

AdminRepository uses parameterized JDBC for audit writes and bounded history reads,
plus scalar domain aggregates and a fixed UTC 14-day series. No user lists are
loaded to calculate metrics. Lists use pages capped at 100 and stable sorting.
Classic lazy-loads the standalone administration UI under `/admin`; the main app
shell is replaced by an administration sidebar on those routes. Local SVG icons
avoid a dependency on externally hosted icon fonts. CSS is split by shell,
overview, people, models, and responsive rules, keeping existing error budgets.

Bootstrap/access guide and exact API semantics: `docs/admin-console.md`.


## Usage admission and native DeepSeek

UsageService is a leaf dependency on JDBC, avoiding UserService/PlantService cycles.
Plant insertion/restoration locks the user's row inside the caller transaction before
counting active plants. AiUsageAspect reserves one action in a separate transaction
before user-triggered AI work; daily rows keyed by user/date persist across instances.
Scans consume both scan and AI allowance. Failed admitted attempts count; background
steps do not count separately. Default ceilings are 100 plants, 20 scans/day, 100 AI
attempts/day. Zero blocks new usage. Lowering ceilings never deletes user data.

AdminPlantService delegates to PlantService so cache eviction/reminder behavior stays
shared with user actions. Lists are bounded and plant mutations require owner+plant IDs.
Archive/restore preserves history; restoration leaves reminders disabled. User ARCHIVED
is recoverable and blocked by the JWT filter; scheduled reminders require ACTIVE users.

DeepSeekDirectClient is separate from legacy Azure-backed DeepSeekClient. Optional
DEEPSEEK_HOSTED_API_KEY controls configured availability; DEEPSEEK_DIRECT_MODEL defaults to
native `deepseek-flash`. Both preference enums include DEEPSEEK_FLASH; legacy species
preference mapping preserves it for enrichment. Chat honors the reasoning selection.
HTTP calls are bounded and do not log keys or raw provider failures. Native calls stay
inside PlantPal's standalone domain flow. No Platform-facing contracts changed.


## Hosted DeepSeek format and plant viewer (2026-09-23)

DeepSeekDirectClient now reads DEEPSEEK_HOSTED_API_KEY and DEEPSEEK_HOSTED_BASE_URL
(default https://api.deepseek.com/anthropic); POST /v1/messages uses x-api-key,
Anthropic system/content blocks, base64 image source blocks, end_turn completion
validation and text-block extraction. Legacy GitHub DeepSeekClient is unaffected.
A live small text request succeeded; transport/error cases use a local mock HTTP server.

AdminPlantHistoryRepository combines bounded care, scan, treatment, admin audit and
plant-created records. AdminPlantService first checks plant+owner (including archived).
Treatment rows summarize current state with completed/start/creation time, rather than
pretending to have a full transition audit. Material Dialog owns accessible focus and
keyboard behavior; standalone modal loads independent paginated history and handles
errors/retries without replacing the account page.
