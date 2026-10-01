---
demandId: factory-20261001-launcher-review-command-interface
worker: plantpal
date: 2026-10-01
status: done
shipped:
  - "tools/dev-delivery/dev_delivery.py: `review start`/`review stop`/`review status` read REVIEW_EXPECTED_REVISION, REVIEW_IDEMPOTENCY_KEY, REVIEW_PR_NUMBER, REVIEW_BRANCH from the environment when the matching flag is absent; `deploy` reads COMMAND_PARAM_COMMIT as the revision; an explicit flag wins; empty env values count as absent"
  - "Stop fix: a start that reuses the running environment under a new idempotency key records it as an alias, so `review stop` under the newer key stops the running environment instead of exit 4 review_environment_not_found (unknown keys still exit 4)"
  - "tools/dev-delivery/test_dev_delivery.py: 5 new tests (env params, flag-beats-env, stop via env, stop under alias key, deploy via COMMAND_PARAM_COMMIT); suite 97 pass"
  - "DEPLOYMENT.md: exact plain-argv entries for review-apps.json start/stop and command-allowlist.json deploy; CHANGELOG [Unreleased] entry"
---

# Report: launcher review command interface (plantpal side)

## State check
Not previously shipped: no environment-variable parameter reads existed and `review stop` matched only the key the environment was first started under.

## Verified
- `python -m unittest discover -s tools/dev-delivery` : 97 tests pass.
- Receipt already prints as indented multi-line JSON, which launcher's last-JSON-document parsing (criterion 1) must accept.

## Not done / not verified (outside this repo or needs the owner)
- Criterion 1 (launcher reads the last complete JSON document on stdout, with tests) is a launcher change; I did not touch that repo.
- Criterion 5 (live run on one real PR: launcher starts, settles `ready`, stops; owner replaces the inline wrappers) was not run. No docker/launcher/PR run happened here; everything above is unit-tested with mocked docker/gh only.
- The `<venv python>` path in the DEPLOYMENT.md entries is a placeholder for the owner's interpreter.
