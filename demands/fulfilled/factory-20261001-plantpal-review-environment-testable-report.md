---
demandId: factory-20261001-plantpal-review-environment-testable
worker: plantpal
date: 2026-10-01
status: done
shipped:
  - "tools/dev-delivery/dev_delivery.py: `review start` now seeds the review environment through the app's own API (`review_baseline_checks`): test account review@plantpal.test / review-password-1 and one baseline plant 'Review Monty'; idempotent (login if registered, no duplicate plant)"
  - "Review backend runs with Spring profiles `dev,platform` (compose APP_SPRING_PROFILES, set by compose_env for review only) so all AI calls go through ai-gateway at REVIEW_AI_GATEWAY_URL (default http://host.docker.internal:8085; extra_hosts host-gateway added); state-feed off in review; dev stack unchanged"
  - "backend AnthropicClient.isAvailable(): true when platform.gateway.enabled, so Claude is selectable (and the account's default preference) with no Anthropic key in the app; AnthropicClientTest covers it"
  - "Receipt checks recorded for review: seed:test-account, seed:baseline-plant, smoke:identification-endpoint, smoke:claude-enabled, smoke:ai-gateway; an unreachable gateway makes the result `unknown`, never `passed`"
  - "docs/dev-delivery.md 'Ready to test' section; CHANGELOG [Unreleased] entry; 5 new tool tests (suite 106 pass)"
---

# Report: review environment starts ready to test

## State check
Not previously shipped: the review stack had no seeded account or data, placeholder AI keys (`unset-in-dev-delivery`, empty Anthropic key, so Claude was gated off), no `platform` profile (direct provider clients, no gateway), and checked only identity, frontend, health, auth guard and Swagger.

## Verified
- `python -m unittest discover -s tools/dev-delivery`: 106 pass (gh/docker/HTTP mocked).
- `mvn -o test -Dtest=AnthropicClientTest`: pass; `spotless:apply` run.

## Not done / not verified
- No live `review start` against a real PR: the seeding, preferences shape and gateway probe are unit-tested with mocked HTTP only. The `/api/v1/users/me/preferences` field names were read from the code, not observed on a running review.
- Full `mvn verify` not run; CI is the gate.
- ai-gateway's own Claude enablement and its health path are not observed: the probe is "any HTTP answer on `/` at the host port".
- Plant identification is verified reachable (endpoint answers for the test account), not exercised with a real photo through the gateway.
- The test-account password is fixed and documented (loopback-only, volumes removed on every teardown).

## Open items
- Owner: run one live review with ai-gateway up and confirm all five new checks pass.
- Changes are committed on the current branch only; not pushed (dev is ruleset-protected, land via PR).
