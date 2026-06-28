---
description: Persist current session state to .claude/ files and sync the plants-vault (D6 dual-maintenance). Safe to run mid-session or at end. Pass an optional title or freeform summary as the argument.
allowed-tools: Read, Edit, Write, Glob, Grep, Bash
argument-hint: "[optional session title or freeform summary]"
---

# /save — PlantPal Session Persist & Vault Sync

You are acting as the **Architect agent** for PlantPal. This command persists the current
session's state to `.claude/` coordination files and mirrors relevant changes to the
knowledge vault per decision D6.

**Hard constraints — never break:**
- Do **not** touch any source file (`.java`, `.ts`, `.html`, `.scss`, `.xml`, `src/` anything).
- Do **not** generate feature code, test code, or migration SQL.
- Write **only** to `.claude/` files and `../plants-vault/`.
- Keep every edit **surgical** — a targeted insert/replace is always preferred over rewriting a whole file.
- If `STATE.md` and `ARCHITECT.md` disagree on current state: **flag, do not guess**.

`$ARGUMENTS` = optional session title or freeform summary hint.
If blank, derive the session summary from the current conversation context.

---

## Step 0 — Read before writing (always)

Read all four sources in parallel before writing anything:

1. `.claude/STATE.md` — note the "Last updated" date, branch statuses, "Next Tasks" list, and "Key Open Decisions".
2. `.claude/ARCHITECT.md` — note the highest D-number assigned so far (to assign the next one correctly).
3. `.claude/TASK_PLAN.md` — note which phases/tasks are marked complete, in-progress, or not started.
4. `../plants-vault/wiki/hot.md` — note "Current Project Status", "Active Threads", and "Key Recent Facts".

**Conflict check:** If `STATE.md` and `ARCHITECT.md` show inconsistent current state (e.g. different active phase, different migration sequence number), stop and report both values. Ask which is authoritative before continuing. Do not silently pick one.

---

## Step 1 — Update `.claude/STATE.md`

Surgical edits only. Do not rewrite sections that are unchanged.

- **"Last updated" line** (top): set to today's date + a 3–5 word description of what changed.
- **Branch/migration status table**: update rows only for branches that were created, merged, or changed this session. Do not touch unchanged rows.
- **Phase Status table**: flip any phase that completed this session from `🔲`/`🟡` → `✅`. Append the completing commit ref if known.
- **What Was Built section**: append a short bullet for any newly completed phase. Do not edit existing phase summaries.
- **Next Tasks list**: remove tasks completed this session; add tasks that emerged. Keep ordered (most urgent first).
- **Key Open Decisions**: close any decisions resolved this session; add new open decisions (no D-number yet if unresolved — just a plain bullet).
- **Post-session bugfixes block**: if fixes were applied outside a formal phase task, add a dated block using the same format as the existing "Post-Phase-10 Bugfixes (2026-06-28)" block.
- **Known Tech Debt**: add items introduced; remove items resolved this session.

---

## Step 2 — Update `.claude/ARCHITECT.md`

Surgical edits only. Do not reorder or reformat the file.

**New architectural decisions (resolved this session):** Append using the next available
D-number (from Step 0). Use this exact format — insert before the `---` separator that
precedes the Knowledge Vault section, to keep decisions chronological:

```
### Architecture Decision D{N} — {Short Title} (Phase {X}, {YYYY-MM-DD})
> {1-sentence trigger or context}

**Decision:** {what was decided, 2–4 sentences}

**Rationale:** {why this option over the alternatives}

**Consequences:** {what this commits us to; what follow-up it creates}
```

**Unresolved decisions (no ruling yet):** Do NOT assign a D-number. Add a one-liner
under the nearest relevant "Open Decisions" or "Two design forks" note instead.

**Superseded decisions:** Add an `⚠️ Superseded by D{N} ({date})` note to the old entry.
Do not delete the old entry.

**New established patterns (if a new coding pattern was confirmed this session):**
Append a short entry under the appropriate `### ...` heading in the "Established Patterns"
section, matching the existing heading style and depth.

Do **not** touch the "Current State", "Your Behavior", "Domain Model", or
"Migration Sequencing" sections unless those areas explicitly changed this session.

---

## Step 3 — Update `.claude/TASK_PLAN.md` (skip if no task status changed)

Skip this step entirely if no task or phase status changed this session.

If tasks changed:
- **Completed tasks**: move their detail block to the "COMPLETED PHASES (one-liner)" section
  at the top, compressed to the one-liner format matching existing entries.
- **Completed phase**: change the phase header from `🔲 NOT STARTED` / `🟡 IN PROGRESS`
  → `✅ COMPLETE` and collapse its detail block.
- **Status Summary table** (bottom of file): update affected rows.
- Do **not** renumber or reformat tasks that did not change.

---

## Step 4 — Sync the plants-vault

Vault root: `../plants-vault`. Read each target file before writing to it.

### 4a — `../plants-vault/wiki/hot.md` (always update this, even if nothing else changed)

- **Current Project Status table**: update rows for phases whose status changed. Use the same
  `🔲`/`🟡`/`✅` emoji format already in the file.
- **Key Recent Facts**: replace stale entries with facts from this session that a future agent
  would want in the first 30 seconds. Remove facts older than two phases ago.
- **Active Threads**: update to reflect what is actually in-progress or blocked right now.
- **What Was Just Added to the Vault**: list any new wiki pages created in steps 4b–4d.

### 4b — Phase page (only if a phase completed or meaningfully progressed this session)

Path: `../plants-vault/wiki/phases/phase-{N}-{slug}.md`
Check the directory to confirm the exact filename before writing.

- Change status from planned → active → complete as appropriate.
- Fill in actual deliverables (built vs. planned).
- Note tasks that were skipped, deferred, or changed scope.
- Link to any new decision or learning pages created in 4c/4d.

If the page does not exist yet, check naming conventions used by existing phase pages
(`../plants-vault/wiki/phases/`) before creating a new one.

### 4c — New decision pages (only for decisions logged in Step 2)

One file per decision. Path: `../plants-vault/wiki/decisions/d{N}-{slug}.md`

```markdown
---
type: decision
title: "D{N} — {title}"
status: active
phase: "{phase}"
tags: []
related: []
---

## Context
{what problem or situation prompted this decision}

## Decision
{what was decided}

## Rationale
{why this option, concisely}

## Consequences
{what this commits us to; any follow-up needed}

## Alternatives Considered
- {road not taken 1}
- {road not taken 2}
```

After creating: add a link entry to `../plants-vault/wiki/decisions/_index.md`.

### 4d — New learnings pages (only for hard-won insights found this session)

Qualifying learnings: bugs with non-obvious root causes, anti-patterns discovered,
surprising library/framework behavior, or anything that would save a future session 30+ minutes.
Do not create a learning page for routine implementation detail.

Path: `../plants-vault/wiki/learnings/{slug}.md`

```markdown
---
type: learning
title: "{title}"
severity: critical | high | medium
phase: "{phase}"
tags: []
related: []
---

## What Went Wrong
{the symptom}

## Root Cause
{the actual cause}

## The Fix
{what resolved it}

## Rule of Thumb
{one sentence a future developer can apply without re-reading the story}
```

Mark `critical`/`high` severity entries with ⚡ in `../plants-vault/wiki/learnings/_index.md`.
Add each new page there.

### 4e — `../plants-vault/wiki/index.md`

- Update the phase status emoji for any phase that progressed.
- Add links to new decision and learning pages in their catalog sections.
- Increment "Total pages" by the count of new pages created.

---

## Step 5 — Report

Output this exact block:

```
## /save complete

### .claude/ files updated
- STATE.md: {one line per change, or "no changes"}
- ARCHITECT.md: {one line per change, or "no changes"}
- TASK_PLAN.md: {one line per change, or "no changes"}

### Vault pages updated
- hot.md: {one line per section changed}
- {page path}: {one line summary}

### New decisions logged
- D{N}: {title} → {vault page path}
(none if no new decisions)

### New learnings logged
- ⚡ {title} → {vault page path}
(none if no new learnings)

### Flags / conflicts
{STATE.md ↔ ARCHITECT.md disagreements found, or "none"}
{Content that was ambiguous or could not be mapped, or "none"}
```
