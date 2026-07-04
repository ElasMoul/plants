# PlantPal — Agent Recovery Guide
> If you lose a conversation, paste the matching section below
> as the FIRST message in a new Claude Code conversation.
> The agent will be fully restored.

---

## ARCHITECT AGENT
> Conversation type: Claude.ai (this project)
> Scope: Architecture and infrastructure only.
> To restore: paste contents of ARCHITECT.md as first message.

## BACKEND AGENT
> Conversation type: Claude Code
> Scope: Java/Spring Boot — execution and troubleshooting.
> To restore: paste contents of BACKEND.md as first message.

## FRONTEND AGENT
> Conversation type: Claude Code
> Scope: Angular — execution and troubleshooting.
> To restore: paste contents of FRONTEND.md as first message.

> All three agents should also read STATE.md (current phase, next tasks) and
> TASK_PLAN.md (the task this session is likely working from) once restored.

---

## How to keep these files current
After each session:
1. Update STATE.md (completed tasks, active branches, open items)
2. Update the relevant agent .md if new patterns or decisions were established
3. Commit: `chore(agents): update agent memory — [what changed]`

### Keeping memory lean
These files are restore prompts and a live status board, not an archive. When a
file grows large with content that's now historical (a phase shipped, a decision
is settled, a bug is fixed and verified), archive the current version into
`Archive/<FileName>_<N>.md` first, then rewrite the live file to keep only what's
still load-bearing for future sessions. Durable patterns belong in ARCHITECT.md;
current status and the open task list belong in STATE.md; implementation detail
belongs in BACKEND.md/FRONTEND.md's inventories, not as session-by-session prose.

---

## Session Summary Format
> Every agent (Architect, Backend, Frontend) MUST output this block
> at the END of every prompt response. The user pastes it back to the
> Architect to trigger a .claude/ update.

```
─────────────────────────────────────────────
SESSION SUMMARY — [AGENT] — [YYYY-MM-DD]
─────────────────────────────────────────────
Branch: feature/PP-XXX-...

Completed:
- [what was done, one line each]

Files changed:
- path/to/File.java — [what changed]

Key decisions:
- [decision] — [why]

Infra / config changes:
- [env vars added, migrations added, YAML changed]

Tests:
- Added: [test files]
- Missing: [what still needs tests]

Open items / blockers:
- [anything that needs attention next session]
─────────────────────────────────────────────
```

Rules for the summary:
- Always output it, even if nothing changed (say "No changes this session")
- Be specific — "updated PlantNetClient" is not enough, say what and why
- List every file touched, not just the "important" ones
- Architect reads this and updates STATE.md, BACKEND.md, FRONTEND.md as needed
