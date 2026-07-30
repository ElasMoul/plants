# plantpal — architecture (stable facts)

<!--
Hand-maintained (this file + conventions.md + overrides.md + config.yml are
the hand-edited files under .brain/ -- see conventions.md for the source of
truth this repo's `.brain` follows). Seeded by `brain-toolkit`'s `bin/adopt`
on 2026-07-23 (toolkit v0.4.0) from this repo's own HEXAGON.md and README.md at
adoption time. FRESH ADOPTION -- no history, no build-order churn, and no
decisions have been captured here yet; this is a skeleton, not a record of
anything that happened before adoption. Keep this file to STABLE design
facts as they're established -- build-order and open-questions churn
belongs in the spec / session log, not here.
-->

## Purpose

History and depth for the platform — never load-bearing; overview goes to the state-feed.

## Stable identity facts (seeded from HEXAGON.md at adoption time)

- functionalName: `plantpal` · kind: `runtime` · side: `host`
- Deps (from HEXAGON.md): `contracts`, `state-feed`
- Governing decisions (from HEXAGON.md): D011, D020, D021, D041, D039, D047
- Full spec: `../platform-vault/spec-plantpal.md` -- this file is a
  stable-facts seed, not a replacement; the spec is still the authoritative
  work order.

<!-- Nothing else has been established yet. Add stable facts here as this
     repo's own sessions confirm them. -->
