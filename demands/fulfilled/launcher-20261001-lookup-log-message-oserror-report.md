---
demandId: launcher-20261001-lookup-log-message-oserror
worker: plantpal
date: 2026-10-01
status: done
shipped: ["tools/dev-delivery/dev_delivery.py: lookup_server Handler.log_message catches OSError", "tools/dev-delivery/test_dev_delivery.py: test_broken_stderr_does_not_break_the_response, test_healthy_stderr_logs_without_token_or_body"]
---

## What was found
Not previously shipped: `Handler.log_message` wrote to `sys.stderr` unguarded, so a broken stderr pipe raised out of the request handler.

## What changed
- `log_message` wraps the stderr write in `try/except OSError` (covers BrokenPipeError and closed streams); the log line format is unchanged.
- Test 1 starts the real lookup server (port 0) with `sys.stderr` replaced by a stream whose `write`/`flush` raise BrokenPipeError, sends a GET with no credentials, and asserts 403 `caller_not_authorized`.
- Test 2 (healthy stderr): the log line `lookup GET <path> -> 403` still reaches stderr and contains neither the bearer token nor a body.
- `python -m unittest test_dev_delivery` in tools/dev-delivery: 90 tests OK.

## Not verified / notes
- Committed on the branch that was checked out (`feature/PLA-93-species-photos`); not pushed. Land via a branch + PR into `dev` (ruleset-protected).
