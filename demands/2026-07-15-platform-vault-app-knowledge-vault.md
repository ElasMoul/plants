---
id: plantpal-20260715-app-knowledge-vault-ruling
date: 2026-07-15
from: plantpal
to: [platform-vault]
capability: Rule on where a tenant app's own knowledge vault lives — plantpal's internal convention mandates a sibling folder (`../plants-vault`) that does not exist, and creating it would conflict with the "never create repos or folders outside your repo" standing order.
needs-owner: true
status: open
---

# Demand — ruling on per-app knowledge vaults (plants-vault doesn't exist)

## The problem (found 2026-07-15, during Phase 10 closure)

PlantPal's internal standing instructions (`.claude/CLAUDE.md`, predating the
platform integration) mandate a **vault sync as the last step of every phase**,
targeting a knowledge vault at `../plants-vault` (wiki with `hot.md`,
`index.md`, `phases/`, `decisions/`, `learnings/` — see
`.claude/VAULT_SYNC_TEMPLATE.md`).

That vault **does not exist anywhere on this machine**. Only `platform-vault`
exists at the platform root. Every historical "vault sync" commit in plantpal
(e.g. `6c6491e`, 2026-06-27) only ever touched in-repo `.claude/` files — the
external half of the checklist has silently never run.

PlantPal cannot resolve this itself:
- **Creating** `../plants-vault` (a new sibling folder at the platform root)
  is forbidden by the platform standing orders ("Never create repos or folders
  outside your repo") — sanctioning an exception is an owner/vault ruling.
- **Retiring** the convention is an in-repo edit plantpal *could* make alone,
  but the same question will recur for `pregnancy`, `tutor`, and every future
  tenant app that arrives with (or grows) its own agent-memory practice — a
  platform-wide convention is worth one findable paragraph.

## What we need from the vault (a ruling + a doc, not code)

Rule on and document ONE of:

1. **Sanction per-app knowledge vaults** — a tenant app may have an
   owner-created sibling folder (e.g. `<app>-vault`) for its own history;
   record the naming/placement convention and who creates it (owner at
   scaffold time vs. the app agent, sanctioned). Plantpal would then bootstrap
   `plants-vault` and backfill Phase 10 pages.
2. **Rule that app-internal history stays in-repo** — the app's `.claude/`
   (or equivalent) is the single memory layer; external per-app vaults are
   retired. Plantpal would then amend `.claude/CLAUDE.md` (Knowledge Vault
   section) and `VAULT_SYNC_TEMPLATE.md` to make the phase-end sync
   .claude-only — matching what has actually been happening all along.
3. Some other owner-preferred model (e.g. a governed subtree inside the app's
   own repo, mirroring D035's single-writer idea at app scale).

## Why / what's blocked

- Phase 10's mandated closure step ("Vault sync is the last step of every
  phase implementation — always, automatically") is unsatisfiable as written.
  The in-repo half is done (build table/state reconciled, commit `9a8667c`);
  the external half is blocked on this ruling.
- Non-blocking for Phase DEPLOY (code work proceeds normally) — this prevents
  every future phase-closure from tripping over the same dead pointer, and
  pre-decides the convention for the next tenant app.

## Acceptance criteria

- A decision entry (D-number) in `PLATFORM_DECISIONS.md` choosing a model, and
  a short findable convention paragraph (where app-level agent memory lives),
  so plantpal can act without interpretation.

## What we do once closed

- Option 1 → bootstrap the sanctioned vault and backfill Phase 10 pages
  (phase page, D10.1–D10.3 decisions, mic-contention +
  async-transactional-race learnings) from `.claude/` + git history.
- Option 2 → amend `.claude/CLAUDE.md` + `VAULT_SYNC_TEMPLATE.md` in-repo;
  no external writes.
- Either way: archive this demand file per the usual flow.
