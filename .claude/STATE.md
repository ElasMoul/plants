# PlantPal — Shared Project State
> Updated after each session. All agents read this first.
> Last updated: 2026-07-06 (housekeeping — stale git index refresh, no code changes)
> Full session diary: Archive/STATE_2.md and Archive/STATE_1.md | git log for code history

---

## Current State

| Branch | Status |
|---|---|
| `dev` | Clean — all phases 0–10 merged ✅ (PR #81, bb9b0a8) |

**Migration sequence:** 001–030 applied. Next free: **031**.
**Next free PP branch number:** PP-079 (PP-071–075 = T10.A–F; PP-076 = T10.G voice fix; PP-077 = T10.H TTS; PP-078 = T10.I mic bug).

---

## Phase Status

| Phase | Status |
|---|---|
| 0 — Setup | ✅ Complete |
| 1 — Auth + Plant Management | ✅ Complete |
| 2 — AI Plant Identification | ✅ Complete |
| 3 — Reminders + Care Plans | ✅ Complete (T3.3 manual device testing → Phase DEPLOY beta) |
| 4 — AI Chat | ✅ Complete (streaming + history) |
| 5 — Launch prep | 🔲 Renamed Phase DEPLOY, runs last |
| 6 — Species & Treatment Restructure | ✅ Complete (T6.1–T6.14) |
| 7 — Model Control, Batch, Multi-Treatment | ✅ Complete (T7.1–T7.4) |
| 8 — PlantNet First-Class Provider | ✅ Complete (T8.0–T8.7) |
| 8.5 — Identification Pipeline Resilience | ✅ Complete (T8.A–T8.G) |
| 9 — Quality, Testing & Hardening | ✅ Complete — merged to dev |
| 9.5 — Species Card Harvest + Async Reliability | ✅ Complete — merged to dev |
| 10 — Contextual Scanning & Treatment Polish | ✅ T10.A–I complete on PHASE10 branch (PP-071–078); pending merge to dev |
| DEPLOY — Launch Preparation | 🔲 Not started (T-DEPLOY.1–8, PP-079+) |

---

## What Was Built (brief per phase)

**Phase 0–1:** Spring Boot + Angular skeletons, Docker Compose (Postgres 15 + Redis 7), CI/CD, JWT auth, Plant CRUD.

**Phase 2:** Async Kafka identification pipeline (POST /analyze → 202 → poll), gpt-4o vision + DeepSeek-R1 text, polygon disease annotation, Redis photo storage with SHA-256 dedup, image-dimension locking.

**Phase 3:** Reminder CRUD + scheduler (one daily push per user), web-push VAPID, actionable care plans with ROUTINE reminders and multi-step TREATMENT plans, Mermaid diagrams.

**Phase 4:** AI chat (Ollama/GitHub Models), plant-context injection, SSE streaming, conversation history.

**Pre-Phase-5 cleanup:** Reminder double-completion fix, duplicate treatment fix, species care cards, chat history/SSE, 4 controller ITs, JaCoCo gate 55%.

**Phase 6:** Species entity (shared, not per-user), Treatment entity (disease lifecycle wrapping TreatmentPlan), 5-tab bottom nav, Home page, species-first Garden, Plant page icon-bar, 3-path identification flow (Flow 1/2/3), event-driven Treatment→TreatmentPlan completion sync.

**Phase 7:** VisionModelPreference + ReasoningModelPreference split, silent-fallback removal, RateLimitException + retryAfterSeconds, model-usage badges, batch scan (BatchScanService), multi-treatment picker, disease-description poll fix, LenientJsonParser.

**Phase 8:** PlantNet v2 (ranked candidates, organs, attribution, geolocation flora), disease cross-check (second opinion for Flow 3), GBIF/POWO/IUCN enrichment, quota telemetry, Claude (AnthropicClient) as 5th model option for both vision and reasoning.

**Phase 8.5:** Per-stage status model on Identification (identificationStatus/annotationStatus/candidateStatus, D5), non-fatal annotation + PlantNet stages, predictedOrgans fix, token-budget Bucket4j + finite executor queue + jittered retry, PlantNet enrichment moved to async fire-and-forget (D1 amendment), retry endpoint, stage-aware UI.

**Phase 9:** Frontend unit tests (Jest), Playwright E2E (Chromium + WebKit, AI calls stubbed), visual regression + a11y (axe) + Lighthouse CI, AI visual review (advisory), Testcontainers ITs wired into mvn verify + JaCoCo 80%, Sentry + X-Correlation-ID, gitleaks + Trivy + OWASP + Dependabot, nightly AI eval suite + prompt-injection guardrails.

**Phase 9.5:** PlantNet candidate data harvested onto Species at resolve time (family, genus, imageUrl, attribution), GenerationStatus (PENDING/READY/FAILED) on Species.descriptionStatus + Treatment.descriptionStatus, regenerate endpoints, Species overview redesigned with real PlantNet hero + candidate strip + status-driven prose, Treatment description poll + retry, dedicated Task Step page (full-width Mermaid, AI deep-links), plant-edit back-nav fix.

---

## Next Tasks (ordered)

1. **Merge PHASE10 → dev** — Phase 10 fully complete (T10.A–I, PP-071–078). Open PR to merge PHASE10 branch to dev.
2. **Phase DEPLOY** — production config, Railway/Vercel deploy, beta, v1.0.0.
4. **Re-enable E2E in CI** — need `ng serve + wait-on` before Playwright runs (deferred T9.2).
5. **Re-enable Lighthouse CI** — fix dist path `dist/frontend` → `dist/plantpal` (deferred T9.3).
6. **T3.3** — manual on-device push/PWA testing — folded into Phase DEPLOY beta.
7. **Kafka/Zookeeper production story** — managed add-on or synchronous fallback for v1.0.0.

---

## Key Open Decisions

- **D10.1 — Annotation strategy:** Conservative skip chosen (skip when healthStatus ≠ ISSUES_DETECTED). Full merge-into-single-call is a follow-up option. ✅ Implemented.
- **D10.2 — Health-scan card draft state:** Client-side only confirmed. `@Input() isDraft` + `@Input() treatmentActive` on CareCardComponent. ✅ Implemented.
- **D10.3 — Treatment auto-progression:** Root cause was H3 (CareCardComponent + plant-detail chaining craftPlan). Fixed in T10.C + T10.F. ✅ Resolved.
- **Kafka prod story:** still open, blocks T-DEPLOY.5.

---

## Phase 10 — T10.G + T10.H + T10.I (2026-06-28, PHASE10 branch)

**T10.G — Mic permission pre-check (PP-076, merged PHASE10):**
Original fix: added `getUserMedia` pre-check before `recognition.start()`. Later found to cause
mic contention — see T10.I below. Pre-check removed; pattern superseded by T10.I.

**T10.H — TTS Read Aloud (PP-077, merged PHASE10):**
`SpeechService` (`providedIn:'root'`, `NgZone`-safe callbacks) + `ReadAloudButtonComponent`
(SharedModule, `volume_up`/`stop_circle`). Wired into: CareCardComponent header,
SpeciesDetail About section (READY guard), TreatmentDetail disease description (READY guard),
TreatmentStepListComponent per-step, HomeComponent reminder rows (overdue + today,
`$event.stopPropagation()`), ScanDetailComponent userContext block. PlantModule gained
`SharedModule` import for ScanDetail access.

---

**T10.I — isSecureContext gate + mic contention root cause (PP-078, PHASE10 branch):**
- `speechSupported = !!SpeechRecognitionAPI` (hides button on Firefox) + `speechSecure = window.isSecureContext || location.hostname === 'localhost'` (disables + tooltip on plain HTTP over LAN).
- `/voice-test` diagnostic page added at `features/voice-test/` — two-panel layout (Step 1: getUserMedia mic test; Step 2: SpeechRecognition without prior getUserMedia). Accessible via user menu. Reveals that getUserMedia-before-recognition causes silent mic contention on Chromium.
- `startListening()` simplified: `getUserMedia` pre-check entirely removed, calls `doStartRecognition()` directly. `requestingPermission` flag and "Requesting access…" chip removed.
- ESLint CI fix: empty `() => {}` → `() => { /* ... */ }` in voice-test; `// eslint-disable-next-line` → block form to cover multi-line `(window as any)` declarations in both files.
- All callbacks wrapped in `ngZone.run()` (NgZone fix from the previous session for textarea not updating).
- Commits: `d9e3eaf` (voice input fix), `5691763` (ESLint fix).

## Post-Phase-10 Bugfixes (2026-06-28, PHASE10 branch)

Three frontend UX fixes (commit 6c10241):
- **Scans blank after submit** — `submitAddScan()` now calls `loadScanHistory()` immediately when `activeSection === 'scans'` instead of leaving the section blank.
- **Plant overview merged care plan** — Initial fetch changed to size=10; `mergedCarePlan` getter deduplicates care cards by `type` across all scans. `loadScanHistory()` updates `allIdentifications` as side-effect.
- **Species page botanical facts** — "Botanical facts" section (family/genus/IUCN badge) added to species-detail overview; visible regardless of `descriptionStatus`. `gbifId`/`powoId`/`iucnCategory` added to frontend `SpeciesResponse` model.

Backend race condition fix (commit d2c03e3):
- **`@Async` + `@Transactional` race in `SpeciesEnrichmentServiceImpl`** — `createSpecies()` was firing `enrich()` inline before the outer transaction committed; `enrich()`'s own `findById` returned null → enrichment silently skipped → `descriptionStatus` stuck at PENDING forever. Fixed with `TransactionSynchronizationManager.registerSynchronization(afterCommit)` in both `createSpecies()` and `regenerateDescription()`. See vault: [[async-transactional-race-condition]].

---

## Dev Infrastructure

**LAN HTTPS (2026-06-28):** Frontend Nginx now serves HTTPS on port 443 (self-signed cert) for
mobile device testing. Port 80 redirects to 443. Cert lives at `frontend/nginx/certs/self.crt`
(committed); key at `frontend/nginx/certs/self.key` (gitignored). One-time cert generation:
```
mkdir -p frontend/nginx/certs
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout frontend/nginx/certs/self.key \
  -out frontend/nginx/certs/self.crt \
  -subj "/CN=plantpal.local" \
  -addext "subjectAltName=IP:<LAN_IP>"
```
Then `docker compose build frontend && docker compose up -d frontend`. Phone: navigate to
`https://<LAN_IP>`, tap Advanced → Proceed. Verify via `/voice-test`: isSecureContext = true.
Production (Railway/Vercel) is unchanged — they already terminate TLS.

---

## Known Tech Debt

- JaCoCo gate at 80% (restored in T9.5 — verify still holds after Phase 9.5 additions).
- E2E disabled in CI (needs `ng serve + wait-on` setup).
- Lighthouse CI disabled (dist path issue).
- GITHUB_TOKEN: rotate before prod (T9.7 reminder).
- `generateCarePlan()` in DeepSeekClient is dead code (never called — safe to remove in a cleanup pass).
- `PlantNetClient` PLANTNET enum: if a future cleanup removes it, remove from both backend enum and frontend type simultaneously.
- SSE streaming (chat): written and unit-tested but never confirmed against a live Docker stack with actual incremental token delivery — re-verify before DEPLOY.
- DeepSeekAnnotationClient 429 shape differs from DeepSeekClient's RateLimitException — noted in ARCHITECT.md, not yet unified.
