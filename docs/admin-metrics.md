# Admin operational metrics (PLA-34)

`GET /api/v1/admin/metrics?from=YYYY-MM-DD&to=YYYY-MM-DD` (admin only) and the
"Operational metrics" panel on the classic app's `/admin` overview. Both bounds are
optional and inclusive UTC days; the default is the last 30 days; the maximum range is
92 days (`400` otherwise, also when `to` precedes `from`). Read-only; no schema change.
Existing admin endpoints and views are unchanged.

## Definitions

| Metric | Definition | Source | Time basis | Caveats |
|---|---|---|---|---|
| Scans (`scans.total/completed/failed/pending`) | Identification attempts created in the range, split by pipeline status (`PENDING`+`PROCESSING` = pending). | `identifications` rows, `created_at` | UTC day, `[from 00:00, to+1 00:00)` | Counts rows, not billable requests. A scan still pending when read can later change status. |
| Errors (`errors.failedScans`) | Scans whose pipeline ended `FAILED`. | `identifications.status = 'FAILED'` | as Scans | Pipeline failures only. HTTP 5xx and log errors stay in runtime monitoring (e.g. Sentry); they are not counted here. |
| AI usage (`aiUsage.totalRequests`, `scanRequests`, `perDay`) | Accepted AI attempts (all kinds) and the scan-type subset, per day and summed. | `SUM(user_daily_usage.ai_requests / scans)` by `usage_date` | UTC date (`usage_date`) | Counts accepted attempts, including ones whose provider call failed; attempts rejected by a daily limit are not counted. `scanRequests` can differ from Scans because some accepted attempts never create an `identifications` row. The two are separate metrics and are not reconciled. |
| Cost (`cost.estimated`) | `totalRequests` × `app.admin.metrics.ai-cost-per-request`. | AI usage + configuration | as AI usage | **Estimate.** No token or billed-cost data is recorded anywhere, so a flat per-request rate is used. Rate unset or 0 returns `estimated: null` with basis `rate not configured` (shown as "—"). Differing provider/model prices are not modelled. |

## Access control

`/api/v1/admin/**` requires the `ADMIN` role in `SecurityConfig` (server-side); the
endpoint inherits it. `AdminMetricsIT` asserts `401` unauthenticated and `403` for a
`USER` token. No frontend-only check is relied on.

## Gaps found

| Gap | Status |
|---|---|
| No scan/error/AI-usage/cost view over an arbitrary range (overview showed fixed 30/14-day figures). | Closed: endpoint + panel. |
| Cost has no billing source (no token or cost columns). | Closed as an estimate only; real provider-billed cost is **not essential for V1** and needs new metering (out of scope). Owner to confirm. |
| Scans (`identifications`) and AI scan requests (`user_daily_usage`) differ by design. | Documented above, not reconciled; not essential. |
| Runtime HTTP/log errors are not in the admin views. | Not essential: stays in existing monitoring; "Errors" is defined as failed scans. |
| Dependency PLA-64 (reconciliation) outcome not re-checked against these definitions. | Open: owner to confirm. |

## Verification

* **Build-time (done):** `AdminMetricsIT` seeds identifications (completed, failed, pending,
  and rows just outside both range edges) and `user_daily_usage` rows for 2034-05-10..11, and
  asserts exactly scans 4/2/1/1, errors 1, AI requests 8 (scan-type 3), the per-day series
  and cost `null` / "rate not configured". `AdminMetricsServiceTest` asserts
  0.002 × 8 = 0.016 for a configured rate.
* **Live comparison on dev (open):** this delivery has no access to the dev database or an
  admin token, so the comparison with the underlying data is **not yet recorded**. To do on
  dev for one UTC range (e.g. 2026-10-01..2026-10-31):

  ```sql
  SELECT count(*), count(*) FILTER (WHERE status='COMPLETED'), count(*) FILTER (WHERE status='FAILED'),
         count(*) FILTER (WHERE status IN ('PENDING','PROCESSING'))
  FROM identifications WHERE created_at >= '2026-10-01' AND created_at < '2026-11-01';
  SELECT COALESCE(SUM(ai_requests),0), COALESCE(SUM(scans),0)
  FROM user_daily_usage WHERE usage_date BETWEEN '2026-10-01' AND '2026-10-31';
  ```

  Compare with the endpoint/panel and note the range, both values and any explained
  difference here. Also record the `curl` status codes: no token → 401, non-admin → 403,
  admin → 200.

## Review seed (dev profile)

`MetricsReviewSeeder`: `pla34-metrics-admin@plantpal.test` (ADMIN) and
`pla34-metrics-member@plantpal.test`, password `pla34-metrics-password-1`. The member has
4 scans (2 completed, 1 failed, 1 pending) and AI usage of 6 (today) + 4 (yesterday) = 10
requests with 4 scan-type. Set `app.admin.metrics.ai-cost-per-request` to see a cost.
