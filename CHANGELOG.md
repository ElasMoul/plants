# Changelog — plantpal

> PlantPal's own development history (85+ merged PRs, Phase 1 → Phase 10) lives in
> its git log and `.claude/STATE.md`/`TASK_PLAN.md` — this file tracks only the
> **platform-delta** work (D013), starting from Chunk 0.

## [Unreleased]

### Added
- Chunk 0 — platform Room bootstrap: `HEXAGON.md` and `DEPLOYMENT.md` added
  (PlantPal already carries `README.md`/`SECURITY.md`; this is the first real
  retrofit of the D013 convention onto a pre-existing, already-deployed app).
  `app-manifest.yaml` static registry record (`app.manifest` contract, routes
  drawn from the real `@RequestMapping` paths). `.claude/CLAUDE.md` gained a
  platform-integration notice prepended above PlantPal's own 516 lines of
  instructions (untouched).
  No application code changed — repo cloned as-is at `dev` (post PHASE10 merge)
  onto a new `platform-integration` branch.
