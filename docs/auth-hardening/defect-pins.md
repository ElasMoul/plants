# PlantPal auth defect pins

These pins freeze security-relevant current behaviour before hardening work. A pin is not an
endorsement: later units may deliberately change it, but characterization suites must make the
change visible first.

## Defect pin records

| Pin | Assignment | Surface | Current behavior | Security consequence | Characterization | Source |
|---|---|---|---|---|---|---|
| PP-AUTH-001 | authguard | classic AuthGuard | A signed-out visitor is redirected to `/login` with a safe `returnUrl`; an expired local token is not admitted. | The guard remains advisory; backend authorization is decisive. | Assert signed-out deep-link return and expired-token denial. The former forged-future-token characterization is retained as immutable Wave 1 history, not a current pin. | `frontend/src/app/core/guards/auth.guard.ts`; `docs/auth-hardening/evidence/wave-1-defect-pin-history.md` |
| PP-AUTH-002 | jwtinterceptor | classic JwtInterceptor | Every concurrent HTTP 401 independently calls `logout()` and navigates to `/login`; there is no shared transition latch. | A burst of rejected requests fans out duplicate session clearing and navigation side effects. | Flush concurrent 401 responses and assert the exact logout and navigation call counts. | `frontend/src/app/core/interceptors/jwt.interceptor.ts` |
| PP-AUTH-003 | sessionhandoff | cross-origin session handoff | The classic app places the bearer token in a URL fragment and Atlas copies it directly to localStorage without a one-time server exchange or destination-origin binding. | Anyone who obtains a valid handoff URL can replay the bearer token until it expires. | Assert the fragment contract, storage keys, one-time consumption, malformed-input scrubbing, and replayable payload shape. | `frontend/projects/shared-core/src/lib/session-handoff.ts`; `frontend/projects/atlas/src/main.ts` |

## Wave-2 disposition (mission 9b774285)

Recorded 2026-09-16. The original Wave 1 records remain frozen in their original commits and
brain-session history; this section records the deliberate current-suite replacements.

| Pin | In wave-2 scope | Disposition | Evidence |
|---|---|---|---|
| PP-AUTH-001 | yes | **Rewritten.** The current pin asserts the corrected signed-out redirect and expired-token denial. The forged-future-`exp` admission characterization was removed from the current suite and preserved as Wave 1 history; it is not recast as a passing security assertion. ADR-4's advisory-client boundary remains documented, while server denial stays covered by protected-endpoint and session-filter tests. | `frontend/src/app/core/guards/auth.guard.spec.ts`; `docs/auth-hardening/evidence/wave-1-defect-pin-history.md`; `backend/src/test/java/com/plantpal/session/SessionEnforcementFilterTest.java` |
| PP-AUTH-002 | yes | **Rewritten to assert the fixed behaviour.** The old pin asserted the fan-out defect (three concurrent 401s producing three `logout()` and three `navigate()` calls); the suite now asserts exactly one of each across concurrent 401s, plus that the latch re-arms after the navigation settles. | `frontend/src/app/core/interceptors/jwt.interceptor.spec.ts` |
| PP-AUTH-003 | no | **Unchanged.** Cross-origin session handoff is client-alignment work outside wave 2; the pin still characterizes the replayable fragment contract. | `frontend/projects/shared-core/src/lib/session-handoff.spec.ts` |

The Wave-1 structural fixture `tools/auth-hardening/fixtures/defect-pin-schema.json` still
requires the literal `this.router.createUrlTree(['/login'])` in `auth.guard.ts`, which wave 2
legitimately changed by adding `{ queryParams: { returnUrl: state.url } }`. The fixture was not
updated with that change, so `verify_auth_inventory.py` would report source-evidence drift for
PP-AUTH-001. The verifier is not invoked by CI, so this is latent, not a build failure. It is
left untouched here because Wave 1 evidence is read-only for this mission.

## Pin lifecycle

- Tests refer to the exact Pin and Assignment values above.
- A hardening change may replace a pin only in the same reviewed change that updates its
  characterization evidence.
- Deleting or renaming a pin without updating the fixture is verifier failure.
