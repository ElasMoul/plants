---
demandId: app-studio-20260907-plantpal-t1-20
worker: plantpal
date: 2026-09-08
status: done
shipped:
  - "commit d0c46dc: auth surface inventory and structural verifiers"
  - "23 frontend route records, 48 backend endpoint records, and 3 defect pin records"
  - "3 controller declarations and 3 frontend route declarations sampled against source"
summaryRef: "commit d0c46dc on feature/PP-096-auth-surface-inventory (T1.20 inventory and verifier assets)"
---

# Fulfillment — PlantPal auth surface inventory

## What shipped

- `docs/auth-hardening/auth-surface-inventory.md` records every classic frontend route, the
  routerless Atlas document, and all 48 Spring controller method/path pairs with guard or Spring
  Security classification.
- `docs/auth-hardening/defect-pins.md` registers three source-anchored current-behaviour pins for
  AuthGuard, JwtInterceptor, and cross-origin session handoff characterization.
- `tools/auth-hardening/verify_auth_inventory.py` parses the documentation, independently
  enumerates all Spring controller mappings, checks the fixture-pinned frontend records and defect
  pins, and validates three controller plus three routing samples against source declarations.
- `tools/auth-hardening/verify_allowed_change_paths.py` builds its assertion subject from the
  complete dispatched-base (`ccde4a8`) branch diff, index, worktree, and untracked changed-file
  channels before applying the documentation/verifier allowlist. A temporary forbidden
  `frontend/src/` probe was observed failing and was removed.

## Acceptance evidence

`python tools/auth-hardening/verify_auth_inventory.py --inventory
docs/auth-hardening/auth-surface-inventory.md --pins docs/auth-hardening/defect-pins.md
--fixture-dir tools/auth-hardening/fixtures` exits 0 and prints:

```text
auth_inventory_verification=PASS
frontend_route_records=23
backend_endpoint_records=48
defect_pin_records=3
sampled_route_declarations=3
sampled_controller_declarations=3
```

`python tools/auth-hardening/verify_allowed_change_paths.py --input . --fixture
tools/auth-hardening/fixtures/inventory-allowed-paths.json` exits 0 and prints positive
`changed_file_records` plus `production_behavior_file_records=0`.

The sampled controller rows are AuthController login (`POST`, public), PlantController archive
(`DELETE`, protected), and IdentificationController retry (`POST`, protected). The sampled route
rows are the public landing route, protected plant detail route, and protected reminders route;
their component declarations and inherited guards all match the inventory.

## What the origin must know

- Redirect rows are classified by effective target: `/dashboard` and the classic wildcard have no
  guard on the redirect declaration itself but resolve to an `AuthGuard`-protected target.
- Atlas has no Angular Router or client guard. Its root document is public; its live data remains
  protected by the backend endpoints recorded here.
- Subsequent denial-suite work can select every backend row whose Access column is `protected`;
  public photo aliases and auth endpoints are intentionally excluded from that set.

## Not done / caveats

- No production source, route declaration, controller, or security behaviour changed.
- `status: done` is this worker's claim only; coordinator validation and owner approval remain the
  demand-system gate.
