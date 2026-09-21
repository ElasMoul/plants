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

A bounded persisted catalog contains the ten non-deprecated vision/reasoning
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
