# Public-page performance budget (PLA-123)

Public pages are the first impression and feed search results, so they carry a budget measured on an
emulated mid-range phone.

| Metric | Budget |
|---|---|
| Largest Contentful Paint (LCP) | <= 2.5 s |
| Cumulative Layout Shift (CLS) | <= 0.1 |
| Interaction to Next Paint (INP) | <= 200 ms |

**Source of truth:** `frontend/perf/budget.json` (thresholds, device profile, runs, page list). This
document explains it; if they disagree, the JSON wins.

**Device profile:** Chromium, 412x823 viewport at 2.625 DPR, touch + Android mobile user agent,
4x CPU slowdown, Slow 4G (1.6 Mbps down, 150 ms RTT), applied through the Chrome DevTools Protocol.
Each page is loaded in a fresh browser context, 5 times; the **median** of each metric is compared with
the budget.

## Re-run (one command)

```bash
cd frontend
npm run perf:public            # build:prod, serve dist/plantpal locally, measure, exit 1 on a miss
PERF_BASE_URL=https://<dev-host> node perf/measure-public.mjs   # measure a deployed instance, no build
```

Needs Playwright's Chromium (`npx playwright install chromium` if missing). No extra dependency.
The run prints a table and writes `frontend/perf/last-run.json` (git-ignored).

Pages: homepage (`/{lang}/`) and FAQ (`/{lang}/faq`) for every language in `budget.json`
(`en`, `fr`, the prerendered public languages in `public-routes.ts`).

## Limits

- **There is no FAQ page yet** (no route, no prerender, nothing in the sitemap). It is listed in
  `budget.json` as `optional`: a 404 is reported as "skipped". Once a FAQ page ships at another path,
  or is renamed, update `path` and its `interaction` selectors there; it is then measured and enforced.
- **INP is a lab proxy**: the worst event-timing duration after one scripted tap (the hero call to
  action on the homepage, a toggle on the FAQ), not field INP. Navigation is suppressed so the page
  stays alive while the event is recorded.
- Lab numbers vary by machine. The figures below are from a 32-core Windows dev machine; a value close
  to a threshold can flip between runs.

## Recorded results

Run 2026-10-24, commit `714580c` (dev) plus the perf tooling, local serve of the production build.

| Page | Lang | LCP ms | CLS | INP ms | Result | LCP element |
|---|---|---|---|---|---|---|
| home | en | 2028 | 0.0057 | 40 | within budget | `p.hero-subline` |
| home | fr | 2024 | 0.0694 | 56 | within budget | `h1.hero-title` |
| faq | en, fr | - | - | - | not measured: page does not exist yet | - |

No metric exceeded its budget on the first run, so no code fix was needed. Watch item: French CLS
(0.069) is 70% of the budget, against 0.006 in English.

Deployed-instance numbers (CDN, compression) are to be added after dev delivery with
`PERF_BASE_URL`; the local serve has no compression and no latency beyond the emulated network.

Misses awaiting owner acceptance: none.
