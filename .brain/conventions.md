# plantpal — `.brain` conventions

<!--
Hand-maintained. FRESH ADOPTION via `brain-toolkit`'s `bin/adopt` on 2026-07-23
(toolkit v0.4.0). This file intentionally does NOT duplicate the platform's
`.brain` worker-agent conventions -- see the source of truth below -- so a
toolkit-wide or spec-wide rule change never needs a fleet-wide find/replace
across every adopted repo's own copy of this file.
-->

## Source of truth

Full worker-agent conventions for any `.brain`-enabled repo on this
platform: `../platform-vault/spec-agent-memory-system.md` (v0.6, D061), §8
"Worker Agent Conventions". Also read
`../conventions/conventions-for-agents.md`'s `.brain` rules section, and
this repo's own `CLAUDE.md` for anything repo-specific. If this file ever
disagrees with either of those, they win -- this file may be stale.

Nothing repo-specific has been recorded here yet -- this is a fresh
adoption, with no session history. Add repo-specific `.brain` conventions
here only when they genuinely diverge from the platform-wide rules above;
never copy those rules into this file wholesale.
