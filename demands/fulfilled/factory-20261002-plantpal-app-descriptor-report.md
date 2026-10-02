---
demandId: factory-20261002-plantpal-app-descriptor
worker: plantpal
date: 2026-10-02
status: done
shipped:
  - "app.yaml at the repo root (app.descriptor/1) carrying every value the demand lists; no prior app.yaml existed, so nothing was already shipped"
  - "DEPLOYMENT.md: new 'App descriptor (app.yaml)' section - layout is in-repo now, becomes wrapped when the hexagon moves to the elmoul org (owner ruling 2026-10-02), code stays in ElasMoul/plants"
---

## Verification done
- Schema: app.yaml parsed (launcher's own YAML parser) and validated with jsonschema Draft 2020-12 against `../contracts-worktrees/v0.43.0/schemas/app/descriptor.json` -> valid. No contracts repin.
- Commands are plain argv arrays through `tools/dev-delivery/.venv/Scripts/python.exe` (repo-relative, so it passes the launcher's inside-the-hexagon rule). `dev_delivery.py` already reads REVIEW_* / COMMAND_PARAM_COMMIT from the environment (factory-20261001-launcher-review-command-interface), so no tool change was needed. `startTimeoutMs: 1800000` = review-apps.json.
- Launcher derivation (`descriptors.describe()`/`load()` run locally against the repo): plantpal available for review and deploy (`deploy-plantpal`), `problems: []`.

## Derived vs hand-kept (not deleted)
| field | derived from app.yaml | hand entry | same? |
|---|---|---|---|
| review cwd | repo root | repo root | yes |
| review hostname | planotell | planotell | yes |
| review startTimeoutMs | 1800000 | 1800000 | yes |
| deploy cwd / timeoutMs / enabled / parameters | repo root / 1800000 / true / {commit} | same | yes |
| review start argv | `[venv python, dev_delivery.py, review, start]` | `[venv python, -c, <env->flags wrapper>]` | semantics equal (tool reads the env itself); text differs |
| review stop / deploy argv | `... review stop` / `... deploy` | `-c` wrappers passing flags | semantics equal; text differs |
| program path | relative `tools/dev-delivery/.venv/...` | absolute | resolves to the same interpreter via cwd |

Residual difference: the hand start wrapper re-serialises the receipt to one line; the derived command prints the tool's indented JSON. The launcher's receipt parsing was not exercised end to end here.

## NOT verified (environment, not code)
- The running launcher on :5055 predates the `GET /api/apps` route (returns 404 Not found) and was not restarted (owner's process), so the live comparison is local-module only.
- control-plane :8081 did not answer, so `GET /registry` (app object, no descriptorError) was not checked. Needs a scan after control-plane is up.
- No review/deploy was run; nothing in app behaviour, CI or tools/dev-delivery changed.
