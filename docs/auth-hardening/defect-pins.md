# PlantPal auth defect pins

These pins freeze security-relevant current behaviour before hardening work. A pin is not an
endorsement: later units may deliberately change it, but characterization suites must make the
change visible first.

## Defect pin records

| Pin | Assignment | Surface | Current behavior | Security consequence | Characterization | Source |
|---|---|---|---|---|---|---|
| PP-AUTH-001 | authguard | classic AuthGuard | Any locally stored JWT with a future `exp` claim is treated as logged in; the guard does not validate its signature, issuer, audience, or current account state. Signed-out navigation returns a `/login` UrlTree. | A forged or revoked token can open protected client routes until the backend rejects subsequent requests. | Assert signed-out denial and forged future-exp token admission as the pinned boundary. | `frontend/src/app/core/guards/auth.guard.ts`; `frontend/projects/shared-core/src/lib/services/auth.service.ts` |
| PP-AUTH-002 | jwtinterceptor | classic JwtInterceptor | Every concurrent HTTP 401 independently calls `logout()` and navigates to `/login`; there is no shared transition latch. | A burst of rejected requests fans out duplicate session clearing and navigation side effects. | Flush concurrent 401 responses and assert the exact logout and navigation call counts. | `frontend/src/app/core/interceptors/jwt.interceptor.ts` |
| PP-AUTH-003 | sessionhandoff | cross-origin session handoff | The classic app places the bearer token in a URL fragment and Atlas copies it directly to localStorage without a one-time server exchange or destination-origin binding. | Anyone who obtains a valid handoff URL can replay the bearer token until it expires. | Assert the fragment contract, storage keys, one-time consumption, malformed-input scrubbing, and replayable payload shape. | `frontend/projects/shared-core/src/lib/session-handoff.ts`; `frontend/projects/atlas/src/main.ts` |

## Wave-2 disposition (mission 9b774285)

Recorded 2026-09-16. The records above stay frozen as the wave-1 characterization; this section
records what the wave-2 hardening change deliberately did to each pin, which is what wave-2's
"deliberately rewrite or remove the two wave-1 defect pins" asks to be made visible.

| Pin | In wave-2 scope | Disposition | Evidence |
|---|---|---|---|
| PP-AUTH-001 | yes | **Partially rewritten.** The signed-out facet was rewritten to assert the fix — the guard now returns `/login` carrying a `returnUrl` (`/login?returnUrl=%2Fplants%2F42`). The forged-future-`exp`-token facet was **retained, not rewritten**, and deliberately relabelled as a by-design boundary under ADR-4: all client-side expiry judgement is advisory, the guard never calls the server, and the server's 401 is the only verdict. The underlying property is unchanged; its fix is wave 3's server-authoritative registry, not a guard change. | `frontend/src/app/core/guards/auth.guard.spec.ts` |
| PP-AUTH-002 | yes | **Rewritten to assert the fixed behaviour.** The old pin asserted the fan-out defect (three concurrent 401s producing three `logout()` and three `navigate()` calls); the suite now asserts exactly one of each across concurrent 401s, plus that the latch re-arms after the navigation settles. | `frontend/src/app/core/interceptors/jwt.interceptor.spec.ts` |
| PP-AUTH-003 | no | **Unchanged.** Cross-origin session handoff is client-alignment work outside wave 2; the pin still characterizes the replayable fragment contract. | `frontend/projects/shared-core/src/lib/session-handoff.spec.ts` |

Two consequences to carry forward, neither blocking:

- PP-AUTH-001's retained facet means wave 2 rewrote one of its two in-scope pins to fixed
  behaviour and reframed the other. A stricter reading of "rewrite or remove" is not satisfied
  for the forged-token facet; it is closed only by wave 3's server-side enforcement, which is
  proven at the filter in `backend/src/test/java/com/plantpal/session/SessionEnforcementFilterTest.java`.
- The wave-1 structural fixture `tools/auth-hardening/fixtures/defect-pin-schema.json` still
  requires the literal `this.router.createUrlTree(['/login'])` in `auth.guard.ts`, which wave 2
  legitimately changed by adding `{ queryParams: { returnUrl: state.url } }`. The fixture was not
  updated with that change, so `verify_auth_inventory.py` would report source-evidence drift for
  PP-AUTH-001. The verifier is not invoked by CI, so this is latent, not a build failure. It is
  left untouched here because wave-1 evidence is read-only for this mission.

## Pin lifecycle

- Tests refer to the exact Pin and Assignment values above.
- A hardening change may replace a pin only in the same reviewed change that updates its
  characterization evidence.
- Deleting or renaming a pin without updating the fixture is verifier failure.
