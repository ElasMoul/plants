---
session_id: 2026-09-28_0019_verify-prior-session-s-v0-36-0-repin-ful
agent: plantpal
model: deepseek-flash
started: 2026-09-28T00:19:24+00:00
ended: 2026-09-28T00:20:14+00:00
task: "Verify prior session's v0.36.0 repin fulfillment and record the D114 coordination-push finding"
priority: 2
status: done
launch: interactive
decisions: []
changes:
  - ".brain/events/2026-09-28_0019_verify-prior-session-s-v0-36-0-repin-ful.events.jsonl: working-tree change (??) -- auto-recorded, not hand-described"
  - ".brain/sessions/2026-09-28_0019_verify-prior-session-s-v0-36-0-repin-ful.md: working-tree change (??) -- auto-recorded, not hand-described"
  - ".brain/sessions/2026-09-28_0114_fulfill-demand-contracts-20260928-plantp.md: working-tree change (??) -- auto-recorded, not hand-described"
lessons: []
context_missing: []
notes_used: []
vault_sync: none
close: confirmed
---


## Log

**00:19 Session opened** via `brain session open`.

**Current-state check first — the capability was already shipped.** Demand
`contracts-20260928-plantpal-repin-app-deploy-receipt-and-identity` was worked to
completion by the 00:30 session on `feature/PP-115-contracts-v036-app-deploy-repin`
(6 commits, PR #192 open, all required checks green incl. the new Dev Delivery Tool
CI job). The 01:14 session was a re-dispatch that ended `partial` with no changes.
So this session verified rather than re-implemented.

**Verification (independent, not by trusting the branch's own tests).**
56/56 tests green in `tools/dev-delivery/.venv`. The 3 vendored schemas are
**byte-identical to the v0.36.0 tag** (`sha256sum` vs `git show v0.36.0:...`).
All 3 real D113 receipts (2 `deploy` + 1 genuine `rollback`) emit a valid closed
`delivery.deployment-receipt` through the actual CLI, validated by an
independently built `referencing` registry — 0 schema errors, and every `passed`
receipt satisfies the cross-field rules (`observed.revision == mergedRevision`,
`observed.deploymentId == deploymentId`). Miss transport exact: exit 4, nothing on
stdout, stderr exactly `deployment_not_found: <id>` (no `dev-delivery:` prefix).
`receipt`/`lookup` each print exactly one JSON document on exit 0.

**Two findings recorded in the report's caveats (commit b5955f9).**
1. **D114 is on `dev` only.** `749c8b4` added `paths-ignore: demands/**, .brain/**,
   **/*.md` to `deploy.yml`, but `git merge-base --is-ancestor 749c8b4 origin/main`
   fails and `origin/main`'s `deploy.yml` still triggers on every push. So the
   owner's `dev` → `main` release is what unblocks plantpal's demand doorbell —
   the two raises to factory/runtime cannot ring before it.
2. **The coordinator reads the filesystem, not git.** `BoardService.scanBoard()`
   walks `<root>/<repo>/demands/` on disk, so this fulfillment shows on `/board`
   only while the shared plantpal checkout is on this branch. It is absent from
   `dev` and `main`. Merging PR #192 is what makes the registration durable rather
   than incidental — which likely explains the repeated re-dispatches.

**Decisions.** Did **not** merge PR #192 and did **not** push `main`: no evidence
any plantpal session merges its own PR (the owner merged #191), the standing orders
put review at chunk boundaries, and main still deploys production on any push. The
docs clarification was rebased onto the owner's `dev`-into-branch merge (`0ec70ed`),
re-tested, and pushed to the PR branch instead.

**NOTE for the next session:** the plantpal worktree was restored to `dev` (its
state at session start) after the push, so `/board` will no longer list this
fulfillment until PR #192 merges.

**00:20 Session closed via `brain session close` (status: done).**
