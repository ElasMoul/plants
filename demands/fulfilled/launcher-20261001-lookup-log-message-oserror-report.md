---
demandId: launcher-20261001-lookup-log-message-oserror
worker: plantpal
status: done
date: 2026-10-01
shipped:
  - "tools/dev-delivery/dev_delivery.py: lookup_server's Handler.log_message now catches OSError (BrokenPipeError, closed stream) around the stderr write and never lets it propagate; the request is still answered. Healthy stderr is unchanged: one line `lookup <METHOD> <path-without-query> -> <status>`, no bearer token, no body"
  - "tools/dev-delivery/test_dev_delivery.py: new LookupServerLogging tests. test_broken_stderr_does_not_break_the_response starts the real lookup server with sys.stderr replaced by a stream whose write raises BrokenPipeError, sends a GET without credentials and asserts 403 caller_not_authorized. test_healthy_stderr_logs_one_line_without_token_or_body covers the unchanged path"
---

# Report: lookup server survives a broken stderr pipe

## State check
Not previously shipped: `log_message` wrote to `sys.stderr` unguarded.

## Verified
- `python -m unittest test_dev_delivery` in tools/dev-delivery: 90 tests pass.
- With the fix stashed, the new broken-stderr test errors, so it exercises the fix.

## Not verified
- Committed on branch `feature/PLA-lookup-log-message-oserror`; not pushed or merged (dev is ruleset-protected, lands via PR).
