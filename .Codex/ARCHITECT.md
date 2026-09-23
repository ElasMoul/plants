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


## User-interface localization (2026-09-23)

Classic uses shared/i18n/fr.ts with English source messages as stable lookup keys,
a standalone t pipe, and translate() for application-owned TypeScript copy.
Numbered placeholders keep values outside translated text; Angular escapes rendered
values. Never translate protocol enum comparisons, identifiers, user text, or AI
content. Species health-summary presentation handles the existing server string
without changing filtering comparisons. Tests verify catalog placeholder parity.

plantpal.language is a per-origin browser preference (en/fr), not a database field.
Selection reloads the current route to reinitialize LOCALE_ID, MAT_DATE_LOCALE,
Material labels and module-level translated constants. Unsaved form state is not
preserved; settings explains the reload. No language-based routes or extra packages.
English is the fallback for unknown language/message and unavailable localStorage.
French locale data is registered; Material hint/error sizing is dynamic to prevent
long French hints overlapping following fields. Language metadata includes direction;
Arabic still needs its catalog, plural categories beyond simple suffixes, and full
RTL layout/accessibility QA. Atlas is an independent later integration.

## French AI presentation translations (2026-09-23)

Keep canonical domain DTOs and saved analyses unchanged. TranslationResponseAdvice
runs after authorized identification/species/treatment/reminder controllers and adds
localization metadata for X-Content-Language: fr. TranslationTextExtractor allowlists
prose and excludes user notes, names, enums and IDs. Angular waits for translation
jobs, then applies aiText/aiDiagram at display time; never translate action payloads.

TranslationService (interface/impl) uses ai_translations (036), keyed by a fingerprint
of sorted source prose, owner/shared species scope, language and prompt version.
Only public species detail/regenerate-description routes use shared scope. Source
changes create a new cache version. Atomic INSERT ON CONFLICT claims one worker on
the bounded AI executor; attempt/status fencing prevents old retries overwriting.
Pending jobs older than ten minutes fail on read; explicit retry has a ten-second
cooldown. Authenticated GET/POST retry enforce owner or public-species visibility.

TranslationClient uses native hosted DeepSeek, then Anthropic/Ollama. Each batch
consumes AI usage and per-user translation rate limit; cache reads are free. Bounded
batches and numeric/unit validation prevent unsafe structural output being saved.
Do not regenerate diagnoses merely to change display language. Chat instead carries
language on each request into the shared system prompt. Current target is explicitly
French; adding Arabic requires language-aware cache/schema/prompt changes and RTL UI.
