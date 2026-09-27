---
id: plantpal-20260927-ci-runner-plants-runner
date: 2026-09-27
from: plantpal
to: [ci-runner]
capability: "A self-hosted runner registered to ElasMoul/plants (labels self-hosted, platform, linux) that can reach the host-native SonarQube server, so plantpal jobs can run on-box."
acceptance-criteria:
  - "`gh api repos/ElasMoul/plants/actions/runners` lists an online runner carrying the `platform` label"
  - "A job on that runner can reach SonarQube at host.docker.internal:9000 (GET /api/system/status returns UP)"
  - "The existing elmoul/conventions runner keeps working unchanged"
  - "DEPLOYMENT.md documents how to add a runner for another repo"
needs-owner: false
status: open
---

# Demand — register a `ci-runner` runner for `ElasMoul/plants`

## What we need

`ci-runner`'s single `runner` service is repo-scoped
(`RUNNER_SCOPE: repo`) to `GITHUB_REPO_URL=https://github.com/elmoul/conventions`.
`ElasMoul/plants` has **no** self-hosted runners (verified 2026-09-27 via
`gh api repos/ElasMoul/plants/actions/runners` → empty). A repo-scoped runner
serves one repo, and `plants` lives under a different account than
`conventions`, so it cannot share the existing registration.

Please add a second runner registration for `ElasMoul/plants` (e.g. a
`runner-plants` compose service with its own `REPO_URL`/token env), labels
`self-hosted,platform,linux`, with the same Docker-out-of-Docker workdir
pattern. Note: `WEBHOOK_REPOS` already includes `ElasMoul/plants` — that only
feeds `state.event`s; it does not execute jobs.

Two things must be true on the plants runner:
- It can reach the host-native SonarQube server. Already verified from the
  existing runner container: `host.docker.internal:9000/api/system/status` → `UP`.
- `plants` is a **public** repo. The runner should run only workflows from
  plantpal's own branches. plantpal's jobs will guard on a same-repo PR head.
  Please confirm the runner setup doesn't weaken that guard.

## Why / what's blocked

plantpal's `sonar-gate` (pending ruling `plantpal-20260927-d109-sonar-gate-enforcement`)
must run on-box, because GitHub-hosted runners cannot reach SonarQube.

## What we do once closed

Add `runs-on: [self-hosted, platform]` to plantpal's `sonar-gate` job. The job
only lands after the vault records the D109 ruling.
