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

## Pin lifecycle

- Tests refer to the exact Pin and Assignment values above.
- A hardening change may replace a pin only in the same reviewed change that updates its
  characterization evidence.
- Deleting or renaming a pin without updating the fixture is verifier failure.
