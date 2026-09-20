# Waves 2–4 candidate checkpoint — `375dfa9`

This is a review handoff for Factory mission
`9b774285-8a9f-4763-9d27-7310127bc931`. It is not a completion, release, merge,
deployment, or enforcement claim.

## Candidate and evidence

- Candidate: `375dfa9` on `feature/PP-100-session-hardening-waves-2-4`.
- The checkpoint preserves the Wave 2 pin replacements and the removed forged-token
  characterization as immutable historical evidence in
  `evidence/wave-1-defect-pin-history.md`.
- `test-results-backend-clean-verify.log` records `mvn clean verify` at this candidate
  with `BUILD SUCCESS`: 465 unit tests and 34 integration tests, with 3 explicitly
  skipped evaluation tests. The Failsafe summary has zero failures and zero errors.
- `test-results-auth-it-docker-api-144.log` is intentionally retained as partial
  evidence of the earlier Docker-profile failure. The follow-up
  `test-results-auth-it-testcontainers-1214.log` and the full verify log are the
  passing Docker/Testcontainers evidence after the correction.
- The isolated-browser check used PlantPal at `127.0.0.1:4301`, proxied only to the
  healthy PlantPal Docker backend at `:8180` (never the platform dashboard at `:4200`).
  It identified the `PlantPal` page, registered a local acceptance user, re-logged in
  to `/garden`, and observed a real `GET /api/v1/auth/session` response of `200`.
  It used no API stubs and observed no browser console errors.

## Rollout and rollback boundary

`app.session.enforcement-enabled` remains `false`. No deployment, merge, production
configuration change, or enforcement cutover is authorized by this candidate. If a
future owner-authorized Wave 5 rollout exposes a session-registry failure, immediately
restore that flag to `false` and redeploy the prior approved image/configuration; the
inert code path preserves existing JWT behavior while the incident is investigated.

## Remaining owner/release checks

1. Obtain the separate Wave 5 enforcement and release authorization.
2. Run the owner-approved deployed-revision protected-route and inactivity journeys
   after the explicit cutover; the local browser check above is not deployed acceptance.
3. Have the origin agent review this candidate and decide whether to merge, schedule
   the cutover, or request further changes. Do not issue a fulfillment claim before
   the remaining mission criterion is independently evaluated.
