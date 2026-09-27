---
demandId: contracts-20260928-plantpal-repin-app-deploy-receipt-and-identity
worker: plantpal
date: 2026-09-28
status: done
shipped:
  - "tools/dev-delivery/requirements.txt repinned to platform-contracts v0.36.0"
  - "tools/dev-delivery/dev_delivery.py receipt <id> emits delivery.deployment-receipt, validated on emit"
  - "tools/dev-delivery/dev_delivery.py lookup <id> emits delivery.producer-result via the App-deploy mapping"
  - "lookup transport per §App-deploy — exit 0 = exactly one JSON document; miss = exit 4"
  - "tools/dev-delivery/schemas/ — 3 schemas vendored verbatim from v0.36.0, used only to validate"
  - "cross-field rules ported from check_deployment_semantics, not left to the schema"
  - "tools/dev-delivery/test_dev_delivery.py — 56 tests, 30 of them new"
  - ".github/workflows/ci.yml — new Dev Delivery Tool CI job"
  - "docs/dev-delivery.md — tagged shapes, transports, validation and tests"
  - "3 real D113 receipts round-tripped through the tagged shapes, incl. a kind rollback"
  - "demand raised to factory — consume the tagged app-deploy receipt"
  - "demand raised to runtime — consume the tagged running-app identity"
summaryRef: "branch feature/PP-115-contracts-v036-app-deploy-repin (not pushed to main; see Not done)"
---

# Fulfillment — app-deploy receipt + running-app identity: consuming leg closed

## What shipped

plantpal's producing leg now emits the **v0.36.0** tagged shapes. The on-disk
record is still native (`plantpal.dev-deployment-receipt/1`); both lookup
transports were moved to the tag, which is what the demand asked for.

**The repin.** `tools/dev-delivery/requirements.txt` moves
`platform-contracts` from v0.31.0 to **v0.36.0** (tagged git URL), plus
`jsonschema>=4.20`. Verified by installing from that exact pinned URL into a
**clean venv** and running the suite green there — so the file is complete on
its own, not just working in the venv that happened to be lying around.

**`receipt <id>` now emits `delivery.deployment-receipt`**, the full tagged
receipt including the rollback identity that `delivery.producer-result`
deliberately does not carry. The native → tagged mapping is the one written in
`contracts` §Upgrade / repin v0.36.0: `revision`→`mergedRevision`,
`testUrl`→`environment.url` with `environment` becoming `{name, url}`,
`digestKind`→`local-image-id` (the native field held prose), `rollback`/
`rollbackOf`/`restores` carried over with explicit `null` back-links for
`kind: deploy`, `nativeRef` = `plantpal:deployments/<id>`. The native-only
fields (`schema`, `revisionRole`, `composeProject`, `port`, `preDeployChecks`,
`log`) and the native per-check `detail`/`prHead` stay in the file and are
dropped from the closed tagged shape.

**`lookup <id>` keeps emitting `delivery.producer-result`**, now built *through*
the tagged receipt by the executable §App-deploy mapping
(`producer_result_from_tagged`), so there is one mapping rather than two that
can drift. `environment` is still built only from `observed`, never from the
receipt's own intentions.

**Lookup transport, exactly as §App-deploy names it.** Exit `0` with exactly one
JSON document on stdout; a miss is exit `4`, stderr exactly
`deployment_not_found: <id>`, nothing on stdout. The miss message is printed
bare (no `dev-delivery:` prefix) because Factory machine-reads that line.

**Validation on emit — all three checks, because each catches what the others
miss.** `receipt` refuses rather than emitting:

1. the generated **binding** (`DeliveryDeploymentReceipt`);
2. the **JSON Schema itself**, vendored verbatim at v0.36.0 into
   `tools/dev-delivery/schemas/` (with a README naming provenance and the repin
   procedure) so it works offline, in CI, and with no `contracts` checkout;
3. the **cross-field rules**, ported from `contracts` `tests/validate_delivery.py`
   `check_deployment_semantics`: `nativeRef` names this deployment, a rollback
   identity names an *earlier* deployment, a rollback is never the deployment it
   restores, and a `passed` receipt has `observed.revision == mergedRevision`,
   `observed.deploymentId == deploymentId` and the observed environment.

Point 3 is the one that matters most and is worth stating plainly: **the schema
accepts a `passed` receipt whose URL serves a different revision.** Draft
2020-12 has no keyword for field equality, and that equality is the entire claim
a receipt exists to prove. There is a test that pins exactly this — the mismatch
is schema-valid and only the ported rules catch it. Validating against the
binding alone would have been worse still, since the binding does not implement
the schema's `if`/`then` at all.

**Evidence beyond fixtures.** The three **real** receipts from the D113 dev
run — two `kind: deploy` (one with rollback identity) and one genuine
`kind: rollback` — all emit a valid closed tagged document through the actual
CLI, with `digestKind: local-image-id`, `environment` derived from `testUrl`,
and the native-only fields and check fields correctly dropped. Both transports
also agree on `environment.url` for the same deployment: `receipt` prints the
binding-normalized document rather than the raw mapping, because otherwise the
two transports disagreed on an incidental trailing slash for the same field.

**Tests.** 56 (was 26; 30 new). They cover the tagged mapping for passed /
pending-first / rollback, the closed shape, the identity round-trip through
`AppDeploymentIdentity` including `null` staying *unreported*, the contracts
`GOOD_*` fixtures round-tripping and the `BAD_*` fixtures being rejected, the
cross-field rules, and both transports' exit codes and stderr. The `GOOD_*`/
`BAD_*` documents are copied verbatim from `contracts` `tests/validate_delivery.py`
so a shape drift shows up here rather than at a consumer.

**CI.** A new **Dev Delivery Tool CI** job runs the suite on every push and PR.
`contracts` is a public repository, so the pinned install needs no token; the
job uses a venv because the runner's python is externally managed (PEP 668).
Without this the gate would only run when an operator remembered to.

## What the origin must know

- **Nothing further is needed from `contracts`** — no interface gap surfaced.
  Both new shapes fit the producer as-is, and `delivery.producer-result` output
  is unchanged in meaning across the repin. No Java binding wanted: the Spring
  backend serves the identity block with no contracts dependency, and the
  identity is a block plantpal *serves*, not a document it constructs from types.
- **The tag to consume is v0.36.0.** Both transports emit it; see the two demands
  raised to `factory` and `runtime` below.
- **A stale venv will fail loudly, not silently.** A venv still holding the
  v0.31.0 binding cannot emit `receipt` (no `DeliveryDeploymentReceipt` class),
  and a venv missing `jsonschema` raises rather than skipping schema validation.
  That is deliberate: a silently skipped schema check would be worse than no check.
- **Vendored schemas are a validation oracle, not types.** No plantpal code
  imports them. The pydantic binding remains the only thing that shapes a
  document. The vendored copies are byte-identical to the v0.36.0 tag and are
  refreshed only as part of a repin commit.

## Not done / caveats

1. **Nothing was pushed, and `main` was not touched.** A push to `main` in this
   repo deploys production (`.github/workflows/deploy.yml`), so the work sits on
   `feature/PP-115-contracts-v036-app-deploy-repin` for PR into `dev`. This also
   means the demand-system doorbell push for the two raised demands did not ring
   — they are on disk, which is what the coordinator reads, but the owner should
   know the raises rode a branch rather than `main`. The coordination-publication
   ruling (prerequisites table, `docs/dev-delivery.md` §5) is still with the owner.
2. **The new CI job is not a required check.** It reports on every push and PR but
   is not in the `dev` ruleset, which is owner-managed — so it does not gate a
   merge until the owner adds it. Flagging rather than changing repo settings.
3. **`receipt` is stricter than the old tool about `correlation.deliveryId`.** The
   tag requires UUID format, so a deploy invoked with a non-UUID
   `--delivery-id` now produces a receipt that `receipt <id>` refuses. This is the
   contract, but it is a new refusal and worth knowing before an operator hits it.
4. **`route:planotell` was `unavailable` on all three real receipts**, so their
   `environment.url` is the loopback URL, not the hostname — correctly, since a
   configured hostname is only reported when the identity observed through it
   matched. Closing that is `runtime`'s prerequisite (hostname → `127.0.0.1:8184`),
   not plantpal's.
5. **No Java-side test was added.** The contributor that serves the block already
   has null-semantics tests, and the tagged round-trip criterion is necessarily
   Python (there is no Java binding). The one producer-side gap left open:
   nothing in the backend asserts that the published `deployment` map's key set is
   *exactly* the tagged four, so a future extra key would surface in the Python CI
   job rather than in Backend CI.
6. **The `deploy`/`rollback`/`reconcile` paths do not run the schema validation.**
   They print `delivery.producer-result` through the same mapping but keep their
   current robustness: a validation failure there would turn a successful deploy
   into a non-zero exit *after* the stack came up, so the hard gate sits on
   `receipt`, which is the transport the demand names. If the owner wants the
   stricter behaviour on `deploy` too, that is a small follow-up and a deliberate
   choice to make rather than a bug.
