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
