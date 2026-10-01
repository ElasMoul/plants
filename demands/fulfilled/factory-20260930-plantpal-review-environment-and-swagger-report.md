---
demandId: factory-20260930-plantpal-review-environment-and-swagger
worker: plantpal
status: done
date: 2026-09-30
shipped:
  - "tools/dev-delivery/dev_delivery.py: `review start|status|stop` (PR #209, into dev). Builds a PR head whose four required checks are green (Backend CI, Frontend CI, Detect secrets, sonar-gate) in compose project `plantpal-review` with its own volumes, generated config (review.env, review-db-password) and loopback port 8186; never touches the dev stack (plantpal-devdelivery, 8184/8185) or production"
  - "Receipt: contracts v0.38.0 tagged delivery.deployment-receipt (environment review, revisionRole task, review block, apiDocsUrl) with the running app's observed /actuator/info identity; tool repinned and schemas re-vendored from v0.38.0"
  - "Idempotent: starting a review for the revision already running returns the existing receipt; starting another tears the previous one down first (only after the new head passes the gate). `review status` / `review stop` print the launcher ReviewEnvironment document; a miss exits 4 with `review_environment_not_found: <key>`"
  - "Swagger: /swagger-ui.html, /swagger-ui/** and /v3/api-docs proxied in deploy/dev-delivery/nginx.dev-delivery.conf and frontend/nginx.conf; application-prod.yml sets springdoc api-docs and swagger-ui disabled (Caddy forwards all paths, so the backend prod profile is the control); ApiDocsExposureTest asserts it"
  - "docs/dev-delivery.md section 6 (review mode, isolation, never merges anything) and section 7 (Swagger URLs, dev and review only); CHANGELOG [Unreleased] entry"
---

# Report: review environment and dev-only Swagger

## State check
Not previously shipped: no `review` command, no review port or compose project, no Swagger routes in the nginx configs, and springdoc was enabled in every profile.

## Verified
- `tools/dev-delivery` unit suite: 88 tests pass (new `ReviewEnvironment` and `ReviewCommands` classes cover target resolution, gating, idempotency, one-at-a-time, port refusal, key conflict, receipt shape).
- `ApiDocsExposureTest` (backend): 2 tests pass; prod disables both springdoc switches, base/dev leave them enabled. `spotless:apply` made no changes.
- `nginx -t` passes on the dev-delivery conf in Docker.

## Not verified
- No live `review start --pr N` was run (needs a PR with green checks, Docker builds and port 8186). The end-to-end path is unit-tested with mocked docker/gh only.
- No full `mvn verify` was run locally; CI on PR #209 is the gate.
- `frontend/nginx.conf` was edited identically but not syntax-checked on its own.

## Decisions
- Port 8186: next free 81xx after 8184/8185. It needs registering by platform-vault; I did not edit the vault.
- sonar-gate is a required check, so a branch-only review without an open PR into dev is refused. `main` and `dev` branches are never reviewed.
- The tool overlays its own `deploy/dev-delivery/*` onto the PR tree, so older PRs still get the review wiring.
- `review status` reports the last recorded observation, not a fresh probe (documented).
- The `staging` profile is left unchanged (still exposes springdoc); noted in the docs.
- Dev-only receipt lookups (`last_passed`, rollback, operation-key lookup) now ignore review receipts.

## Open items
- platform-vault: register port 8186 (demand to be raised by the owner).
- Launcher: consume `review start|status|stop`.
- Factory: record evidence from the receipt.
- Run one live review once PR #209's checks are green.
