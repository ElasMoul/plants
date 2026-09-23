# PlantPal feature handoff index

Project-wide rules remain in the root `AGENTS.md`. This file is a concise build
status index, not a second copy of those rules.

| Feature | Status |
|---|---|
| Administration console | Complete on `codex/plantpal-admin-dashboard` (2026-09-21) |
| Administration expansion | Complete 2026-09-22: lifecycle, quotas, garden management, native DeepSeek, migration 035 |
| Hosted DeepSeek + plant viewer | Complete 2026-09-23; live native provider test passed; photo/history modal |
| Local administrator | admin@plantpal.com (existing ID 3 renamed; same password and garden) |

Read `STATE.md` for validation and handoff, `TASK_PLAN.md` for completed scope,
`ARCHITECT.md` for decisions, and `../docs/admin-console.md` for operation.
The prior `.claude/` archive remains the source for older feature history.

| I18N — User languages | Complete on `codex/plantpal-french-localization` (2026-09-23): Classic EN/FR/AR UI, compact selector, Arabic RTL/plurals, localized dates/calendar/pagination/speech, persistent French/Arabic AI content and language-aware chat (migrations 036–037); owner testing before dev merge, Atlas pending; `docs/localization.md` |
