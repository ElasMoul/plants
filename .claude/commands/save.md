---
description: Persist current session state to the .claude/ files (single memory layer). Safe to run mid-session or at end. Pass an optional title or freeform summary as the argument.
allowed-tools: Read, Edit, Write, Glob, Grep, Bash
argument-hint: "[optional session title or freeform summary]"
---

# /save — PlantPal Session Persist

You are acting as the **Architect agent** for PlantPal. This command persists the current
session's state to the `.claude/` coordination files — the single memory layer for this
repo (the former external plants-vault was removed 2026-07-15).

**Hard constraints — never break:**
- Do **not** touch any source file (`.java`, `.ts`, `.html`, `.scss`, `.xml`, `src/` anything).
- Do **not** generate feature code, test code, or migration SQL.
- Write **only** to `.claude/` files (and `PROGRESS.md` for platform-delta sessions).
- Keep every edit **surgical** — a targeted insert/replace is always preferred over rewriting a whole file.
- If `STATE.md` and `ARCHITECT.md` disagree on current state: **flag, do not guess**.

`$ARGUMENTS` = optional session title or freeform summary hint.
If blank, derive the session summary from the current conversation context.

---

## Step 0 — Read before writing (always)

Read all three sources in parallel before writing anything:

1. `.claude/STATE.md` — note the "Last updated" date, branch statuses, "Next Tasks" list, and "Key Open Decisions".
2. `.claude/ARCHITECT.md` — note the highest D-number assigned so far (to assign the next one correctly).
3. `.claude/TASK_PLAN.md` — note which phases/tasks are marked complete, in-progress, or not started.

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
- **Learnings**: hard-won insights (bugs with non-obvious root causes, anti-patterns,
  surprising library/framework behavior — anything that would save a future session 30+
  minutes) get a short dated entry here (symptom → root cause → fix → rule of thumb).
  Do not record routine implementation detail.

---

## Step 2 — Update `.claude/ARCHITECT.md`

Surgical edits only. Do not reorder or reformat the file.

**New architectural decisions (resolved this session):** Append using the next available
D-number (from Step 0). Use this exact format — insert before the `---` separator that
precedes the Project Memory section, to keep decisions chronological:

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

## Step 4 — `PROGRESS.md` (platform-delta sessions only)

If this session did platform-delta work (demands, gateway, contracts, manifest — anything
under the platform integration notice), append the handoff entry to `PROGRESS.md` at the
repo root per `../CLAUDE.md`. Skip for pure feature-work sessions.

---

## Step 5 — Report

Output this exact block:

```
## /save complete

### .claude/ files updated
- STATE.md: {one line per change, or "no changes"}
- ARCHITECT.md: {one line per change, or "no changes"}
- TASK_PLAN.md: {one line per change, or "no changes"}
- PROGRESS.md: {one line, or "n/a (no platform-delta work)"}

### New decisions logged
- D{N}: {title}
(none if no new decisions)

### New learnings logged
- ⚡ {title} → STATE.md
(none if no new learnings)

### Flags / conflicts
{STATE.md ↔ ARCHITECT.md disagreements found, or "none"}
{Content that was ambiguous or could not be mapped, or "none"}
```
