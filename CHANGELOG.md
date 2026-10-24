# Changelog — plantpal

> PlantPal's own development history (85+ merged PRs, Phase 1 → Phase 10) lives in
> its git log and `.claude/STATE.md`/`TASK_PLAN.md` — this file tracks only the
> **platform-delta** work (D013), starting from Chunk 0.

## [Unreleased] (opened 2026-07-04)

### Added
- Secret scanning is enforced: the `Detect secrets` job now also runs on pull requests into `dev` and `epic/**` (it already ran on every push and on pull requests into `main`), and is a required check in `app.yaml` `delivery.requiredChecks`. The PLA-120 e2e test web-push key finding (commit 5f7dcd9) is allowlisted by exact fingerprint in `.gitleaksignore`. Docs: `DEPLOYMENT.md` "Secret scanning". The matching GitHub branch rule is the owner's step.
- Units and local time (PLA-132): a metric/imperial setting on the classic preferences page (migration 048 adds nullable `users.unit_system` and `users.time_zone`; no backfill). Without a choice the default is imperial for region US, LR or MM, otherwise metric (`effectiveUnitSystem` in the preferences response). Stored data stays metric; the classic app converts temperatures, lengths and volumes in AI care advice at display time (`aiText`/`aiDetail`) and has a `unit` pipe for numeric values. Dates and numbers use `Intl` with the browser locale (the active /fr/ locale in the French build) and the user's time zone (`localeDate`/`localeNumber` pipes). The browser time zone is stored once after login; invalid IANA ids are rejected with a 400. Reminders and treatment-plan steps now add calendar days in the user's zone, so a reminder keeps its local time across a daylight-saving change (null zone = UTC, as before); existing `nextDueAt` values correct themselves when next completed. Atlas is unchanged. Dev profile only: `UnitsReviewSeeder` creates `units-us-pla132@plantpal.test` and `units-eu-pla132@plantpal.test` (password `units-password-1`).
- Language policy (PLA-126): English is the default and French the only other language, defined once per side (`SUPPORTED_LANGUAGES` in the frontend, `SupportedLanguages` in `com.plantpal.localization`). The browser language is ignored; a visitor's choice stays on the device, a signed-in user's profile language wins at login, and a missing or empty French string falls back to its English text. Chat, translation prompts, emails (already EN/FR) and the daily reminder push follow the user's language and default to English. Arabic is removed: stored `ar` values are shown English (no migration), and `ar` is now rejected on register/preferences. No schema change.
- Plant journal events (PLA-111): record repotting, pruning, flowering, fruiting and propagation on a plant, each with a date and an optional note (max 1000 characters). `GET`/`POST /api/v1/plants/{plantId}/journal-events` (paged, oldest first) and `PUT`/`DELETE /api/v1/plants/{plantId}/journal-events/{eventId}` (update is a full replace; delete is a hard delete, like plant groups). Everything is scoped to the signed-in user's own plant and event (404 otherwise, so existence is not revealed). Migration 046 adds the `plant_journal_events` table (additive, no backfill). Classic app: the plant's Scans tab merges events with photos by date (newest first), with an "Add event" button and edit/delete on each event; en/fr/ar strings. Dev profile only: `JournalReviewSeeder` creates `journal-owner@plantpal.test` (a plant with one event of each type) and `journal-other@plantpal.test` (password `journal-password-1`). Atlas is unchanged.
- Transactional email (PLA-25): one `MailService` that verification (PLA-26) and recovery (PLA-65) both call. Emails are queued in a new `email_outbox` table (migration 047) in the caller's transaction and sent by a scheduled dispatcher over SMTP (Resend in staging/prod; `app.mail.transport=log` in dev and tests delivers nothing). EN and FR templates (text and HTML; other languages fall back to EN). A failed send is retried with exponential backoff up to `app.mail.max-attempts`; after that the row is FAILED, an ERROR is logged (Sentry), the `plantpal.mail.delivery{outcome}` counter is incremented and `GET /api/v1/admin/email-deliveries?status=FAILED` lists it (admin only). Staging and prod now require `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` and `MAIL_FROM`; no credential is stored in the repo. Runbook: `docs/transactional-email.md`. No user-facing change until PLA-26 and PLA-65 call the service.
- Self-service data export (PLA-112): "Export my data" on the classic preferences page downloads a ZIP of the signed-in user's plants (archived included), care log, scan history and journal events (PLA-111) as JSON and CSV, plus their photos. `POST /api/v1/users/me/export` builds it, `GET /api/v1/users/me/export/{id}` streams it (owner only, identical 404 otherwise); 3 exports per user per hour (429 with Retry-After); files are deleted after 24 h by an hourly job. No migration, no new dependency. Docs: `docs/data-export.md`. Dev profile only: `DataExportReviewSeeder` creates `export-owner@plantpal.test` and `export-other@plantpal.test` (password `export-password-1`). en/fr/ar strings.
- Plant groups (PLA-110): group plants by room, bed or greenhouse. `/api/v1/groups` (list, create, rename, delete), `PUT /api/v1/plants/{id}/group` (assign, `groupId: null` clears), `GET /api/v1/plants?groupId=` and `POST /api/v1/groups/{id}/care` (`WATERED` or `FED`: one care log per active plant, and an enabled recurring reminder of that type is rescheduled like "mark done"; treatment steps are untouched). A plant is in at most one group; deleting a group keeps its plants and only clears `plants.group_id` (migration 045, additive). Everything is scoped to the signed-in user (404 otherwise). Classic app: group filter and "Manage groups" dialog on the plant list, "Move to group" on the plant actions sheet; en/fr/ar strings. Atlas is unchanged. Dev profile only: `PlantGroupReviewSeeder` creates `groups-owner@plantpal.test` and `groups-other@plantpal.test` (password `groups-password-1`).
- Identification verdicts (PLA-102): a "Was this right?" control (right / wrong / not sure, with an optional correct species when wrong, suggesting PlantNet candidates) on the classic identification result and scan detail; `PUT /api/v1/identifications/{id}/verdict` (owner only, 404 otherwise, completed scans only, latest verdict wins) and an admin-only `GET /api/v1/admin/identification-verdicts?from=&to=` (inclusive UTC days) feeding a counts card on the admin overview. Migration 044 adds three nullable columns; the stored species is feedback only and never changes the scan. Dev profile only: `VerdictReviewSeeder` creates `verdict-owner@plantpal.test` and `verdict-admin@plantpal.test` (password `verdict-password-1`) with sample scans. Atlas is unchanged. en/fr/ar strings.
- Plant placement, light and coarse region (PLA-107): optional per-plant placement (indoor/outdoor/greenhouse) and light exposure (low/medium/bright/direct sun), and a per-user region (ISO country + one of five climate zones) on the preferences screen; migration 043 adds four nullable columns (existing rows stay empty, shown as "Not set"). No precise location is requested or stored; nothing consumes the values yet. Map entries: `docs/personal-data-map.md`. en/fr/ar strings.
- Photo GPS removal (PLA-118): GPS coordinates are stripped from every uploaded photo before it is stored, served or sent to an AI provider (date taken is kept for JPEG); migration 042 cleans already-stored photos in place (irreversible). Audit: `docs/photo-gps-audit.md`.
- First-run guidance (PLA-117): a skippable 3-step intro after sign-in (once per browser, localStorage), photo tips and a camera explainer on the Identify upload ("Use camera" or "Upload a photo instead"; a denied camera keeps upload available), and an identify action on the empty dashboard/garden. Funnel events go through Vercel Web Analytics `va`; the event names in `first-run.service.ts` are provisional until the PLA-78 definitions exist. Frontend-only; en/fr/ar strings.
- Offline scan queue (PLA-121): in the classic app's Identify page, a scan submitted without a connection (or failing with a network error) is stored on the device in IndexedDB and listed as "waiting"; it uploads automatically when the browser comes back online (and on page start), with bounded retry/backoff. Failed items offer Retry and Discard. The server and quota are only touched when an item is actually uploaded. Frontend-only; en/fr/ar strings added.
- Crawlable multilingual public routes (PLA-73): the classic frontend now prerenders the landing page at `/en/` and `/fr/`
  (`@angular/ssr`, static output; the URL language wins on those routes). The host rules (shared `frontend/nginx/routes.inc`
  for the dev-delivery and production nginx, and the generated `vercel.json`) now answer: prerendered public pages; the
  client-only shell (`index.csr.html`) only for the known authenticated/client route prefixes; a real 404 (static
  `404.html`) for everything else; no shell fallback for `robots.txt`/`sitemap.xml`; and a 301 from the declared alias host
  to the preferred host (`PREFERRED_HOST` / `HOST_REDIRECT_FROM` for nginx, repo variables `PREFERRED_HOST` / `ALIAS_HOST`
  for Vercel; inert when unset). The service worker's navigation fallback is limited to the same prefixes. Angular
  `@angular/*` packages aligned on 20.3.34. Build output layout (`dist/plantpal/`) is unchanged.
- Species Gallery tab (PLA-95): pick one of your plants to see only its photos as an oldest-to-newest dated timeline, with a
  'Back to all plants' control that restores the full gallery without refetching. Frontend only.
- Review environment starts ready to test (demand `factory-20261001-plantpal-review-environment-testable`): `review start`
  seeds a test account (`review@plantpal.test`) and a baseline plant, runs the backend on `dev,platform` so AI goes
  through ai-gateway, and records seed / identification / Claude / ai-gateway checks in the receipt. `AnthropicClient`
  reports available when the gateway is enabled (no Anthropic key held in the app).
- dev-delivery `review start` accepts the launcher's environment with both `REVIEW_PR_NUMBER` and `REVIEW_BRANCH`
  set: the PR is resolved by number and the branch is verified against its head (mismatch refused).
- Species detail page (classic frontend): third "Gallery" tab (PLA-94) showing the user's photos of the species
  oldest-first by date taken, with an empty state. Consumes the PLA-93 endpoint; fr/ar strings added.
- dev-delivery launcher interface (demand `factory-20261001-launcher-review-command-interface`): `review start|status|stop`
  and `deploy` take launcher's parameters from `REVIEW_*` / `COMMAND_PARAM_COMMIT` when the flag is absent;
  `review stop` under a later idempotency key now stops the environment a reused start returned.
- `GET /api/v1/species/{id}/photos` (PLA-93): the caller's own non-archived plants' photos of a species,
  grouped by plant, each group ordered by `date_taken` ascending (tiebreak identification id). Unowned or
  unknown species return 200 with an empty list. No schema change.
- Review environments (demand `factory-20260930-plantpal-review-environment-and-swagger`):
  `dev_delivery.py review start|status|stop` runs an unmerged PR head (all four required checks
  green on it) in its own compose project `plantpal-review`, volumes, generated config and
  loopback port `8186`, one at a time, and reports the contracts v0.38.0 tagged receipt
  (`environment.name: review`, `revisionRole: task`, observed `/actuator/info` identity).
  Swagger (`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs`) is proxied in the dev-delivery
  and local nginx configs; `application-prod.yml` disables springdoc. Tool repinned to
  contracts v0.38.0. See `docs/dev-delivery.md` sections 6-7.
- PLA-92: each scan (`identifications`) now stores a non-null `date_taken` (migration 041),
  read from the photo's EXIF (DateTimeOriginal, then Digitized, then IFD0 DateTime; UTC unless an
  offset tag exists) with upload time as fallback; existing rows are backfilled from `created_at`.
  Exposed as `dateTaken` on `IdentificationResponse`. Adds `metadata-extractor`.
- Planotell dev-only delivery (D113, demand `factory-20260927-dev-delivery`):
  `tools/dev-delivery/dev_delivery.py` deploys a revision merged into `origin/dev`
  (after its `Backend CI`/`Frontend CI`/`Detect secrets` and PR `sonar-gate` checks
  passed) to an isolated compose project `plantpal-devdelivery`
  (`deploy/dev-delivery/`, loopback `:8184`, own volumes, generated dev-only secrets),
  observes the running identity, runs smoke/criterion checks, and stores an app-owned
  receipt with rollback identity; `lookup` emits contracts v0.31.0
  `delivery.producer-result`. Backend reports `deployment.{appIdentity,revision,
  deploymentId,environment}` at the now-public `GET /actuator/info`
  (`DeploymentIdentityInfoContributor`; `backend/Dockerfile` stamps `APP_REVISION`).
  See `docs/dev-delivery.md`.
- Backend hosting moved from Railway to an OVH VPS (2026-09-24): `deploy/vps/`
  holds the Compose stack (Postgres 15, Redis 7 with password + AOF, runtime-only
  backend image, Caddy TLS for `api.plants.moulworks.com`), a one-time
  `bootstrap.sh` (Docker, ufw, fail2ban, swap, `deploy` user, key-only SSH,
  nightly `pg_dump`) and `.env.example`. `deploy.yml`'s backend job now `scp`s the
  stack + JAR and waits for the container healthcheck; `backend/railway.json` and
  `Dockerfile.railway` removed. Fresh database (no Railway data migrated).
- Chunk 0 — platform Room bootstrap: `HEXAGON.md` and `DEPLOYMENT.md` added
  (PlantPal already carries `README.md`/`SECURITY.md`; this is the first real
  retrofit of the D013 convention onto a pre-existing, already-deployed app).
  `app-manifest.yaml` static registry record (`app.manifest` contract, routes
  drawn from the real `@RequestMapping` paths). `.claude/CLAUDE.md` gained a
  platform-integration notice prepended above PlantPal's own 516 lines of
  instructions (untouched).
  No application code changed — repo cloned as-is at `dev` (post PHASE10 merge)
  onto a new `platform-integration` branch.
- Dimension-event emission (D024/D027): `PlantServiceImpl` publishes a
  `dimension.event` (`plant_count`, delta `+1`/`-1`) to Kafka topic `dimension.events`
  on plant create and archive, for Treasury's business-dimension metering. Bumped
  `contracts` pin to `v0.5.0` (adds the `DimensionEvent` schema). New
  `com.plantpal.plant.config.PlantKafkaTopicConfig` (topic bean, mirrors
  `identification.config.KafkaTopicConfig`'s pattern).
- State-feed emitter (D029): new `com.plantpal.statefeed.StateFeedEmitter`
  outbound port, gated by the `platform` Spring profile
  (`application-platform.yml`, same pattern as the gateway swap). Emits
  `state.event`'s `app.status` once on `ApplicationReadyEvent` and
  `activity.count` (`identification.completed`) each time the existing
  `IdentificationCompletedEvent` fires (now also published via
  `ApplicationEventPublisher` alongside its pre-existing Kafka send — no new
  cross-package injection needed). Fire-and-forget over `POST
  {platform.statefeed.url}/events`, 2s connect / 5s read timeouts, any failure
  logged at WARN and swallowed — the feed is a read-only mirror
  (spec-state-feed.md §3), never load-bearing. New
  `StateFeedProperties`/`StateFeedEmitter` unit tests (7 + 1 cases): default
  gating, payload shape, transport-failure swallowing.

- Auth characterization suites made blocking CI evidence (Factory feature
  mission `9636b730-d536-4ad3-99e8-80c2797f9be3`, demand
  `app-studio-20260907-plantpal-t1-*`) — **delivered waves only, feature not
  complete**. Shipped: T1.20 auth surface inventory (23 frontend route
  records, 48 backend endpoint records, 3 defect pins) and structural
  verifiers; T1.22 protected-endpoint denial characterization
  (`ProtectedEndpointAuthTest`, 88 cases); T1.23 AuthGuard
  defect-characterization coverage; T1.24 JwtInterceptor
  defect-characterization coverage; T1.25 cross-origin session-handoff
  characterization (8 cases); T1.26 made the four characterization runners
  (`ProtectedEndpointAuthTest`, AuthGuard, JwtInterceptor, session-handoff)
  a blocking `Auth Characterization CI` job in `.github/workflows/ci.yml`
  on every push/PR, with a structured JUnit-report artifact and mutation-run
  evidence that a real assertion break fails the job. Reports:
  `demands/fulfilled/app-studio-20260907-plantpal-t1-{20,22,23,24,25,26}-report.md`.
  **Outstanding:** T1.27 (owner-observation unit) and any further planned
  waves of this mission are not yet dispatched/shipped — no v1.0.0 tag or
  app-birth release claim applies to this feature.

- Factory mission `9b774285-8a9f-4763-9d27-7310127bc931` Waves 2–4 implementation
  checkpoint (candidate only; no deployment or enforcement activation): protected-route
  return URLs are validated and restored after login; concurrent protected-API 401s use one
  sign-out/redirect flow; and explicit sign-out best-effort revokes the registry record.
  The new PlantPal-owned Redis session registry issues a per-token `jti`, maintains a 30-minute
  sliding record plus a signed 12-hour absolute cap, distinguishes revocation reasons, and is
  deliberately **inert by default** (`app.session.enforcement-enabled=false`). The client polls
  only the explicit non-renewing status endpoint, warns at two minutes, renews only after an
  explicit user action, and preserves a safe return destination on an expiry verdict. Verified
  in the recovered session: backend unit suite 451/451, focused auth/session suite 108/108,
  and frontend expiry regression 11/11 plus production build. That earlier Docker limitation was
  corrected in the follow-up candidate checkpoint: Testcontainers was updated for Docker Desktop
  29 and the test profile selects the supported in-process identification transport, allowing the
  full verified suite to run. Wave 5's separate enforcement/release/deployed-live-verification
  decision remains outstanding.

- Factory mission `9b774285-8a9f-4763-9d27-7310127bc931` acceptance-evidence pass
  (2026-09-16, branch `feature/PP-100-session-hardening-waves-2-4`; waves 2–4 remain a
  candidate — **no enforcement activation, no deployment, no live verification, no merge**).
  Added `SessionEnforcementFilterTest` (14 cases): the wave-3 criterion "rejected and expired
  sessions cannot call protected APIs" previously had no test at all — the registry test only
  proved the service returns `invalid(...)`, and the existing denial suite ran with
  `enforcementEnabled=false`, so the branch that withholds authentication was never exercised.
  The suite now proves every `RevokedReason` yields 401 plus the `X-Session-Revoked-Reason`
  header, that an unavailable registry fails closed once enforcing and open while inert, that the
  same rejected session is still admitted while the flag is off (pinning ADR-8's inert default and
  the wave-5 cutover boundary), and that the non-renewing status endpoint peeks rather than slides
  — server-side proof that background polling cannot extend a session, which was previously
  untested. It is a surefire `*Test` needing no Spring context and no Testcontainers, so the
  enforcement boundary is verifiable where Docker is unavailable. Also applied `spotless` to the
  mission's own sources (it is not lifecycle-bound, so `mvn clean verify` never ran it) and
  recorded the wave-2 disposition of the wave-1 defect pins in
  `docs/auth-hardening/defect-pins.md`. Verified: backend unit 465/465, frontend Jest 549/549
  across 44 suites, production build exit 0, `spotless:check` clean. The later candidate
  checkpoint establishes backend integration verification after the Testcontainers/test-profile
  correction; its raw evidence and remaining Wave 5 boundary are in
  `docs/auth-hardening/candidate-375dfa9.md`.

- Factory mission `9b774285-8a9f-4763-9d27-7310127bc931` current-candidate checkpoint
  `6981a45` (2026-09-19, branch `feature/PP-100-session-hardening-waves-2-4`) — again
  waves 2–4 only, still **no enforcement activation, no merge, no deployment, no live
  verification**. CI and Secret Scanning both pass at this revision (runs `35437826917`,
  `35437826943`); the preceding `f2f5a44` head was red because the Testcontainers
  correction's new registry test failed lifecycle-bound `spotless:check` while all tests
  passed, which `a75070a` corrected. Receipts were re-measured at `6981a45` rather than
  inherited: backend focused 111/111 (88 protected-endpoint denial, 14 enforcement-filter,
  9 registry) and frontend session specs 26/26 across 5 suites, each with its own exit
  code. Reviewing wave-2's "a safe local destination is restored after login" clause
  against the suite showed it unasserted — `login.component.spec.ts` covered only the
  no-`returnUrl` default — so the restore path and its unsafe-input fallback are now
  covered, the first proven non-vacuous by mutation. The 2026-09-16 fulfillment report
  for this mission, which the origin ruled out of
  the active `demands/fulfilled/` scan, is preserved as history at
  `docs/auth-hardening/evidence/mission-9b774285-historical-partial-fulfillment.md`; the
  current candidate record is `docs/auth-hardening/candidate-6981a45.md`. Wave 5's
  separate, still-unauthorized enforcement/release decision remains the outstanding
  criterion.

### Fixed
- **Identification AI-JSON parsing broke on a markdown-fenced response**
  (`IdentificationServiceImpl.parseIdentificationResult`), causing a
  successful identification to be logged as `Malformed identification JSON
  from DeepSeek, using fallback` and silently saved as "Unknown Plant"
  confidence 0.3 even when the model correctly identified the plant
  (confirmed live: PlantNet independently matched "Coleus spp." on the same
  photo). Root cause: the provider (or the ai-gateway passthrough path, which
  bypasses each client's own `stripThinkTags()`) wrapped its JSON in a
  ` ```json ... ``` ` code fence, and `objectMapper.readValue()` was called on
  the raw fenced string. Added a single reusable `extractJson()` helper
  (mirrors sentinel-hub's `parse.py`): strips a leading/trailing
  ` ```json`/` ``` `/`~~~` fence if present, otherwise falls back to
  extracting the substring from the first `{` to the last `}`. Wired into
  every AI-JSON parse site in the class — `parseIdentificationResult`,
  `parseCarePlan`, `parseCureAdvice`, `parseDuplicateGroups` — all of which
  shared the same fence vulnerability. Existing fallback behavior for
  genuinely malformed output is unchanged; this only rescues
  fenced/prose-wrapped-but-otherwise-valid JSON. 4 new regression tests
  (`IdentificationServiceImplTest$AiJsonFenceRecovery`): fenced JSON now
  parses to real values, bare JSON still works, prose-then-JSON works,
  genuinely-garbage output still hits the fallback.
- Gateway-routed PLANTNET identification (`IdentificationServiceImpl.runIdentification`)
  now attaches `organs`/`project`/`lang` to the `AiRequest` context so
  ai-gateway's `PlantNetAdapter` receives them — previously only the image made
  the trip on the gateway path, silently dropping the user's PlantNet flora/lang
  preference and any explicit `organs` list. Direct (non-gateway) path was
  already correct and unaffected.
- **Gateway-routed identification JSON was truncated mid-array, falling back to "Unknown
  Plant"** (live: `WARN IdentificationServiceImpl - Malformed identification JSON ...
  Unexpected end-of-input: expected close marker for Array`) — the fence-stripping fix above
  was already working; this failure was pure truncation. Root cause: ai-gateway's
  `AnthropicAdapter` defaults `max_tokens` to 2048 when the request `context` carries no
  `maxTokens` entry, but none of `IdentificationServiceImpl`'s gateway request builders set
  one, silently halving the budget the direct clients use
  (`AnthropicClient.DEFAULT_MAX_TOKENS` / `DeepSeekClient.O4_MINI_MAX_COMPLETION_TOKENS`, both
  4096). Added `.putContextItem("maxTokens", ...)` to every gateway `AiRequest` whose response
  is parsed as structured JSON: the main identification+care-plan request
  (`identificationGatewayRequest`, new `GATEWAY_IDENTIFICATION_MAX_TOKENS = 8192` — its response
  nests species/confidence/health fields plus a full multi-card care plan, comfortably bigger
  than the others), the cure-advice reasoning request and the annotation (region polygons)
  request (both `GATEWAY_MAX_TOKENS = 4096`, matching the direct-path floor). PlantNet's gateway
  passthrough is unaffected — it's a REST proxy, not an LLM call, so the extra context field is
  a no-op there. New regression assertions in `IdentificationServiceImplTest` (identification,
  cure-advice, and annotation gateway-routing tests) assert `context.maxTokens >= 4096`.
- `deploy.yml`'s `build-backend` job ran a plain `mvn package`, which fails on a
  GitHub runner since `backend/pom.xml` unconditionally depends on
  `io.platform:contracts:0.5.0` (no registry, built from a pinned tag per D031).
  Replicated `ci.yml`'s three contracts steps (read pinned version from the pom,
  checkout `contracts` at that tag, `mvn install` its Java bindings) before the
  Package step, so the two workflows can never disagree with the pom. Workflow
  stays disabled (owner re-enables after review) — only the build steps changed.

### Changed
- Re-pinned `contracts` `v0.7.0` → `v0.17.0` (2026-07-30). Of the intervening
  releases only **v0.12.0** is breaking, and it only pattern-constrains
  `hexagon.descriptor.json`'s `contracts.used` items — PlantPal's `HEXAGON.md`
  frontmatter ids (`app.health`, `app.manifest`, `ai.request`, `ai.response`,
  `dimension.event`, `state.event`) all already conform, so nothing to fix.
  Everything PlantPal actually consumes in Java (`AiRequest`,
  `AiRequestMediaInner`, `AiResponse`, `BlockedResponse`, `DimensionEvent`,
  `AppStatusEvent`/`AppStatusPayload`, `ActivityCountEvent`/`ActivityCountPayload`)
  is additive-only across the range; v0.13.0's `AiResponse` widening
  (`result`/`model`/`provider` now optional, new `skipped` field) is
  source-compatible with PlantPal's usage. `HEXAGON.md` frontmatter pin bumped
  to match; Docker build-context wiring (`docker-compose.yml`,
  `backend/Dockerfile`, `DEPLOYMENT.md`) renamed `CONTRACTS_M2_0_7_0` →
  `CONTRACTS_M2_0_17_0`. CI workflows read the pin dynamically off
  `backend/pom.xml` — unaffected.
- Re-pinned `contracts` `v0.5.1` → `v0.7.0` (all intervening releases —
  v0.6.0/v0.6.1/v0.6.2/v0.7.0 — are additive or patch-only per contracts'
  `CHANGELOG.md`; no breaking change touches anything PlantPal consumes).
  `HEXAGON.md` frontmatter pin bumped to match; Docker build-context wiring
  (`docker-compose.yml`, `backend/Dockerfile`) renamed
  `CONTRACTS_M2_0_5_1` → `CONTRACTS_M2_0_7_0`. CI workflows already read the
  pinned version dynamically off `backend/pom.xml` — unaffected.
- Gateway swap is now gated by a **Spring profile** (`platform`,
  `application-platform.yml`) instead of a boolean default living inside
  `application.yml`/`application-dev.yml`. `platform.gateway.*` no longer
  appears in either base file; `GatewayProperties` binds `enabled=false` via
  `@DefaultValue` when the prefix is entirely absent, so the default/standalone
  boot never reads a `platform.*` key (D009). Activate with
  `SPRING_PROFILES_ACTIVE=dev,platform` to route AI calls through `ai-gateway`
  (`platform.gateway.enabled` defaults `true` under that profile). Railway prod
  never activates `platform`. Added `GatewayStandaloneProfileIT`/
  `GatewayPlatformProfileIT` context tests for both cases. (PP-081, PR #107)

## 2026-07-07

### Fixed
- **FIX-12** — `plant_count` dimension-event Kafka emit moved out of the
  `@Transactional` service method body into an intra-JVM
  `PlantCountChangedEvent` forwarded to Kafka by a new
  `PlantCountDimensionEmitter` bound to
  `@TransactionalEventListener(phase = AFTER_COMMIT)`. A rolled-back
  transaction can no longer leak a phantom `plant_count` delta to Treasury.
- **SEC-4** — `backend/.env.example`'s `JWT_SECRET` no longer commits a
  real-looking generated value; replaced with an explicit
  `<generate: openssl rand -base64 64>` placeholder. `application-test.yml`'s
  test secret replaced with an obviously-fake repeating constant (still valid
  Base64, still >= JJWT's 256-bit HS256 minimum). `.gitignore`'s malformed
  `./backend/.env` line removed.
- Docker build was unbuildable from a clean checkout: `backend/Dockerfile`
  relied on a BuildKit cache mount that only holds the `contracts` jar if a
  prior build happened to populate it. Switched to `COPY --from=contracts-m2`,
  a named additional build context (matches `../ai-gateway/Dockerfile`'s
  pattern); `docker-compose.yml` wires it via `CONTRACTS_M2_0_5_1`. Documented
  in `DEPLOYMENT.md`.

### Changed
- Contracts re-pinned `0.5.0` → `0.5.1` (`backend/pom.xml`, `HEXAGON.md`) —
  picks up the v0.5.1 `runId` int32→int64 overflow correctness fix.
- `HEXAGON.md`/`DEPLOYMENT.md` pin prose now points at `backend/pom.xml` as
  the authoritative source instead of hardcoding a version number that drifts.
- `README.md` overhauled: Java 21 (was 17), real 5-provider AI stack (was
  Anthropic-only), MVP table flipped to shipped, `.claude/` file-location
  table added, stale dev-branch strategy removed.
