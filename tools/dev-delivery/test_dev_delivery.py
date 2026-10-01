"""Unit tests for dev_delivery.py — pure logic only (no docker, git or network).

Run: python -m unittest discover -s tools/dev-delivery -p "test_*.py"
Needs the contracts v0.36.0 Python binding and jsonschema (see
tools/dev-delivery/requirements.txt).
"""

import base64
import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import dev_delivery as dd

SHA = "0123456789abcdef0123456789abcdef01234567"
OTHER = "fedcba9876543210fedcba9876543210fedcba98"
DEP = "pla-dev-20260927120000-0123456789ab"


def receipt(**overrides):
    r = dd.base_receipt(SHA, DEP, 8184, {"deliveryId": None, "operationKey": None}, "deploy", [], None)
    r.update(overrides)
    return r


def passed(name):
    return {"name": name, "criterionId": None, "outcome": "passed", "exitCode": None}


class IdentityChecks(unittest.TestCase):
    def test_matching_identity_passes(self):
        observed = {"appIdentity": "plantpal", "revision": SHA, "deploymentId": DEP, "environment": "dev"}
        self.assertTrue(all(c["outcome"] == "passed" for c in dd.identity_checks(observed, SHA, DEP)))

    def test_revision_mismatch_is_failed_not_passed(self):
        observed = {"appIdentity": "plantpal", "revision": OTHER, "deploymentId": DEP, "environment": "dev"}
        outcomes = {c["name"]: c["outcome"] for c in dd.identity_checks(observed, SHA, DEP)}
        self.assertEqual(outcomes["identity:revision"], "failed")

    def test_unreported_revision_stays_unknown(self):
        observed = {"appIdentity": "plantpal", "revision": None, "deploymentId": DEP, "environment": "dev"}
        outcomes = {c["name"]: c["outcome"] for c in dd.identity_checks(observed, SHA, DEP)}
        self.assertEqual(outcomes["identity:revision"], "unknown")

    def test_unreachable_app_is_all_unknown(self):
        self.assertEqual({c["outcome"] for c in dd.identity_checks(None, SHA, DEP)}, {"unknown"})


class Overall(unittest.TestCase):
    def test_all_passed(self):
        self.assertEqual(dd.overall(0, [passed("a"), passed("b")]), "passed")

    def test_unknown_never_becomes_passed(self):
        checks = [passed("a"), {"name": "b", "outcome": "unknown"}]
        self.assertEqual(dd.overall(0, checks), "unknown")

    def test_failed_check_fails(self):
        self.assertEqual(dd.overall(0, [passed("a"), {"name": "b", "outcome": "failed"}]), "failed")

    def test_nonzero_exit_fails(self):
        self.assertEqual(dd.overall(1, [passed("a")]), "failed")

    def test_missing_exit_is_unknown(self):
        self.assertEqual(dd.overall(None, [passed("a")]), "unknown")

    def test_unverified_planotell_route_does_not_gate_the_deployment(self):
        route = {"name": "route:planotell", "outcome": "unavailable"}
        self.assertEqual(dd.overall(0, [passed("a"), route]), "passed")


class CheckOutcome(unittest.TestCase):
    def test_no_runs_is_unknown(self):
        self.assertEqual(dd.check_outcome([], True), "unknown")

    def test_in_progress_is_pending(self):
        self.assertEqual(dd.check_outcome([None], False), "pending")

    def test_skipped_is_not_passed(self):
        self.assertEqual(dd.check_outcome(["skipped"], True), "unknown")

    def test_skipped_twin_of_a_real_run_is_ignored(self):
        self.assertEqual(dd.check_outcome(["skipped", "success"], True), "passed")

    def test_neutral_is_not_passed(self):
        self.assertEqual(dd.check_outcome(["neutral"], True), "unknown")

    def test_any_failure_fails(self):
        self.assertEqual(dd.check_outcome(["success", "failure"], True), "failed")


class ProducerResult(unittest.TestCase):
    def test_passed_receipt_maps_with_observed_environment(self):
        r = receipt(result="passed", exitCode=0, observedAt="2026-09-27T12:00:00+00:00",
                    imageDigests={"backend": "sha256:" + "a" * 64, "frontend": "sha256:" + "b" * 64},
                    observed={"appIdentity": "plantpal", "revision": SHA, "deploymentId": DEP, "environment": "dev"},
                    checks=[passed("identity:revision")])
        result = dd.to_producer_result(r)
        self.assertEqual(result["producer"], "app-deploy")
        self.assertEqual(result["operationId"], DEP)
        self.assertEqual(result["nativeRef"], f"plantpal:deployments/{DEP}")
        self.assertEqual(result["environment"]["deployedRevision"], SHA)
        self.assertEqual(result["environment"]["name"], "dev")
        self.assertEqual(result["artifactRef"], "sha256:" + "a" * 64)

    def test_unobserved_revision_gives_null_environment_not_the_configured_one(self):
        r = receipt(result="unknown", observed={"appIdentity": "plantpal", "revision": None,
                                                 "deploymentId": DEP, "environment": "dev"})
        result = dd.to_producer_result(r)
        self.assertIsNone(result["environment"])
        self.assertIsNone(result["exitCode"])
        self.assertEqual(result["outcome"], "unknown")

    def test_pending_receipt_maps_to_pending(self):
        self.assertEqual(dd.to_producer_result(receipt())["outcome"], "pending")


class DevConfig(unittest.TestCase):
    def test_production_keys_are_refused(self):
        with self.assertRaises(dd.DeliveryError):
            dd.validate_dev_env({"JWT_SECRET": "x", "DATABASE_URL": "jdbc:..."})

    def test_unlisted_keys_are_refused(self):
        with self.assertRaises(dd.DeliveryError):
            dd.validate_dev_env({"SOMETHING_ELSE": "x"})

    def test_generated_vapid_pair_is_a_valid_p256_point(self):
        pub, priv = dd.vapid_keypair()
        raw_pub = base64.urlsafe_b64decode(pub + "=" * (-len(pub) % 4))
        raw_priv = base64.urlsafe_b64decode(priv + "=" * (-len(priv) % 4))
        self.assertEqual(len(raw_pub), 65)
        self.assertEqual(raw_pub[0], 4)
        self.assertEqual(len(raw_priv), 32)
        point = (int.from_bytes(raw_pub[1:33], "big"), int.from_bytes(raw_pub[33:], "big"))
        self.assertTrue(dd.on_p256(point))
        self.assertEqual(dd._p256_mul(int.from_bytes(raw_priv, "big"), dd.P256_G), point)


class Criterion(unittest.TestCase):
    def test_parses_full_spec(self):
        c = dd.parse_criterion("AC-1=GET:/api/v1/species:401")
        self.assertEqual((c["criterionId"], c["method"], c["path"], c["status"], c["contains"]),
                         ("AC-1", "GET", "/api/v1/species", 401, None))

    def test_parses_contains(self):
        self.assertEqual(dd.parse_criterion("home=GET:/:200:<app-root")["contains"], "<app-root")

    def test_rejects_garbage(self):
        with self.assertRaises(dd.DeliveryError):
            dd.parse_criterion("nope")


class ReceiptIds(unittest.TestCase):
    def test_rejects_path_traversal(self):
        with self.assertRaises(dd.DeliveryError):
            dd.receipt_path(dd.Path("."), "../../etc/passwd")


# ── contracts v0.36.0: tagged receipt + running-app identity ──────────────────
# The GOOD_*/BAD_* documents below are copied verbatim from contracts @ v0.36.0
# tests/validate_delivery.py. Keeping them here means a shape drift surfaces as a
# failure in this repo rather than at a consumer that already bound to the tag.

REV_MERGED = "2" * 40
REV_TASK = "1" * 40
DEPLOY_ID = "pla-dev-20260927120000-222222222222"
DEPLOY_PREV = "pla-dev-20260926090000-111111111111"
DEPLOY_OLDER = "pla-dev-20260925080000-333333333333"
DIGEST_BE = "sha256:" + "e" * 64
DIGEST_FE = "sha256:" + "f" * 64
DELIVERY = "3c9c9b2a-2c8b-4a8b-9b3d-2f7e5b6c1a10"

GOOD_IDENTITY = {"appIdentity": "plantpal", "revision": REV_MERGED, "deploymentId": DEPLOY_ID, "environment": "dev"}
# A developer's local run: nothing reported but the name. Valid, and verifies nothing.
GOOD_IDENTITY_UNREPORTED = {"appIdentity": "plantpal", "revision": None, "deploymentId": None, "environment": None}
BAD_IDENTITY_SHORT_SHA = {**GOOD_IDENTITY, "revision": "2222222"}
BAD_IDENTITY_NO_APP = {**GOOD_IDENTITY, "appIdentity": None}
BAD_IDENTITY_OMITS_REVISION = {k: v for k, v in GOOD_IDENTITY.items() if k != "revision"}  # absent != null
BAD_IDENTITY_EXTRA = {**GOOD_IDENTITY, "buildTime": "2026-09-27T10:00:00Z"}

GOOD_RECEIPT_PASSED = {
    "deploymentId": DEPLOY_ID,
    "kind": "deploy",
    "repository": "plantpal",
    "branch": "dev",
    "mergedRevision": REV_MERGED,
    "imageDigests": {"backend": DIGEST_BE, "frontend": DIGEST_FE},
    "digestKind": "local-image-id",
    "result": "passed",
    "exitCode": 0,
    "startedAt": "2026-09-27T12:00:00Z",
    "finishedAt": "2026-09-27T12:04:00Z",
    "observedAt": "2026-09-27T12:03:30Z",
    "environment": {"name": "dev", "url": "http://planotell.platform.localhost"},
    "observed": GOOD_IDENTITY,
    "checks": [
        {"name": "ci:Backend CI", "criterionId": None, "outcome": "passed", "exitCode": None},
        {"name": "identity:revision", "criterionId": None, "outcome": "passed", "exitCode": None},
        {"name": "smoke:backend-health", "criterionId": None, "outcome": "passed", "exitCode": None},
        {"name": "criterion:AC-1", "criterionId": "AC-1", "outcome": "passed", "exitCode": None},
    ],
    "correlation": {"deliveryId": DELIVERY, "operationKey": f"{DELIVERY}:deploy:1"},
    "rollback": {"deploymentId": DEPLOY_PREV, "revision": REV_TASK,
                 "imageDigests": {"backend": "sha256:" + "0" * 64, "frontend": "sha256:" + "9" * 64}},
    "rollbackOf": None,
    "restores": None,
    "nativeRef": f"plantpal:deployments/{DEPLOY_ID}",
}

# Reserved, not yet settled: first deployment ever, so no rollback identity exists.
GOOD_RECEIPT_PENDING_FIRST = {
    **GOOD_RECEIPT_PASSED,
    "imageDigests": {"backend": None, "frontend": None},
    "result": "pending",
    "exitCode": None,
    "finishedAt": None,
    "observedAt": None,
    "environment": {"name": "dev", "url": "http://127.0.0.1:8184"},
    "observed": None,
    "checks": [],
    "correlation": {"deliveryId": None, "operationKey": None},
    "rollback": None,
}

GOOD_RECEIPT_ROLLBACK = {
    **GOOD_RECEIPT_PASSED,
    "deploymentId": "pla-dev-20260927130000-111111111111",
    "kind": "rollback",
    "mergedRevision": REV_TASK,
    "imageDigests": GOOD_RECEIPT_PASSED["rollback"]["imageDigests"],
    "observed": {**GOOD_IDENTITY, "revision": REV_TASK, "deploymentId": "pla-dev-20260927130000-111111111111"},
    "rollback": {"deploymentId": DEPLOY_ID, "revision": REV_MERGED, "imageDigests": GOOD_RECEIPT_PASSED["imageDigests"]},
    "rollbackOf": DEPLOY_ID,
    "restores": DEPLOY_PREV,
    "nativeRef": "plantpal:deployments/pla-dev-20260927130000-111111111111",
}

BAD_RECEIPT_PROD = {**GOOD_RECEIPT_PASSED, "environment": {"name": "prod", "url": "https://planotell.example"}}
BAD_RECEIPT_PASSED_NOT_OBSERVED = {**GOOD_RECEIPT_PASSED, "observed": None}
BAD_RECEIPT_PASSED_DIGEST_MISSING = {**GOOD_RECEIPT_PASSED, "imageDigests": {"backend": DIGEST_BE, "frontend": None}}
BAD_RECEIPT_NO_COMPONENTS = {**GOOD_RECEIPT_PASSED, "imageDigests": {}}
BAD_RECEIPT_TAG_AS_DIGEST = {**GOOD_RECEIPT_PASSED, "imageDigests": {"backend": "plantpal-backend:latest", "frontend": DIGEST_FE}}
BAD_RECEIPT_NATIVE_FIELDS = {**GOOD_RECEIPT_PASSED, "schema": "plantpal.dev-deployment-receipt/1"}  # closed shape

# Semantically wrong but schema-valid: exactly what the ported rules exist to catch.
PASSED_SERVING_PREVIOUS_REVISION = {**GOOD_RECEIPT_PASSED, "observed": {**GOOD_IDENTITY, "revision": REV_TASK}}
PASSED_SERVING_ANOTHER_DEPLOYMENT = {**GOOD_RECEIPT_PASSED, "observed": {**GOOD_IDENTITY, "deploymentId": DEPLOY_PREV}}
PASSED_WITH_UNREPORTED_REVISION = {**GOOD_RECEIPT_PASSED, "observed": {**GOOD_IDENTITY, "revision": None}}
ROLLBACK_IDENTITY_NAMES_ITSELF = {
    **GOOD_RECEIPT_PASSED,
    "rollback": {**GOOD_RECEIPT_PASSED["rollback"], "deploymentId": DEPLOY_ID},
}
NATIVEREF_NAMES_ANOTHER_DEPLOYMENT = {**GOOD_RECEIPT_PASSED, "nativeRef": f"plantpal:deployments/{DEPLOY_PREV}"}


def native(**overrides):
    """A native receipt as settle() leaves it, for mapping through to the tagged shape."""
    r = receipt(
        result="passed", exitCode=0,
        imageDigests={"backend": DIGEST_BE, "frontend": DIGEST_FE},
        observed={"appIdentity": "plantpal", "revision": SHA, "deploymentId": DEP, "environment": "dev"},
        observedAt="2026-09-27T12:03:30+00:00", finishedAt="2026-09-27T12:04:00+00:00",
        testUrl="http://planotell.platform.localhost",
        checks=[passed("identity:revision")],
        rollback={"deploymentId": DEPLOY_PREV, "revision": OTHER,
                  "imageDigests": {"backend": "sha256:" + "0" * 64, "frontend": "sha256:" + "9" * 64}},
    )
    r.update(overrides)
    return r


class TaggedReceipt(unittest.TestCase):
    def test_passed_receipt_maps_and_validates(self):
        tagged = dd.to_tagged_receipt(native())
        self.assertEqual([], dd.validate_tagged_receipt(tagged))
        self.assertEqual(tagged["mergedRevision"], SHA)          # deployed revision
        self.assertEqual(tagged["observed"]["revision"], SHA)     # served revision
        self.assertEqual(tagged["nativeRef"], f"plantpal:deployments/{DEP}")
        self.assertEqual(tagged["environment"], {"name": "dev", "url": "http://planotell.platform.localhost"})

    def test_digest_kind_is_the_tagged_enum_not_the_native_prose(self):
        tagged = dd.to_tagged_receipt(native())
        self.assertEqual(tagged["digestKind"], "local-image-id")

    def test_native_only_fields_do_not_leak_into_the_closed_shape(self):
        tagged = dd.to_tagged_receipt(native())
        for native_only in ("schema", "revisionRole", "composeProject", "port", "preDeployChecks", "log"):
            self.assertNotIn(native_only, tagged)
        self.assertEqual(set(tagged["checks"][0]), {"name", "criterionId", "outcome", "exitCode"})

    def test_deploy_receipt_carries_explicit_null_back_links(self):
        tagged = dd.to_tagged_receipt(native())
        self.assertIsNone(tagged["rollbackOf"])
        self.assertIsNone(tagged["restores"])
        self.assertIsNotNone(tagged["rollback"])  # the earlier deployment a rollback would restore

    def test_pending_first_deployment_is_valid_and_has_no_rollback_identity(self):
        tagged = dd.to_tagged_receipt(native(result="pending", exitCode=None, finishedAt=None, observedAt=None,
                                            observed=None, imageDigests={"backend": None, "frontend": None},
                                            checks=[], rollback=None))
        self.assertEqual([], dd.validate_tagged_receipt(tagged))
        self.assertIsNone(tagged["rollback"])

    def test_rollback_receipt_keeps_its_links(self):
        # rollbackOf = the deployment that was current (rolled back); restores = whose images restarted.
        tagged = dd.to_tagged_receipt(native(kind="rollback", rollbackOf=DEPLOY_PREV, restores=DEPLOY_OLDER))
        self.assertEqual([], dd.validate_tagged_receipt(tagged))
        self.assertEqual(tagged["rollbackOf"], DEPLOY_PREV)
        self.assertEqual(tagged["restores"], DEPLOY_OLDER)

    def test_a_rollback_receipt_naming_itself_is_refused(self):
        tagged = dd.to_tagged_receipt(native(kind="rollback", rollbackOf=DEP, restores=DEPLOY_OLDER))
        self.assertTrue(any("never the one it restores" in p for p in dd.validate_tagged_receipt(tagged)))

    def test_a_passed_receipt_serving_another_revision_is_refused(self):
        tagged = dd.to_tagged_receipt(native(observed={"appIdentity": "plantpal", "revision": OTHER,
                                                       "deploymentId": DEP, "environment": "dev"}))
        self.assertTrue(dd.validate_tagged_receipt(tagged))

    def test_a_leaked_native_field_is_refused(self):
        tagged = dd.to_tagged_receipt(native())
        tagged["schema"] = "plantpal.dev-deployment-receipt/1"
        self.assertTrue(any("schema" in p for p in dd.validate_tagged_receipt(tagged)))


class DeploymentSemantics(unittest.TestCase):
    def test_contracts_good_receipts_have_no_semantic_problems(self):
        for fixture in (GOOD_RECEIPT_PASSED, GOOD_RECEIPT_PENDING_FIRST, GOOD_RECEIPT_ROLLBACK):
            self.assertEqual([], dd.check_deployment_semantics(fixture))

    def test_a_passed_receipt_that_serves_another_revision_is_caught(self):
        problems = dd.check_deployment_semantics(PASSED_SERVING_PREVIOUS_REVISION)
        self.assertTrue(any("mergedRevision" in p for p in problems))

    def test_a_passed_receipt_that_serves_another_deployment_is_caught(self):
        problems = dd.check_deployment_semantics(PASSED_SERVING_ANOTHER_DEPLOYMENT)
        self.assertTrue(any("deploymentId" in p for p in problems))

    def test_a_passed_receipt_with_unreported_revision_is_caught(self):
        self.assertTrue(dd.check_deployment_semantics(PASSED_WITH_UNREPORTED_REVISION))

    def test_rollback_identity_naming_itself_is_caught(self):
        problems = dd.check_deployment_semantics(ROLLBACK_IDENTITY_NAMES_ITSELF)
        self.assertTrue(any("EARLIER" in p for p in problems))

    def test_native_ref_naming_another_deployment_is_caught(self):
        problems = dd.check_deployment_semantics(NATIVEREF_NAMES_ANOTHER_DEPLOYMENT)
        self.assertTrue(any("nativeRef" in p for p in problems))

    def test_the_schema_alone_would_not_catch_a_wrong_revision(self):
        """Why the port exists: the mismatch is schema-valid, so only these rules see it."""
        self.assertEqual([], dd.schema_problems(PASSED_SERVING_PREVIOUS_REVISION))
        self.assertTrue(dd.check_deployment_semantics(PASSED_SERVING_PREVIOUS_REVISION))


class ContractsFixturesRoundTrip(unittest.TestCase):
    def test_contracts_good_receipts_validate(self):
        for name, fixture in (("passed", GOOD_RECEIPT_PASSED), ("pending-first", GOOD_RECEIPT_PENDING_FIRST),
                              ("rollback", GOOD_RECEIPT_ROLLBACK)):
            with self.subTest(name):
                self.assertEqual([], dd.validate_tagged_receipt(fixture))

    def test_contracts_bad_receipts_are_rejected(self):
        for name, fixture in (("prod-environment", BAD_RECEIPT_PROD),
                              ("passed-not-observed", BAD_RECEIPT_PASSED_NOT_OBSERVED),
                              ("passed-digest-missing", BAD_RECEIPT_PASSED_DIGEST_MISSING),
                              ("no-components", BAD_RECEIPT_NO_COMPONENTS),
                              ("tag-as-digest", BAD_RECEIPT_TAG_AS_DIGEST),
                              ("native-fields", BAD_RECEIPT_NATIVE_FIELDS)):
            with self.subTest(name):
                self.assertTrue(dd.validate_tagged_receipt(fixture))

    def test_good_identities_round_trip_through_the_binding(self):
        from platform_contracts.app.deployment_identity import AppDeploymentIdentity
        for name, fixture in (("reported", GOOD_IDENTITY), ("unreported", GOOD_IDENTITY_UNREPORTED)):
            with self.subTest(name):
                self.assertEqual(fixture, AppDeploymentIdentity.model_validate(fixture).model_dump(mode="json"))

    def test_unreported_identity_keeps_nulls_it_does_not_default_them(self):
        from platform_contracts.app.deployment_identity import AppDeploymentIdentity
        round_tripped = AppDeploymentIdentity.model_validate(GOOD_IDENTITY_UNREPORTED).model_dump(mode="json")
        self.assertIsNone(round_tripped["revision"])
        self.assertIsNone(round_tripped["deploymentId"])
        self.assertIsNone(round_tripped["environment"])
        self.assertEqual("plantpal", round_tripped["appIdentity"])

    def test_bad_identities_are_rejected(self):
        from pydantic import ValidationError
        from platform_contracts.app.deployment_identity import AppDeploymentIdentity
        for name, fixture in (("short-sha", BAD_IDENTITY_SHORT_SHA), ("no-app", BAD_IDENTITY_NO_APP),
                              ("omits-revision", BAD_IDENTITY_OMITS_REVISION), ("extra-field", BAD_IDENTITY_EXTRA)):
            with self.subTest(name):
                with self.assertRaises(ValidationError):
                    AppDeploymentIdentity.model_validate(fixture)


class ActuatorIdentity(unittest.TestCase):
    """The /actuator/info `deployment` block IS app/deployment-identity: round-trip it as served."""

    def test_the_deployment_block_round_trips_as_served(self):
        from platform_contracts.app.deployment_identity import AppDeploymentIdentity
        served = {"deployment": {"appIdentity": "plantpal", "revision": REV_MERGED,
                                 "deploymentId": DEPLOY_ID, "environment": "dev"}}
        block = dd.observe_identity_block(json.dumps(served))
        self.assertEqual(GOOD_IDENTITY, AppDeploymentIdentity.model_validate(block).model_dump(mode="json"))

    def test_an_unkeyed_local_run_round_trips_with_nulls_not_defaults(self):
        from platform_contracts.app.deployment_identity import AppDeploymentIdentity
        served = {"deployment": {"appIdentity": "plantpal", "revision": None,
                                 "deploymentId": None, "environment": None}}
        block = dd.observe_identity_block(json.dumps(served))
        self.assertEqual(GOOD_IDENTITY_UNREPORTED, AppDeploymentIdentity.model_validate(block).model_dump(mode="json"))

    def test_unreported_fields_are_unknown_and_never_match(self):
        """null means not reported, so those checks never match — but the name is still verified."""
        outcomes = {c["name"]: c["outcome"] for c in dd.identity_checks(dict(GOOD_IDENTITY_UNREPORTED), REV_MERGED, DEPLOY_ID)}
        self.assertEqual("passed", outcomes["identity:app"])  # the one field an identity always carries
        for name in ("identity:revision", "identity:deployment", "identity:environment"):
            self.assertEqual("unknown", outcomes[name])

    def test_a_missing_deployment_block_is_not_an_identity(self):
        self.assertIsNone(dd.observe_identity_block('{"app": {"name": "plantpal"}}'))
        self.assertIsNone(dd.observe_identity_block("not json"))


class LookupTransport(unittest.TestCase):
    """Both transports: exit 0 + exactly one JSON document, or exit 4 + the miss line on stderr."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        patcher = mock.patch.object(dd, "repo_root", return_value=root)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.state = dd.state_dir(root)

    def _run(self, *argv):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = dd.main(list(argv))
        return code, out.getvalue(), err.getvalue()

    def test_a_miss_is_exit_4_with_nothing_on_stdout(self):
        for command in ("lookup", "receipt"):
            with self.subTest(command):
                code, out, err = self._run(command, "pla-dev-20260101000000-ffffffffffff")
                self.assertEqual(4, code)
                self.assertEqual("", out)
                self.assertEqual("deployment_not_found: pla-dev-20260101000000-ffffffffffff", err.strip())

    def test_lookup_prints_one_producer_result_document(self):
        dd.save_receipt(self.state, native())
        code, out, err = self._run("lookup", DEP)
        self.assertEqual(0, code)
        self.assertEqual("", err)
        document = json.loads(out)  # exactly one document, or this raises
        self.assertEqual("app-deploy", document["producer"])
        self.assertEqual(DEP, document["operationId"])
        self.assertEqual(f"plantpal:deployments/{DEP}", document["nativeRef"])
        self.assertEqual(SHA, document["revision"])

    def test_receipt_prints_the_tagged_receipt_document(self):
        dd.save_receipt(self.state, native())
        code, out, err = self._run("receipt", DEP)
        self.assertEqual(0, code)
        self.assertEqual("", err)
        document = json.loads(out)
        self.assertEqual(SHA, document["mergedRevision"])
        self.assertEqual("local-image-id", document["digestKind"])
        self.assertEqual(DEPLOY_PREV, document["rollback"]["deploymentId"])

    def test_both_transports_agree_on_the_environment_url(self):
        dd.save_receipt(self.state, native())
        _, receipt_out, _ = self._run("receipt", DEP)
        _, lookup_out, _ = self._run("lookup", DEP)
        self.assertEqual(json.loads(lookup_out)["environment"]["url"],
                         json.loads(receipt_out)["environment"]["url"])

    def test_receipt_refuses_a_receipt_that_breaks_the_contract(self):
        dd.save_receipt(self.state, native(observed={"appIdentity": "plantpal", "revision": OTHER,
                                                     "deploymentId": DEP, "environment": "dev"}))
        code, out, err = self._run("receipt", DEP)
        self.assertEqual(2, code)
        self.assertEqual("", out)  # never a near-miss document
        self.assertIn("mergedRevision", err)


class HttpLookupRoute(unittest.TestCase):
    """contracts v0.37.0 app-deploy-lookup.openapi.yaml: envelope, status table, auth first."""

    TOKEN = "test-caller-token"
    AUTH = f"Bearer {TOKEN}"
    MISS = "pla-dev-20260101000000-ffffffffffff"

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.state = dd.state_dir(Path(self.tmp.name))

    def _get(self, path, auth=AUTH):
        return dd.lookup_response(self.state, self.TOKEN, path, auth)

    def test_result_route_serves_the_cli_lookup_document(self):
        dd.save_receipt(self.state, native())
        status, body = self._get(f"/delivery/v1/app-deploys/{DEP}")
        self.assertEqual(200, status)
        self.assertEqual(dd.producer_result_document(self.state, DEP), body["data"])
        self.assertEqual("app-deploy", body["data"]["producer"])

    def test_receipt_route_serves_the_cli_receipt_document_verbatim(self):
        dd.save_receipt(self.state, native())
        status, body = self._get(f"/delivery/v1/app-deploys/{DEP}/receipt")
        self.assertEqual(200, status)
        self.assertEqual(dd.tagged_receipt_document(self.state, DEP), body["data"])
        self.assertEqual(DEPLOY_PREV, body["data"]["rollback"]["deploymentId"])

    def test_a_pending_receipt_is_a_200_not_a_miss(self):
        dd.save_receipt(self.state, receipt())
        status, body = self._get(f"/delivery/v1/app-deploys/{DEP}")
        self.assertEqual(200, status)
        self.assertEqual("pending", body["data"]["outcome"])

    def test_a_miss_is_the_only_404_and_carries_the_published_body(self):
        for suffix in ("", "/receipt"):
            with self.subTest(suffix):
                status, body = self._get(f"/delivery/v1/app-deploys/{self.MISS}{suffix}")
                self.assertEqual(404, status)
                self.assertEqual({"code": "deployment_not_found", "retryable": False},
                                 {k: body["error"][k] for k in ("code", "retryable")})

    def test_unauthenticated_is_403_never_404(self):
        for auth in (None, "", "Bearer wrong", f"Basic {self.TOKEN}", self.TOKEN):
            with self.subTest(auth=auth):
                status, body = self._get(f"/delivery/v1/app-deploys/{self.MISS}", auth)
                self.assertEqual(403, status)
                self.assertEqual("caller_not_authorized", body["error"]["code"])

    def test_malformed_id_is_422(self):
        for bad in ("", "a%2F..%2Fb", "x" * 129, "a b"):
            with self.subTest(bad=bad):
                status, body = self._get(f"/delivery/v1/app-deploys/{bad}")
                self.assertEqual(422, status)
                self.assertEqual("invalid_request", body["error"]["code"])

    def test_unreadable_store_is_503_retryable(self):
        (self.state / "receipts" / f"{DEP}.json").write_text("{not json", encoding="utf-8")
        status, body = self._get(f"/delivery/v1/app-deploys/{DEP}")
        self.assertEqual(503, status)
        self.assertEqual({"code": "producer_unavailable", "retryable": True},
                         {k: body["error"][k] for k in ("code", "retryable")})

    def test_a_receipt_that_breaks_the_contract_is_never_served(self):
        dd.save_receipt(self.state, native(observed={"appIdentity": "plantpal", "revision": OTHER,
                                                     "deploymentId": DEP, "environment": "dev"}))
        status, body = self._get(f"/delivery/v1/app-deploys/{DEP}/receipt")
        self.assertEqual(503, status)
        self.assertNotIn("data", body)

    def test_every_error_body_is_a_delivery_error(self):
        for status, body in (self._get("/x", None), self._get("/delivery/v1/app-deploys/a b"),
                             self._get(f"/delivery/v1/app-deploys/{self.MISS}")):
            self.assertEqual({"code", "message", "retryable"}, set(body["error"]))

    def test_token_is_generated_once_and_env_overrides(self):
        first = dd.lookup_token(self.state)
        self.assertEqual(first, dd.lookup_token(self.state))
        self.assertGreaterEqual(len(first), 32)
        with mock.patch.dict(dd.os.environ, {dd.LOOKUP_TOKEN_ENV: "from-env"}):
            self.assertEqual("from-env", dd.lookup_token(self.state))

    def test_serve_refuses_a_non_loopback_host(self):
        with self.assertRaises(dd.DeliveryError):
            dd.cmd_serve(mock.Mock(host="0.0.0.0", port=0))


if __name__ == "__main__":
    unittest.main()
