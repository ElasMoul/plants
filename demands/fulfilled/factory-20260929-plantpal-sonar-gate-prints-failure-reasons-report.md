---
demandId: factory-20260929-plantpal-sonar-gate-prints-failure-reasons
worker: plantpal
date: 2026-09-30
status: done
shipped:
  - "scripts/sonar-explain-failure.mjs — reads the SonarQube read API (qualitygates/project_status, issues/search, measures/component) and prints failed conditions, new-code issues and files with uncovered new lines; the token is never printed"
  - ".github/workflows/ci.yml sonar-gate — 'Explain quality gate failure' step, if: failure() only, uses the job's existing SONAR_TOKEN, ends with exit 1 so it appears in `gh run view --log-failed`"
  - "frontend/sonar-project.properties sonar.coverage.exclusions mirrors Jest collectCoverageFrom excludes (backend pom.xml property already added by PLA-92, verified)"
  - "scripts/check-sonar-coverage-exclusions.mjs — run in Frontend CI; fails if either list drifts"
  - "docs/dev-delivery.md §4, next to the sonar-gate row"
summaryRef: "PR #206 (merged to dev, ffe73e8) + PR #208 (follow-up, open against dev); verification runs 36748524677 (fail) and 36748579596 (pass)"
---

# Fulfillment — sonar-gate prints failure reasons

## State check first

PLA-92 (PR #203, commit 25a1d23) had already added the backend
`sonar.coverage.exclusions` to `pom.xml`. Checked here: it matches the JaCoCo
`<excludes>` entry for entry, and the new drift check enforces that. The frontend
had no such property, and the explain step did not exist. This session built those
two pieces and the drift check.

## What shipped

1. **Explain step** (`ci.yml`, sonar-gate job). It runs only when the job has
   already failed (`if: failure()`). It calls `scripts/sonar-explain-failure.mjs`
   with the job's existing `SONAR_TOKEN`, sent in an Authorization header and never
   echoed. Errors from the script are redacted too. For each project whose gate is
   not OK, it prints:
   - each failed condition as `metric actual comparator threshold`, e.g.
     `new_coverage 71.4 < 80`
   - each open new-code issue as `severity rule file:line message`
   - each file with uncovered new lines and its counts, e.g.
     `…/ExifDateTakenReader.java uncovered 3 of 39 new lines to cover`

   The candidate files come from `git ls-files`, and each one is looked up with a
   per-file `measures/component` call. Two things forced this design: the
   self-hosted runner cannot reach github.com, so a base-ref fetch failed, and the
   analysis token is refused by the component-tree endpoints. The step ends with
   `exit 1` so its output is included in `gh run view --log-failed`, which omits
   passing steps. The job has already failed at that point, so the verdict does not
   change. On a passing gate the step is skipped.
2. **Coverage exclusions mirror.** Backend `sonar.coverage.exclusions` in
   `pom.xml` = the JaCoCo `<excludes>` (e.g. `**/config/**`). Frontend
   `sonar.coverage.exclusions` in `sonar-project.properties` = the `!` entries of
   Jest `collectCoverageFrom`. `scripts/check-sonar-coverage-exclusions.mjs` runs in
   Frontend CI and fails if either pair drifts.
3. **Docs.** `docs/dev-delivery.md` §4, next to the sonar-gate row.

## Verification

- **Failing run:** probe PR #207 (a deliberate TODO plus an untested class, not
  merged, since closed), run 36748524677. `gh run view --log-failed` shows:
  `new_coverage 71.4 < 80`, `new_violations 1 > 0`, `INFO java:S1135
  …/SonarFailureProbe.java:6 Complete the task associated to this TODO comment.`,
  and both uncovered files with their counts. `SONAR_TOKEN` appears only as `***`.
  No artifact is written.
- **Passing run:** PR #208, run 36748579596. sonar-gate succeeded, *Explain quality
  gate failure* was **skipped**, and the drift check printed
  `sonar.coverage.exclusions match the coverage tools (backend and frontend)`.

## Open

PR #208, the follow-up covering the offline file list and the `--log-failed`
visibility, is open against `dev` and green. It needs the owner's merge because
`dev` is ruleset-protected.
