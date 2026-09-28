---
id: plantpal-20260927-contracts-ci-run-steps
date: 2026-09-27
from: plantpal
to: [contracts]
capability: "An additive, optional `steps[]` (plus `jobId`) on the `ci.run` state.event's CiRunPayload, so a CI job's per-step progress can travel to the dashboard as a Jenkins-style stage view."
acceptance-criteria:
  - "state.event.json CiRunPayload gains optional `jobId` (int64) and optional `steps` (array of {number, name, status: queued|in_progress|completed, conclusion?: success|failure|cancelled|skipped, startedAt?, completedAt?}) with additionalProperties false on the step object"
  - "state-event-java.yaml and the generated TS/Java/Python bindings carry the same fields"
  - "An existing ci.run event without jobId/steps still validates (purely additive, minor release)"
  - "The release tag is named in the fulfillment report"
needs-owner: false
status: archived
---

# Demand — optional `steps[]` on `ci.run` (CI stage view, layer 2)

## What we need

`CiRunPayload` in `schemas/state-feed/state.event.json` is
`additionalProperties: false`. It carries only
`runId, repo, ref, workflow, jobName, phase, conclusion, startedAt, completedAt, runnerLabels`.
A producer therefore cannot send step detail until the schema allows it.

Please add two optional fields, in a minor release:
- `jobId`: int64. This is GitHub's `workflow_job.id`. `ci-runner` needs it to
  poll the job, and it gives a stable key per job, since `runId` is shared by all
  jobs in a workflow run.
- `steps`: an ordered array of step objects, mirroring GitHub's
  `workflow_job.steps[]`:
  - `number`: integer;
  - `name`: string;
  - `status`: `queued`, `in_progress` or `completed`;
  - `conclusion` (optional): `success`, `failure`, `cancelled` or `skipped`;
  - `startedAt` and `completedAt` (optional): date-time.

Consumers that ignore the new fields must be unaffected.

## Why / what's blocked

The owner wants a Jenkins-like visual of the SonarQube merge gate (D112's
`sonar-gate` job) and of every CI job running. The dashboard's `/ci` ship wall
already shows job-level phase and result from `ci.run`. The step-level stage
view needs this field, then `ci-runner` to send it
(`plantpal-20260927-ci-runner-ci-run-steps`), then `dashboard` to render it
(`plantpal-20260927-dashboard-ci-stage-view`). Both of those wait on this release.

## What we do once closed

Nothing in plantpal: it neither produces nor consumes `ci.run`. The follow-on
demands to `ci-runner` and `dashboard` pick up the tag from the fulfillment
report.
