# Vault Sync Template
> Use this after every phase completion to keep `../plants-vault` in sync with what was built.
> The agent doing the sync writes ONLY to `../plants-vault` and `.claude/` files.
> It does NOT touch any source code.

---

## How to Use

1. After a phase completes, compose a session summary (format below).
2. Open a new Claude Code conversation (or continue the current one).
3. Paste the prompt below, replacing `[PASTE SESSION SUMMARY HERE]` with your summary.
4. The agent executes the 6 steps and reports back.

---

## Session Summary Format

A good session summary includes:
- **Phase / tasks completed** — what was built, what was skipped, what changed scope
- **Key files changed** — with a one-line description of the change
- **Decisions made** — architectural choices with rationale
- **Hard-won learnings** — bugs, anti-patterns, surprising discoveries
- **Open items** — deferred work, disabled CI steps, pending decisions

Minimum viable summary: a list of completed tasks + one bullet per learning that should survive into future sessions.

---

## The Sync Prompt

```
// ARCHITECT TASK — Vault sync after phase completion
// Paste the session summary from the completed phase below this block.
// Do NOT modify any source code. Write ONLY to plants-vault and .claude/ files.

[PASTE SESSION SUMMARY HERE]

---

STEP 1 — Read current state (do not write yet):
- .claude/STATE.md
- .claude/ARCHITECT.md (if the session summary mentions new patterns or decisions)
- ../plants-vault/wiki/hot.md
- The relevant phase page: ../plants-vault/wiki/phases/phase-{N}-*.md

STEP 2 — Update the phase page (../plants-vault/wiki/phases/phase-{N}-*.md):
- Change status from planned/active → complete (or active if partially done)
- Fill in actual deliverables (what was built vs. what was planned)
- Add any tasks that were skipped, deferred, or changed scope
- Add links to any new decisions or learnings created in this step

STEP 3 — Create new decision pages if needed:
- One page per new architectural decision made during this phase
- File as: ../plants-vault/wiki/decisions/{slug}.md
- Add to ../plants-vault/wiki/decisions/_index.md
- Decision page format:
  - Frontmatter: type, title, status (active/superseded), phase, tags, related
  - Sections: Context, Decision, Rationale, Consequences, Alternatives Considered

STEP 4 — Create new learnings pages if needed:
- One page per hard-won insight, bug root-cause, or anti-pattern found
- File as: ../plants-vault/wiki/learnings/{slug}.md
- Add to ../plants-vault/wiki/learnings/_index.md
- Mark high-severity learnings with ⚡
- Learning page format:
  - Frontmatter: type, title, severity (critical/high/medium), phase, tags, related
  - Sections: What Went Wrong, Root Cause, The Fix, Rule of Thumb

STEP 5 — Update hot.md (../plants-vault/wiki/hot.md):
- Update "Current Project Status" table — phase emoji and status text
- Update "Key Recent Facts" with anything from the session summary
  that a future agent would want to know immediately
- Update "Active Threads" — what's still in progress, what's next
- Update "What Was Just Added to the Vault"

STEP 6 — Update wiki/index.md and .claude/ files:
- ../plants-vault/wiki/index.md:
  - Change the phase status emoji (🔲/🟡/✅) for the completed phase
  - Add any new decision or learning pages to their catalog sections
  - Update "Total pages" count
- .claude/STATE.md:
  - Add a "Phase N — COMPLETE" section at the top
  - Update the "Current Phase" section and status table
  - Update "Next Tasks"
- .claude/CLAUDE.md:
  - Update the "Current Build Status" table in the bottom section
- .claude/TASK_PLAN.md:
  - Update the phase header from 🟡 PLANNED → ✅ COMPLETE

When done, report:
- Which files were created or updated (with one-line summary of change)
- Any session summary content that was ambiguous or couldn't be mapped
- Any stale content found in .claude/ files that was corrected as a side-effect
```

---

## Notes for the Sync Agent

- **Never touch source code** — this is a documentation-only task.
- **Verify file paths before writing** — read the file first if unsure of current content.
- **The `.claude/` files are the authoritative coordination layer** — the vault is for history and exploration. Don't duplicate STATE.md content verbatim; summarize and cross-reference.
- **Only the Architect writes to the vault** — Backend and Frontend agents read only.
- **Decision slugs:** use kebab-case, start with `d{N}-` for sequentially numbered decisions (e.g. `d7-species-harvest`). Check the current high-water mark in `decisions/_index.md` before numbering.
- **Learning slugs:** descriptive kebab-case (e.g. `singleton-container-testcontainers`).
- **Page counts:** increment the total in `index.md` by the number of new pages created.
