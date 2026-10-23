# Requirements reconciliation (PLA-64)

Documentation-only record. No application code was changed, no production system was read,
and no YouTrack state was changed by this task.

- **Dev revision compared:** `714580c5fb8f57a521e4721dc0cb78be06951d58` (head of `origin/dev` when the
  work started; the epic branch for PLA-53 was cut from the same line).
- **Observation date:** 2026-10-24.
- **Sources:** code and tests at the revision above; `README.md`; `docs/admin-console.md`;
  `docs/dev-delivery-evidence/candidate-e3bcd1f.md` (+ receipts); `docs/auth-hardening/candidate-6981a45.md`
  and `docs/auth-hardening/evidence/mission-9b774285-historical-partial-fulfillment.md`;
  read-only dev reads listed below.

## Live dev reads (loopback `http://127.0.0.1:8184`, 2026-10-24)

| Read | Observed |
|---|---|
| `GET /actuator/info` | revision `714580c5fb8f57a521e4721dc0cb78be06951d58`, deploymentId `pla-dev-20261022152721-714580c5fb8f`, environment `dev`. Matches the revision compared. |
| `GET /actuator/health` | 200 `{"status":"UP"}` |
| `GET /api/v1/plants` (no token) | 401 |
| `GET /api/v1/admin/users`, `GET /api/v1/admin/activity` (no token) | 401. A non-admin 403 was **not** exercised (no account was created or used); that is covered only by `AdminControllerIT` in code. |

The `planotell` hostname is never used as evidence (its route check was `unavailable` in the recorded receipts).

## Issue mapping caveat

Only the dependency ids (PLA-16, 33, 34, 35, 36, 50, 69, 71) were supplied, not their text or acceptance
wording. They are **not** mapped to categories here, to avoid guessing. Rows are by category; the
issue column reads **unmapped** until the owner confirms which id belongs to which category.

## Comparison

"Stays in Backlog" applies to every row: no row cites acceptance evidence that proves its issue complete
(code presence and green CI are not acceptance evidence).

| Category | Linked issue(s) | Status | Evidence relied on | Acceptance evidence found | Acceptance evidence absent | Recommended state |
|---|---|---|---|---|---|---|
| Quota | unmapped (PLA-16/33-36/50/69/71 candidates) | partial | `backend/src/test/java/com/plantpal/admin/UsageLimitsIT.java` (independent scan/AI daily limits, concurrency cap, archive frees plant capacity); `UsageServiceImpl`; `docs/admin-console.md` (usage limits editable per user); `AuthRateLimitFilter` (rate limiting, a different concern) | Integration tests at dev revision | Issue wording unseen, so the requirement cannot be matched; no live read of a limit being enforced; no production evidence | stays in Backlog |
| Admin management | unmapped | partial | `docs/admin-console.md`; `AdminController`, `AdminPlantController`, `AdminEmailController`; `SecurityConfig.java:81` (`hasRole("ADMIN")`); `AdminControllerIT`, `MailDeliveryIT`; migration `034_admin_console.sql`; live: admin routes 401 unauthenticated | Tests and unauthenticated-401 live read | Non-admin 403 live read; admin-session walkthrough on dev; issue wording | stays in Backlog |
| Audit | unmapped | partial | `GET /admin/activity` (`AdminController.java:81`); `docs/admin-console.md` (records user changes, model visibility changes, initial admin provisioning); `AdminPlantHistoryRepository`; `README.md` activity-log mention | Code and docs | Coverage of non-admin actions (e.g. logins, data export) not established; no acceptance wording; no live authenticated read | stays in Backlog |
| Authentication | unmapped | partial | `candidate-6981a45.md` and the mission `9b774285` record: waves 2-4 are a candidate; `application.yml:126` still `enforcement-enabled: ${APP_SESSION_ENFORCEMENT_ENABLED:false}` at the dev revision; wave 5 (enforcement cutover, deployment, live verification) not performed. Live: `/api/v1/plants` 401 without token | `SessionEnforcementFilterTest` (14 cases), `ProtectedEndpointAuthTest`; those results date from `67c024f`/`6981a45`, not re-run for `714580c` | Enforcement on at a deployed revision; live session-expiry verification; any newer acceptance record | stays in Backlog |
| Delivery | unmapped | partial | `docs/dev-delivery-evidence/candidate-e3bcd1f.md` + receipts: 3 dev deployments passed, rollback recorded, at `e3bcd1f`/`35e8276` (2026-09-27); current dev serves `714580c` (live read above) so delivery works, but a fresh receipt for `714580c` is not in the repo | Receipts for the older revisions; live identity read at `714580c` | Committed receipt for `714580c`; hostname route `unavailable`; no production delivery evidence | stays in Backlog |

## Gaps

Each gap belongs to an existing issue; none is proposed as new work. Issue ids are as the owner assigns
them from the set PLA-16, 33, 34, 35, 36, 50, 69, 71 (mapping unconfirmed, see caveat).

- Quota (owner to assign id): acceptance wording not matched; no live enforcement read.
- Admin management (owner to assign id): non-admin 403 and admin walkthrough not observed on dev.
- Audit (owner to assign id): scope of recorded events versus the requirement not established.
- Authentication (owner to assign id): wave 5 outstanding; session enforcement defaults off.
- Delivery (owner to assign id): no committed receipt for the current dev revision; hostname route unverified.

## Conflicting statements noted (nothing edited)

- `README.md` says `deploy.yml` is disabled and, in places, implies no `dev` branch; the dev-delivery evidence
  and the mission record use `origin/dev`. Dated observed evidence is relied on over those statements.
- The e3bcd1f delivery evidence (2026-09-27) predates the compared revision; none of its results are carried over.

## Owner-supplied production evidence

**Not yet supplied.** The owner must provide: the live production version (revision / deployment id), and any
authorised production reads for quota, admin, audit, authentication and delivery, with date and form. Until then
every production statement is **unverified**; none is inferred here.

## Issue-state rule

Every compared issue stays in Backlog unless this table cites acceptance evidence proving it complete. None does,
so the recommended state is Backlog for all. State changes are made outside the repo.
