"""Unit tests for dev_delivery.py — pure logic only (no docker, git or network).

Run: python -m unittest discover -s tools/dev-delivery -p "test_*.py"
Needs the contracts v0.38.0 Python binding and jsonschema (see
tools/dev-delivery/requirements.txt).
"""

import base64
import contextlib
import io
import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
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

    def _serve(self):
        server = dd.lookup_server(self.state, self.TOKEN, "127.0.0.1", 0)
        self.addCleanup(server.server_close)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(thread.join, 5)
        self.addCleanup(server.shutdown)
        return server.server_address[1]

    def _http_get(self, port, headers=None):
        req = urllib.request.Request(f"http://127.0.0.1:{port}/delivery/v1/app-deploys/{self.MISS}",
                                     headers=headers or {})
        try:
            with urllib.request.urlopen(req, timeout=10) as r:
                return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read())

    def test_broken_stderr_does_not_break_the_response(self):
        class BrokenStderr:
            def write(self, _s):
                raise BrokenPipeError("stderr pipe is gone")

            def flush(self):
                raise BrokenPipeError("stderr pipe is gone")

        port = self._serve()
        with mock.patch.object(dd.sys, "stderr", BrokenStderr()):
            status, body = self._http_get(port)
        self.assertEqual(403, status)
        self.assertEqual("caller_not_authorized", body["error"]["code"])

    def test_healthy_stderr_logs_without_token_or_body(self):
        port = self._serve()
        buf = io.StringIO()
        with mock.patch.object(dd.sys, "stderr", buf):
            self._http_get(port, {"Authorization": "Bearer secret-value"})
        out = buf.getvalue()
        self.assertIn("lookup GET /delivery/v1/app-deploys/", out)
        self.assertIn("-> 403", out)
        self.assertNotIn("secret-value", out)

    def test_token_is_generated_once_and_env_overrides(self):
        first = dd.lookup_token(self.state)
        self.assertEqual(first, dd.lookup_token(self.state))
        self.assertGreaterEqual(len(first), 32)
        with mock.patch.dict(dd.os.environ, {dd.LOOKUP_TOKEN_ENV: "from-env"}):
            self.assertEqual("from-env", dd.lookup_token(self.state))

    def test_serve_refuses_a_non_loopback_host(self):
        with self.assertRaises(dd.DeliveryError):
            dd.cmd_serve(mock.Mock(host="0.0.0.0", port=0))


class LookupServerLogging(unittest.TestCase):
    TOKEN = "t" * 40

    def _serve(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        server = dd.lookup_server(dd.state_dir(Path(tmp.name)), self.TOKEN, "127.0.0.1", 0)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(thread.join, 5)
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        return f"http://127.0.0.1:{server.server_address[1]}/delivery/v1/app-deploys/abc"

    def _status_and_body(self, url, headers=None):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=headers or {}), timeout=10) as r:
                return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read())

    def test_broken_stderr_does_not_break_the_response(self):
        class BrokenStderr:
            def write(self, _):
                raise BrokenPipeError("stderr pipe closed")

            def flush(self):
                raise BrokenPipeError("stderr pipe closed")

        url = self._serve()
        with mock.patch.object(dd.sys, "stderr", BrokenStderr()):
            status, body = self._status_and_body(url)
        self.assertEqual(403, status)
        self.assertEqual("caller_not_authorized", body["error"]["code"])

    def test_healthy_stderr_logs_one_line_without_token_or_body(self):
        url = self._serve()
        buf = io.StringIO()
        with mock.patch.object(dd.sys, "stderr", buf):
            self._status_and_body(url, {"Authorization": f"Bearer {self.TOKEN}"})
        self.assertIn("lookup GET /delivery/v1/app-deploys/abc -> ", buf.getvalue())
        self.assertNotIn(self.TOKEN, buf.getvalue())


class ReviewEnvironment(unittest.TestCase):
    """contracts v0.38.0 §Review environments: an unmerged PR head, role task, its own project and port."""

    REV_ID = "pla-rev-20260930120000-0123456789ab"
    PR = {"number": 77, "url": "https://github.com/ElasMoul/plants/pull/77"}

    def review_native(self, **overrides):
        r = dd.base_receipt(SHA, self.REV_ID, dd.REVIEW_PORT, {"deliveryId": None, "operationKey": "review-0123456789ab"},
                            "deploy", [], None)
        r.update(
            branch="feature/PLA-7-thing", environment="review", composeProject=dd.REVIEW_COMPOSE_PROJECT,
            revisionRole="task", review={"pullRequest": 77, "pullRequestUrl": self.PR["url"]},
            apiDocsUrl=f"http://127.0.0.1:{dd.REVIEW_PORT}/swagger-ui.html",
            result="passed", exitCode=0, imageDigests={"backend": DIGEST_BE, "frontend": DIGEST_FE},
            observed={"appIdentity": "plantpal", "revision": SHA, "deploymentId": self.REV_ID, "environment": "review"},
            observedAt="2026-09-30T12:03:30+00:00", finishedAt="2026-09-30T12:04:00+00:00",
            testUrl=f"http://127.0.0.1:{dd.REVIEW_PORT}", checks=[passed("identity:revision")])
        r.update(overrides)
        return r

    def test_review_receipt_is_task_role_with_review_block_and_docs_url(self):
        tagged = dd.to_tagged_receipt(self.review_native())
        self.assertEqual([], dd.validate_tagged_receipt(tagged))
        self.assertEqual(tagged["revisionRole"], "task")
        self.assertEqual(tagged["mergedRevision"], SHA)  # the PR head, not a merged revision
        self.assertEqual(tagged["environment"]["name"], "review")
        self.assertTrue(tagged["environment"]["apiDocsUrl"].endswith("/swagger-ui.html"))
        self.assertEqual(tagged["review"], {"pullRequest": 77, "pullRequestUrl": self.PR["url"]})
        self.assertIsNone(tagged["rollback"])

    def test_dev_receipt_is_unchanged_by_review_support(self):
        tagged = dd.to_tagged_receipt(native())
        for review_only in ("revisionRole", "review"):
            self.assertNotIn(review_only, tagged)
        self.assertNotIn("apiDocsUrl", tagged["environment"])

    def test_branch_only_review_keeps_nulls_not_defaults(self):
        tagged = dd.to_tagged_receipt(self.review_native(review={"pullRequest": None, "pullRequestUrl": None}))
        self.assertEqual([], dd.validate_tagged_receipt(tagged))
        self.assertEqual(tagged["review"], {"pullRequest": None, "pullRequestUrl": None})

    def test_review_serving_another_revision_is_refused_not_passed(self):
        observed = {"appIdentity": "plantpal", "revision": OTHER, "deploymentId": self.REV_ID, "environment": "review"}
        problems = dd.validate_tagged_receipt(dd.to_tagged_receipt(self.review_native(observed=observed)))
        self.assertTrue(any("mergedRevision" in x for x in problems), problems)

    def test_review_reporting_the_dev_environment_is_refused(self):
        observed = {"appIdentity": "plantpal", "revision": SHA, "deploymentId": self.REV_ID, "environment": "dev"}
        problems = dd.validate_tagged_receipt(dd.to_tagged_receipt(self.review_native(observed=observed)))
        self.assertTrue(any("environment" in x for x in problems), problems)

    def test_a_dev_receipt_carrying_review_fields_is_caught(self):
        tagged = dd.to_tagged_receipt(native())
        tagged["revisionRole"] = "task"
        self.assertTrue(any("dev receipt" in x for x in dd.check_deployment_semantics(tagged)))

    def test_identity_checks_compare_against_the_review_environment(self):
        observed = {"appIdentity": "plantpal", "revision": SHA, "deploymentId": self.REV_ID, "environment": "review"}
        self.assertTrue(all(c["outcome"] == "passed" for c in dd.identity_checks(observed, SHA, self.REV_ID, "review")))
        self.assertIn("failed", {c["outcome"] for c in dd.identity_checks(observed, SHA, self.REV_ID, "dev")})

    def test_review_checks_include_swagger(self):
        with mock.patch.object(dd, "observe_identity", return_value=None), \
                mock.patch.object(dd, "review_baseline_checks", return_value=[]), \
                mock.patch.object(dd, "smoke", side_effect=lambda *a, **k: {"name": a[1], "outcome": "passed"}):
            _, checks = dd.run_checks("http://x", SHA, self.REV_ID, [], "review")
            _, dev_checks = dd.run_checks("http://x", SHA, self.REV_ID, [], "dev")
        self.assertIn("smoke:swagger-ui", {c["name"] for c in checks})
        self.assertIn("smoke:api-docs", {c["name"] for c in checks})
        self.assertNotIn("smoke:swagger-ui", {c["name"] for c in dev_checks})

    def _baseline(self, api, http=(200, "")):
        with mock.patch.object(dd, "http_json", side_effect=api), mock.patch.object(dd, "http", return_value=http):
            return {c["name"]: c for c in dd.review_baseline_checks("http://x")}

    @staticmethod
    def _healthy_api(method, url, body=None, token=None, timeout=15.0):
        data = {"data": {"token": "t"}} if "/auth/" in url else {"data": {"content": []}}
        if url.endswith("/preferences"):
            avail = {"ANTHROPIC_CLAUDE": True}
            data = {"data": {"visionModelPreference": "ANTHROPIC_CLAUDE", "visionModelAvailability": avail,
                             "reasoningModelAvailability": avail}}
        return (201 if method == "POST" else 200), data

    def test_review_baseline_seeds_account_and_plant_and_observes_ai(self):
        checks = self._baseline(self._healthy_api)
        self.assertEqual({c["outcome"] for c in checks.values()}, {"passed"})
        self.assertEqual(set(checks), {"seed:test-account", "seed:baseline-plant", "smoke:identification-endpoint",
                                       "smoke:claude-enabled", "smoke:ai-gateway"})

    def test_review_baseline_logs_in_when_the_account_exists_and_does_not_duplicate_the_plant(self):
        calls = []

        def api(method, url, body=None, token=None, timeout=15.0):
            calls.append((method, url))
            if url.endswith("/auth/register"):
                return 400, {"message": "exists"}
            if url.endswith("/plants") and method == "GET":
                return 200, {"data": {"content": [{"nickname": dd.REVIEW_PLANT["nickname"]}]}}
            return self._healthy_api(method, url, body, token, timeout)
        checks = self._baseline(api)
        self.assertEqual(checks["seed:test-account"]["outcome"], "passed")
        self.assertNotIn(("POST", "http://x/api/v1/plants"), calls)

    def test_review_baseline_reports_claude_unselectable_and_gateway_down(self):
        def api(method, url, body=None, token=None, timeout=15.0):
            status, data = self._healthy_api(method, url, body, token, timeout)
            if url.endswith("/preferences"):
                data["data"]["visionModelAvailability"] = {"ANTHROPIC_CLAUDE": False}
            return status, data
        checks = self._baseline(api, http=(None, ""))
        self.assertEqual(checks["smoke:claude-enabled"]["outcome"], "failed")
        self.assertEqual(checks["smoke:ai-gateway"]["outcome"], "unavailable")

    def test_review_baseline_stops_at_the_login_when_the_app_is_down(self):
        checks = self._baseline(lambda *a, **k: (None, None))
        self.assertEqual(list(checks), ["seed:test-account"])
        self.assertEqual(checks["seed:test-account"]["outcome"], "unavailable")

    def test_review_runs_through_the_gateway_dev_does_not(self):
        with mock.patch.object(dd, "contracts_m2", return_value=Path("/m2")), \
                mock.patch.dict(dd.os.environ, {}, clear=False):
            dd.os.environ.pop("APP_SPRING_PROFILES", None)
            rev = dd.compose_env(Path("."), SHA, self.REV_ID, Path("e.env"), "pw", dd.REVIEW_PORT, "review")
            dev = dd.compose_env(Path("."), SHA, "pla-dev-x", Path("e.env"), "pw", dd.DEFAULT_PORT, "dev")
        self.assertEqual(rev["APP_SPRING_PROFILES"], "dev,platform")
        self.assertNotIn("APP_SPRING_PROFILES", dev)

    def test_review_never_shares_a_port_project_or_secret_with_dev(self):
        self.assertNotIn(dd.REVIEW_PORT, (dd.DEFAULT_PORT, dd.LOOKUP_PORT))
        self.assertNotEqual(dd.REVIEW_COMPOSE_PROJECT, dd.COMPOSE_PROJECT)
        self.assertNotEqual(dd.IMAGE_PREFIX["review"], dd.IMAGE_PREFIX["dev"])
        with tempfile.TemporaryDirectory() as tmp:
            state = Path(tmp)
            dev_env, dev_pw = dd.ensure_dev_config(state, "dev")
            rev_env, rev_pw = dd.ensure_dev_config(state, "review")
            self.assertNotEqual(dev_env, rev_env)
            self.assertNotEqual(dev_pw, rev_pw)
            self.assertNotEqual(dd.parse_env(dev_env.read_text())["JWT_SECRET"],
                                dd.parse_env(rev_env.read_text())["JWT_SECRET"])

    def test_review_receipts_are_never_dev_rollback_targets(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = dd.state_dir(Path(tmp))
            dd.save_receipt(state, self.review_native())
            self.assertIsNone(dd.last_passed(state))
            self.assertEqual(dd.dev_receipts(state), [])

    def test_compose_env_sets_environment_and_image_prefix(self):
        with mock.patch.object(dd, "contracts_m2", return_value=Path("/m2")):
            env = dd.compose_env(Path("."), SHA, self.REV_ID, Path("e.env"), "pw", dd.REVIEW_PORT, "review")
        self.assertEqual((env["APP_ENVIRONMENT"], env["IMAGE_PREFIX"], env["DEV_DELIVERY_PORT"]),
                         ("review", "plantpal-review", str(dd.REVIEW_PORT)))


class ReviewCommands(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        patcher = mock.patch.object(dd, "repo_root", return_value=self.root)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.state = dd.state_dir(self.root)
        self.helper = ReviewEnvironment()

    def _run(self, *argv):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = dd.main(list(argv))
        return code, out.getvalue(), err.getvalue()

    def _seed(self, **receipt_overrides):
        rec = self.helper.review_native(**receipt_overrides)
        dd.save_receipt(self.state, rec)
        dd.save_review_state(self.state, {
            "idempotencyKey": "k1", "deploymentId": rec["deploymentId"], "revision": SHA, "branch": rec["branch"],
            "pullRequest": ReviewEnvironment.PR, "port": dd.REVIEW_PORT, "status": "active",
            "startedAt": "2026-09-30T12:00:00+00:00"})
        return rec

    def test_status_of_a_passed_review_is_ready_with_the_tagged_receipt(self):
        self._seed()
        code, out, _ = self._run("review", "status", "--idempotency-key", "k1")
        doc = json.loads(out)
        self.assertEqual(code, 0)
        self.assertEqual((doc["status"], doc["expectedRevision"], doc["error"]), ("ready", SHA, None))
        self.assertEqual(doc["receipt"]["environment"]["name"], "review")
        self.assertEqual(doc["receipt"]["observed"]["revision"], SHA)

    def test_a_revision_mismatch_is_failed_never_ready(self):
        self._seed(result="failed", observed={"appIdentity": "plantpal", "revision": OTHER,
                                              "deploymentId": ReviewEnvironment.REV_ID, "environment": "review"})
        self.assertEqual(json.loads(self._run("review", "status")[1])["status"], "failed")

    def test_unknown_key_is_a_miss_exit_4(self):
        self._seed()
        code, out, err = self._run("review", "status", "--idempotency-key", "other")
        self.assertEqual((code, out, err.strip()), (4, "", "review_environment_not_found: other"))

    def test_stop_tears_down_and_is_idempotent(self):
        self._seed()
        calls = []
        ok = mock.Mock(returncode=0, stdout="", stderr="")
        with mock.patch.object(dd, "run", side_effect=lambda cmd, **k: calls.append(cmd) or ok):
            first = json.loads(self._run("review", "stop", "--idempotency-key", "k1")[1])
            second = json.loads(self._run("review", "stop", "--idempotency-key", "k1")[1])
        self.assertEqual((first["status"], second["status"]), ("stopped", "stopped"))
        self.assertEqual(first["receipt"]["result"], "passed")  # the receipt is kept, never rewritten
        self.assertTrue(all(c[:4] == ["docker", "compose", "-p", dd.REVIEW_COMPOSE_PROJECT] for c in calls))
        self.assertTrue(all("-v" in c for c in calls))

    def test_start_for_the_running_revision_is_idempotent(self):
        rec = self._seed()
        target = {"sha": SHA, "branch": rec["branch"], "pullRequest": ReviewEnvironment.PR}
        with mock.patch.object(dd, "resolve_review_target", return_value=target), \
                mock.patch.object(dd, "observe_identity", return_value=rec["observed"]), \
                mock.patch.object(dd, "review_gate") as gate, mock.patch.object(dd, "review_teardown") as down:
            code, out, _ = self._run("review", "start", "--pr", "77", "--idempotency-key", "k1")
        self.assertEqual(code, 0)
        self.assertEqual(json.loads(out)["deploymentId"], rec["deploymentId"])
        gate.assert_not_called()
        down.assert_not_called()

    def test_start_reads_launcher_parameters_from_the_environment(self):
        rec = self._seed()
        target = {"sha": SHA, "branch": rec["branch"], "pullRequest": ReviewEnvironment.PR}
        env = {"REVIEW_EXPECTED_REVISION": SHA, "REVIEW_IDEMPOTENCY_KEY": "k1",
               "REVIEW_PR_NUMBER": "77", "REVIEW_BRANCH": ""}
        with mock.patch.dict("os.environ", env),                 mock.patch.object(dd, "resolve_review_target", return_value=target) as resolve,                 mock.patch.object(dd, "observe_identity", return_value=rec["observed"]):
            code, _, _ = self._run("review", "start")
        self.assertEqual(code, 0)
        resolve.assert_called_once_with(self.root, 77, None, SHA)

    def test_an_explicit_flag_beats_the_environment(self):
        rec = self._seed()
        target = {"sha": SHA, "branch": rec["branch"], "pullRequest": ReviewEnvironment.PR}
        with mock.patch.dict("os.environ", {"REVIEW_PR_NUMBER": "5", "REVIEW_EXPECTED_REVISION": OTHER}),                 mock.patch.object(dd, "resolve_review_target", return_value=target) as resolve,                 mock.patch.object(dd, "observe_identity", return_value=rec["observed"]):
            self._run("review", "start", "--pr", "77", "--revision", SHA, "--idempotency-key", "k1")
        resolve.assert_called_once_with(self.root, 77, None, SHA)

    def test_stop_reads_the_key_from_the_environment(self):
        self._seed()
        ok = mock.Mock(returncode=0, stdout="", stderr="")
        with mock.patch.dict("os.environ", {"REVIEW_IDEMPOTENCY_KEY": "k1"}),                 mock.patch.object(dd, "run", return_value=ok):
            code, out, _ = self._run("review", "stop")
        self.assertEqual((code, json.loads(out)["status"]), (0, "stopped"))
        with mock.patch.dict("os.environ", {"REVIEW_IDEMPOTENCY_KEY": "nope"}):
            self.assertEqual(self._run("review", "stop")[0], 4)

    def test_stop_under_the_newer_key_stops_the_environment_a_reused_start_returned(self):
        rec = self._seed()
        target = {"sha": SHA, "branch": rec["branch"], "pullRequest": ReviewEnvironment.PR}
        with mock.patch.object(dd, "resolve_review_target", return_value=target),                 mock.patch.object(dd, "observe_identity", return_value=rec["observed"]):
            self.assertEqual(self._run("review", "start", "--pr", "77", "--idempotency-key", "k2")[0], 0)
        ok = mock.Mock(returncode=0, stdout="", stderr="")
        with mock.patch.object(dd, "run", return_value=ok) as run:
            code, out, _ = self._run("review", "stop", "--idempotency-key", "k2")
        self.assertEqual((code, json.loads(out)["status"]), (0, "stopped"))
        run.assert_called()

    def test_deploy_reads_the_revision_from_command_param_commit(self):
        seen = {}
        def fake_resolve(root, revision):
            seen["revision"] = revision
            raise dd.DeliveryError("stop here")
        with mock.patch.dict("os.environ", {"COMMAND_PARAM_COMMIT": SHA}),                 mock.patch.object(dd, "resolve_merged_revision", side_effect=fake_resolve):
            self._run("deploy")
            self.assertEqual(seen["revision"], SHA)
            self._run("deploy", "--revision", OTHER)
            self.assertEqual(seen["revision"], OTHER)

    def test_same_key_with_another_revision_is_a_conflict(self):
        self._seed()
        target = {"sha": OTHER, "branch": "feature/PLA-7-thing", "pullRequest": ReviewEnvironment.PR}
        with mock.patch.object(dd, "resolve_review_target", return_value=target):
            code, _, err = self._run("review", "start", "--pr", "77", "--idempotency-key", "k1")
        self.assertEqual(code, 3)
        self.assertIn("idempotency_key_conflict", err)

    def test_start_refuses_unless_every_check_passed_and_leaves_the_running_review_alone(self):
        self._seed()
        target = {"sha": OTHER, "branch": "feature/PLA-8", "pullRequest": None}
        gate = [{"name": "quality:sonar-gate", "outcome": "unknown"}]
        with mock.patch.object(dd, "resolve_review_target", return_value=target), \
                mock.patch.object(dd, "review_gate", return_value=gate), \
                mock.patch.object(dd, "review_teardown") as down:
            code, _, err = self._run("review", "start", "--branch", "feature/PLA-8")
        self.assertEqual(code, 2)
        self.assertIn("refused", err)
        down.assert_not_called()

    def test_start_refuses_the_dev_and_lookup_ports(self):
        for port in (dd.DEFAULT_PORT, dd.LOOKUP_PORT):
            self.assertEqual(self._run("review", "start", "--pr", "1", "--port", str(port))[0], 2)

    def test_target_needs_exactly_one_of_pr_or_branch(self):
        with self.assertRaises(dd.DeliveryError):
            dd.resolve_review_target(self.root, None, None, None)
        with self.assertRaises(dd.DeliveryError):
            dd.resolve_review_target(self.root, 1, "x", None)

    def _pr_info(self, ref="feature/PLA-7-thing"):
        info = {"sha": SHA, "ref": ref, "base": dd.BRANCH, "state": "open", "repo": dd.GITHUB_REPO, "url": "u"}
        return mock.Mock(returncode=0, stdout=json.dumps(info), stderr="")

    def test_env_with_pr_and_branch_starts_the_same_target_as_pr_alone(self):
        rec = self._seed()
        env = {"REVIEW_PR_NUMBER": "77", "REVIEW_BRANCH": rec["branch"], "REVIEW_IDEMPOTENCY_KEY": "k1"}
        target = {"sha": SHA, "branch": rec["branch"], "pullRequest": ReviewEnvironment.PR}
        with mock.patch.dict("os.environ", env), \
                mock.patch.object(dd, "resolve_review_target", return_value=target) as resolve, \
                mock.patch.object(dd, "observe_identity", return_value=rec["observed"]):
            code, _, _ = self._run("review", "start")
        self.assertEqual(code, 0)
        resolve.assert_called_once_with(self.root, 77, None, None, expected_branch=rec["branch"])

    def test_pr_resolution_accepts_the_expected_branch_and_refuses_a_mismatch(self):
        with mock.patch.object(dd, "run", return_value=self._pr_info()):
            self.assertEqual(dd.resolve_review_target(self.root, 77, None, None, "feature/PLA-7-thing")["sha"], SHA)
            with self.assertRaisesRegex(dd.DeliveryError, "head branch is feature/PLA-7-thing, not the expected other"):
                dd.resolve_review_target(self.root, 77, None, None, "other")

    def test_env_branch_mismatch_is_refused_from_the_command(self):
        with mock.patch.dict("os.environ", {"REVIEW_PR_NUMBER": "77", "REVIEW_BRANCH": "other"}), \
                mock.patch.object(dd, "run", return_value=self._pr_info()):
            code, _, err = self._run("review", "start")
        self.assertEqual(code, 2)
        self.assertIn("not the expected other", err)

    def test_both_flags_on_the_command_line_stay_refused(self):
        with mock.patch.dict("os.environ", {}, clear=False):
            code, _, err = self._run("review", "start", "--pr", "77", "--branch", "feature/x")
        self.assertEqual(code, 2)
        self.assertIn("exactly one of --pr or --branch", err)

    def test_main_and_dev_branches_are_never_reviewed(self):
        for branch in ("main", "dev"):
            with mock.patch.object(dd, "run", return_value=mock.Mock(returncode=0, stdout=SHA + "\n", stderr="")):
                with self.assertRaises(dd.DeliveryError):
                    dd.resolve_review_target(self.root, None, branch, None)


if __name__ == "__main__":
    unittest.main()
