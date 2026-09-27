"""Unit tests for dev_delivery.py — pure logic only (no docker, git or network).

Run: python -m unittest discover -s tools/dev-delivery -p "test_*.py"
Needs the contracts v0.31.0 Python binding (see tools/dev-delivery/requirements.txt).
"""

import base64
import unittest

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


if __name__ == "__main__":
    unittest.main()
