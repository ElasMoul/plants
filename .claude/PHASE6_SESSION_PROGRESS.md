# Phase 6 Planning Session — Progress Tracker
> If this session is interrupted (rate limit, crash, etc.), resume by reading this file first,
> then continuing from the first unchecked item below. Do not redo checked items.
> Started: 2026-06-19. Architect session — planning only, no feature code written.

## ⚠️ Numbering deviation from the original request — READ FIRST
The user's prompt asked for tasks "T3.1–T3.14" and migrations "012–015", filed under "Phase 2".
Both collide with work that is ALREADY COMPLETE and documented in STATE.md:
- T3.1, T3.2, T3.4, T3.4b, T3.5, T3.6, T3.7, T3.8, T3.9 already exist (Reminder + Care Log +
  Actionable Care Plans + the EXISTING `TreatmentPlan` entity from T3.4 — a different concept
  from this session's new `Treatment` entity; see ARCHITECT.md's disambiguation note).
- Migrations 012–015 are already applied (012_add_treatment_plans, 013_add_reminder_instruction,
  014_add_step_detail, 015_add_ai_model_used).

A SECOND collision turned up mid-session: TASK_PLAN.md already has a pre-existing (unstarted)
**"Phase 5 — Launch"** section with its own T5.1–T5.8 (performance, security, API docs,
deployment, beta testing, release). My first attempt at renumbering this work to "Phase 5"
collided with that too.

**Final resolution:** this entire initiative is filed as **Phase 6**, tasks **T6.1–T6.14**,
migrations **016–019**. Despite the higher number, Phase 6 is intended to execute BEFORE Phase 5
(Launch) — phase numbers here reflect order of definition in the docs, not recommended execution
order. This is documented in STATE.md's "Current Phase" section and "Phase 6" section.

## Source brief
The full domain-restructure brief (Species entity, Treatment entity, navigation changes, Plant
page redesign, Home page, Garden species-first restructure, Identification flow 3-path redesign)
was pasted in full by the user in this session's first message. Re-read it from conversation
history if needed — not duplicated here in full, only decisions/output are tracked below.

---

## Checklist

- [x] Read all `.claude/` files (STATE.md, ARCHITECT.md, TASK_PLAN.md, AGENTS.md, BACKEND.md,
      FRONTEND.md, DESIGN_PROGRESS.md, CLAUDE.md) — done, full context loaded
- [x] Checked git branch (`newPhase`, clean) and recent commits
- [x] Detected and resolved the T3.x / migration-012-015 collision
- [x] Detected and resolved the SECOND collision (pre-existing TASK_PLAN.md "Phase 5 — Launch",
      T5.1–T5.8) — renumbered this whole initiative to Phase 6 / T6.1–T6.14
- [x] Verified branch names PP-029 through PP-037 don't collide with existing PP-0xx branches
      (highest pre-existing was PP-028)
- [x] Create this progress file (superseded PHASE5_SESSION_PROGRESS.md — that file's content was
      wrong due to the Phase-5 collision; this file replaces it)
- [x] Update STATE.md:
  - [x] Mark current build state accurately (NOT "Phase 1 complete" — see note below)
  - [x] Add "Phase 6 — Species & Treatment Domain Restructure" section with T6.1–T6.14 listed,
        all 🔲 Not Started
  - [x] Add migration 016–019 placeholders to the migration sequence list
  - [x] Note the existing gap: 015_add_ai_model_used.sql was missing from the "DB Migration
        Sequence" list (it shipped in T3.9 but the list block was never updated) — fixed
- [x] Update ARCHITECT.md:
  - [x] New domain model patterns: Species (shared-across-users entity), Treatment (NEW entity —
        distinct from the existing TreatmentPlan from T3.4)
  - [x] "Two Treatment concepts" disambiguation note (TreatmentPlan vs. new Treatment)
  - [x] Identification flow 3-path decision tree (Garden scan / Species-page scan / Plant-page
        health-check scan)
  - [x] Treatment lifecycle state machine (DRAFT → IN_PROGRESS → COMPLETED/DISMISSED)
  - [x] Species data enrichment async pattern
  - [x] Sticky header + icon button bar Angular pattern
  - [x] Migration Sequencing section updated with 016–019
  - [x] Renumbered all T5.x/"Phase 5" references in this new section to T6.x/"Phase 6"
- [x] Add T6.1–T6.14 to TASK_PLAN.md with full Claude Code prompts ready to paste
- [x] Add a Phase 6 row to TASK_PLAN.md's "Task Summary" table at the end of the file
- [x] (Cleanup) Deleted the stale `.claude/PHASE5_SESSION_PROGRESS.md` file
- [ ] Output SESSION SUMMARY block (AGENTS.md format) as the final chat message — do this last,
      after re-reading this checklist to confirm everything above is genuinely done

## Session complete (pending final summary output)
All STATE.md / ARCHITECT.md / TASK_PLAN.md edits are done as of this checkpoint. If resuming
after an interruption and every box above is checked, all that's left is producing the
SESSION SUMMARY block for the user — no further file edits needed. Verify with `git diff
.claude/` that the three files actually contain the expected sections before assuming this is
true from memory alone.

## Note on "Phase 1 complete" instruction
The user's brief said "we are closing Phase 1 (everything completed so far)". In this project's
actual phase numbering (CLAUDE.md / STATE.md), the team is mid-way through **Phase 3**
(Reminders) with **Phase 4** (Chat) basically done and **Phase 2** (AI Identification) fully
complete. There is no unfinished "Phase 1" to close — Phase 1 (Auth + Plant CRUD) has been done
since early sessions. Treating the user's "Phase 1" as shorthand for "everything done up to now"
rather than literally CLAUDE.md's Phase 1. STATE.md is updated to reflect real status, not to
mark a fictitious Phase 1 as freshly completed.

## Resume instructions if cut off mid-session
1. Check which boxes above are unchecked.
2. Re-read STATE.md / ARCHITECT.md / TASK_PLAN.md to see how far the edits actually got (the
   checklist above is the source of truth, not memory).
3. Continue from the first unchecked box — at last save, that's writing T6.1–T6.14 into
   TASK_PLAN.md (appended near the end of the file, after the existing "Phase 5 — Launch"
   section's T5.8/release content, before "## Task Summary").
4. Each T6.x prompt should follow the exact same format as T2.A–T2.F / T3.4/T3.5 in TASK_PLAN.md:
   `### T6.N — Name (Agent) 🤖 AI`, `**Branch:**`, `**Depends on:**`, a `>` context blockquote,
   then a fenced ` ```` ` Claude Code prompt block, then a `**Verify:**` line.
5. Once all boxes are checked, delete this file (and the stale PHASE5 one) or leave them —
   harmless either way, but consider folding any leftover useful notes into ARCHITECT.md /
   STATE.md and removing the trackers once Phase 6 execution actually begins.
