# PlantPal feature state

## 2026-09-21 — Administration console

Owner-requested PlantPal feature work, independent of Platform integration.
Branch: `codex/plantpal-admin-dashboard`, based on `dev`.
Implementation commit: `44d1664`.

Completed: responsive Classic `/admin` console with overview, People directory,
account editor, AI studio, and activity history. Real aggregate reporting,
paginated search, user status/role/membership changes, persisted model visibility,
server-side administrator authorization, version conflict protection, audit trail,
and explicit initial-admin provisioning. Both Classic and Atlas Settings honor
model visibility. Migration 034 adds roles, user versions, catalog, and activity.

Validation:
- Full backend `mvn verify` passed, including PostgreSQL/Redis integration tests,
  formatting, Checkstyle, and the existing JaCoCo gate. Existing integration suite
  has three skips; all five new administration HTTP tests passed.
- Final administrator-bootstrap/model/user unit tests and Spotless check passed.
- Classic and Atlas production builds passed. Existing CommonJS warnings and
  non-fatal component-style budget warnings remain; error budgets passed.
- Full frontend Jest suite: 552 tests passed; two additional Settings policy tests
  were then added and passed with their 29-test focused suite.
- Six Chromium browser tests passed: desktop/mobile, users, AI visibility,
  authorization, retry, and axe checks on all five administration routes.
- ESLint on the changed Classic components/routes passed; git diff check passed.

Initial administrator access has NOT been applied to a real account. Set
APP_ADMIN_BOOTSTRAP_EMAIL to an existing active account for one backend startup,
then remove it. Follow `docs/admin-console.md`. No production deployment or push
was performed. Browser screenshots use test fixtures; shipped UI uses real APIs.

The legacy `.claude/` histories remain untouched. This directory records the
current feature handoff requested by the root AGENTS.md instructions.


## 2026-09-22 — administration expansion complete

Implementation commit: `5132689`, still on `codex/plantpal-admin-dashboard`.
Added recoverable user deletion, explicit disable/enable/restore, per-user active-plant,
UTC daily-scan and daily-AI allowances, persisted atomic counters, and admin garden
editing/archive/restore. Migration 035 applied to the local dev database. Login now
routes administrators directly to /admin. Reminder delivery excludes inactive users.
Native DeepSeek Flash supports vision/annotation, reasoning, species and chat;
DEEPSEEK_API_KEY from backend/.env is optional, DEEPSEEK_DIRECT_MODEL defaults to
`deepseek-flash`. Model visibility and configured-key availability remain independent.
No live paid provider call was made. See docs/admin-console.md for precise accounting.

Validation: complete Maven verify passed: 477 unit tests; 45 integration cases, three
pre-existing skips, no failures; coverage/Checkstyle/Spotless passed. Classic and Atlas
production builds passed. 555 Jest tests and eight Chromium admin journeys passed,
including mobile overflow and accessibility. ESLint changed supported files: no errors;
shared-core files are outside the existing lint configuration. UI formatted with Prettier.

Local administrator ID 3 was renamed from a@a.a to admin@plantpal.com as the default
interpretation of the owner's request, preserving password/data; audit record saved.
No separate account or new password was created. Administrator identity is stored in
this local DB only, not auto-granted at public signup. Existing old-email tokens must
log in again. Preview is running on 4210 (frontend) and 8190 (backend); health UP and
SPA HTTP 200 checked. Runtime logs/pids/proxy are in ignored backend/target/admin-preview.
No push, merge, or production deployment. Remaining owner action: set DeepSeek key and
restart the backend to use that provider.


## 2026-09-23 — hosted DeepSeek and plant viewer complete

Code commit a4ef7e5. Native provider uses DEEPSEEK_HOSTED_API_KEY plus
DEEPSEEK_HOSTED_BASE_URL=https://api.deepseek.com/anthropic, POST /v1/messages,
Anthropic payload/response format. User's ignored .env already contained the key;
tracked .env.example key is blank. Other owner edits to the example were preserved.
Small live DeepSeek test succeeded (HTTP 200, deepseek-flash, end_turn, pothos tip).
No credentials printed or stored in logs/source.

People account plants now offer View plant: accessible responsive photo/details modal,
paginated care/scans/treatment/admin history, completed status, expandable details,
archived support, missing-photo fallback, retry, Escape and focus restoration.
API validates owner and ADMIN role; history lists capped at 50. No migration.

Validation: 33 focused provider/model/user/chat unit tests passed, then four adapter
and eight real-database admin tests passed; production Classic build and admin lint
passed; nine Chromium admin journeys passed, with an additional successful modal
visual run. Live preview frontend 4210 and backend 8190 healthy (UP, SPA 200).
Backend process last started PID 9028; logs/pids are in target/admin-preview. No push.


## 2026-09-23 — French user interface complete

Created codex/plantpal-french-localization from clean dev after the owner merged admin.
Implementation commit: e1932b1. Standard Angular user app now offers English/Français
in the header (including before login) and settings. Translation catalog covers
landing/auth, home/garden/species, plants, identification, reminders/treatments,
chat controls, model settings, notifications, validation, accessibility labels and
voice-test interface. No backend or database changes; no admin page translation.

Preference is local to this browser origin (plantpal.language), defaults to English,
and persists through login/logout. Selecting a language reloads the current page
so Angular locale providers and eagerly constructed labels initialize consistently.
French calendar/date/pagination/speech locales, document lang/dir, and plural rules
are configured. Form hints grow to accommodate longer French text. Existing AI
output, species descriptions, user notes/names, and unknown server errors retain
their source language; UI language does not change AI prompts or PlantNet preferences.
Atlas and Arabic remain future rollouts. Direction metadata admits rtl, but Arabic
translation/plurals/layout validation are not implemented.

Validation: production build passed (existing size/CommonJS warnings); 562 unit
tests in 46 suites passed; touched TypeScript and new localization files passed
ESLint; 12 Chromium journeys passed (3 French, 9 admin). Screenshot:
docs/screenshots/french-plant-form.png. Calendar test selects a date before capturing
the mobile form; native Escape timing during opening animation was not a reliable
screenshot setup. Angular template audit found no untranslated static user text
except the technical isSecureContext diagnostic field name.

Preview processes were restarted after stopping between sessions: frontend 4210,
backend 8190, logs/pids/proxy in backend/target/admin-preview. No push or merge.

## 2026-09-23 — French AI content complete

Implementation f5f913d on codex/plantpal-french-localization. Adds async persisted
French presentation translations for existing/new identification, species, care and
treatment prose; original domain records remain immutable. Native hosted DeepSeek
is preferred, then configured Anthropic/Ollama. Translation batches consume AI quota,
not scans; cached versions are reused. Chat requests carry en/fr into the shared
system prompt for both streaming and buffered paths. Migration 036 applied locally.

Private translations are owner-scoped; only public species detail prose is shared.
Atomic cache claim, attempt fencing, ten-minute stale recovery, explicit retry with
cooldown, bounded text/batches, and numeric/unit validation are covered. Frontend
waits for jobs with progress/failure notices and display-only pipes. Diagram labels
and read-aloud use translations. Fixed species direct-navigation service providers.

Validation: 483 backend units, four translation integration tests, 566 frontend
units, 14 Chromium journeys passed. Final production build and touched-TS lint
passed; existing bundle/CommonJS warnings remain. Hosted DeepSeek synthetic-text
check returned French and preserved Monstera deliciosa, 5 ml, 1 L and 7. Automatic
approval review blocked exporting an existing saved identification for a live test;
no such export occurred. Database pipeline was tested with mocked provider output.
Preview remains frontend 4210/backend 8190. No push or merge; Arabic awaits owner testing.

## 2026-09-23 — Arabic and compact selector ready for owner testing

Implementation 816783c on codex/plantpal-french-localization. Selector displays
EN / FR / AR without the previous symbol. Arabic catalog has 586 entries and
placeholder/key parity with French. Uses Modern Standard Arabic, ar-MA locale,
RTL document, logical layout properties, mirrored navigation arrows, LTR technical
inputs, and Arabic counted forms (zero/one/two/few/many/other). Existing user text
is preserved. Screenshot reviewed: docs/screenshots/arabic-plant-form.png.

AI translation jobs now carry target language throughout scheduling/polling/retry;
cache fingerprints separate French and Arabic. Migration 037 widens the language
constraint while preserving existing French jobs. Arabic chat prompts shared by
streaming and buffered paths. Arabic pending/failure/retry notices included.

Validation: 567 frontend unit tests, 485 backend units, five database translation
integration tests, seven language Chromium journeys and nine admin journeys passed.
Production frontend/backend builds and localization lint passed; existing bundle
size/CommonJS warnings remain. UI catalog generated using hosted DeepSeek with only
static application labels, reviewed and corrected; no saved user content exported.
Preview restarted to include new catalog/backend; migration 037 applied, backend
8190 UP, frontend 4210 available. No push/merge. Owner explicitly wants testing
before merge to dev; await that verdict.

## 2026-09-23 — PR #159 formatting and checkout recovery

Commit bdb92a4 fixes the five Java files rejected by CI Spotless. Ran unfiltered
mvn spotless:apply spotless:check and full mvn verify -Ddependency.check.skip=true:
unit/integration tests, coverage and formatting all passed. Do not treat a filtered
formatter invocation or package/test as proof that CI verify passed.

Checkout was blocked by untracked feature files left on older local main. Preserved
all untracked files in stash named codex-preserve-main-files-before-pr159-format-fix,
then switched to the PR branch. Preserved main's three unique local commits on
codex/main-before-sync-20260923 before aligning inactive main with fetched origin/main.
No files or commits discarded; do not blindly pop the stash over tracked feature files.
PR: https://github.com/ElasMoul/plants/pull/159. Merge still awaits owner approval.
