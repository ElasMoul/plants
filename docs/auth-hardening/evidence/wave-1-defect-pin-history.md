# Wave 1 defect-pin history

This is a historical copy of the PP-AUTH-001 characterization removed from the
current Wave 2 suite. It preserves the Wave 1 fact-base without presenting the
client-side limitation as a current acceptance assertion.

- **Pin:** PP-AUTH-001
- **Historical behaviour:** a locally stored JWT with a structurally valid,
  future `exp` was admitted by the classic `AuthGuard`; the guard does not
  validate its signature, issuer, audience, or server-side session state.
- **Historical consequence:** a forged or revoked token could render a protected
  route until the backend rejected its next request.
- **Boundary retained:** ADR-4 keeps the guard optimistic and the server's 401
  remains the authorization verdict. The current suite asserts the safe redirect
  contract and expired-token denial; backend protected-endpoint and session-filter
  tests assert that the server denies invalid or revoked requests.

The original Wave 1 evidence remains immutable in its original commits and
brain-session record. This copy exists solely to make the Wave 2 pin removal
auditable without changing that evidence.
