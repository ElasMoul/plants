---
demandId: brain-toolkit-20260914-fleet-repin-v063
worker: plantpal
date: 2026-09-14
status: done
shipped:
  - ".brain/toolkit-pin moved from v0.6.2 to v0.6.3, committed and pushed"
  - "Verified by use with --model: `python .brain/bin/brain session open \"verify v0.6.3 pin model field\" --priority 3 --model claude-opus-5` wrote a session file whose frontmatter reads `model: claude-opus-5`"
  - "Verified the omitted-flag path: the same command without --model wrote `model: unknown`, not `claude-code`"
  - "Added a hand-written `.brain` section to .claude/CLAUDE.md documenting that every `session open` must pass --model with the actual model id (bin/adopt could not seed it: this repo's CLAUDE.md lives at .claude/CLAUDE.md, not the repo root)"
---

# Fulfilment report — fleet re-pin to brain-toolkit v0.6.3 (plantpal)

## Pin move
Found `.brain/toolkit-pin` reading **v0.6.2** — matching the expected fleet-wide state left by the
2026-07-31 sweep. Moved it to **v0.6.3**. The `../brain-toolkit-worktrees/v0.6.3/` worktree already
existed, so the shim resolved without any worktree creation step.

## Evidence of the bug being fixed (before/after)
The session file this very demand opened *under v0.6.2* is the before-shot:
`.brain/sessions/2026-09-14_1816_fulfill-demand-brain-toolkit-20260914-fl.md` carries
`model: claude-code`, which is false — the session is running on `claude-opus-5`.

After the pin move, two throwaway verification sessions were opened:

1. `--model claude-opus-5` → frontmatter `model: claude-opus-5`. The flag is honoured verbatim.
2. no `--model` flag → frontmatter `model: unknown`. This is the part that proves the *fix* rather
   than just the pin bump: under v0.6.2 this path hardcoded `claude-code`.

Both verification session files were deleted afterwards (they were scratch, not real work), and
`.brain/.current_session` was restored to point back at this demand's own session, which the second
`session open` had overridden.

## CLAUDE.md documentation
`bin/adopt` in v0.6.3 seeds its `.brain` section into a repo-root `CLAUDE.md`. plantpal has no
root `CLAUDE.md` — its Claude Code instructions file is `.claude/CLAUDE.md` — so adopt's
auto-append had nothing to target and this repo needed the hand edit the demand anticipated.
Appended the section to `.claude/CLAUDE.md` with the `<!-- brain-adopt:section -->` marker intact
(so a future adopt run detects it and skips rather than duplicating), including the full
`--model <your-actual-model-id>` guidance, the example model ids, the `model: unknown`-not-a-guess
rationale, a note on the pre-v0.6.3 `claude-code` defect, and a note explaining the non-standard
file location.

`AGENT.md` was left untouched — it is structurer-generated and carries a "do not edit" banner.

## Scope note
Nothing outside plantpal was touched. `brain-toolkit` itself was read only (the v0.6.3 worktree's
`bin/adopt`, to match the seeded wording exactly) and never written to.
