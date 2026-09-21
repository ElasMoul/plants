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
