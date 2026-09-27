---
id: plantpal-20260927-d109-sonar-gate-enforcement
date: 2026-09-27
from: plantpal
to: [platform-vault]
capability: "Record the owner's ruling on D109 clause 5 (the quality gate's enforcement point): a GitHub required status check `sonar-gate`, run on ci-runner, blocks merges into dev — piloted by plantpal."
acceptance-criteria:
  - "A PLATFORM_DECISIONS entry amends D109 clause 5 naming the enforcement point (GitHub required check `sonar-gate` on ci-runner) and its scope (plantpal pilot, dev branch)"
  - "spec-sonarqube.md §8/§9 no longer states that no merge check consults the gate, for the piloted scope"
  - "The gate policy is recorded: new-code-only conditions; fixer = PR author (owner or agent); retry = push to the source branch"
  - "The fork-safety rule for self-hosted runners on public repos is recorded (job runs only for same-repo PR heads; fork workflows need owner approval)"
  - "The public-vs-private trade-off below is ruled or explicitly left open"
needs-owner: true
status: archived
---

# Demand — enforce the SonarQube quality gate on merges into `dev` (D109 clause 5)

## What we need

D109 clause 5 made the gate **report-only** and recorded its enforcement point
as *an outstanding owner ruling* (candidates: a `ci-runner` step, a GitHub merge
check, or D094's ship-clean rule). The owner has now asked for enforcement
(2026-09-27, plantpal session). Please record it:

- **Enforcement point:** a GitHub **required status check** named `sonar-gate`
  on the `dev` ruleset. The job runs on **`ci-runner`**'s self-hosted runner
  (the only runner that can reach the host-native server — verified from the
  runner container: `host.docker.internal:9000/api/system/status` → `UP`).
  The job scans with the repo's own scanner (D109 clause 1 unchanged) and uses
  `sonar.qualitygate.wait=true`, so a failing gate fails the check.
- **Scope:** plantpal as pilot, `dev` only; fleet rollout is a later ruling.
- **Policy:** the gate uses **new-code** conditions only (existing debt must not
  block every merge). The **PR author** — owner or agent — fixes on the source
  branch and pushes; the check re-runs; the merge is retried.
- **Unchanged:** the scan bridge (`:8094`) still never runs a scan and holds no
  enforcement role; CI never deploys.

## Why / what's blocked

- The owner wants every merge into `dev` (human- or agent-initiated) refused
  unless SonarQube passes, as a step toward automating the fleet.
- `ElasMoul/plants` already has a `dev` ruleset requiring `Backend CI` and
  `Frontend CI`; adding `sonar-gate` is one more required check — but under
  D109 as written, building it would be an unruled refusal, which the spec
  explicitly forbids a worker from adding on its own authority.

## Open points for the ruling

- **Public vs private.** The repo is public today (the owner made it public so
  Actions could run). Required checks via rulesets are free on public repos but
  need **GitHub Pro on private repos**. Self-hosted runner minutes are free
  either way. So: stay public → enforcement is GitHub-native; go private on
  Free → Actions still run on `ci-runner`, but nothing can *require* the check,
  and enforcement would fall back to a local merge wrapper (bypassable).
- **Fork safety while public:** a self-hosted runner on a public repo executes
  fork PR code on the owner's machine. Proposed rule: the `sonar-gate` job runs
  only when `github.event.pull_request.head.repo.full_name == github.repository`,
  and the repo requires owner approval for workflows from outside contributors.
- **Community Edition analyses one branch.** Either the mc1arke
  community-branch plugin (true PR analysis), or the gate scans the PR's merge
  ref into the single project. Owner's choice.

## What we do once closed

1. Add the `sonar-gate` job to plantpal's `ci.yml` (`runs-on: [self-hosted, platform]`),
   reusing `scripts/sonar-scan.ps1`'s scan logic with the gate wait on.
2. Settle the Sonar host: `backend/pom.xml` / `frontend/sonar-project.properties`
   say `localhost:9000`; `ci.yml` still carries 2026-09-24 SonarCloud steps.
3. Add `sonar-gate` to the `dev` ruleset's required checks (owner action).
4. Run the unmerged branches (`feature/PP-086-business-tier-declaration`,
   `codex/main-before-sync-20260923`) through the gate as the first real test.

Depends on `plantpal-20260927-ci-runner-plants-runner` (the runner must serve
`ElasMoul/plants` first).
