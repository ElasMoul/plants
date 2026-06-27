# PlantPal — Shared Project State
> Updated after each session. All agents read this first.
> Last updated: 2026-06-27 (Phase 10 designed; files cleaned up + archived)
> Full session diary: Archive/STATE_2.md and Archive/STATE_1.md | git log for code history

---

## Current State

| Branch | Status |
|---|---|
| `dev` | Clean — all phases 0–8.5 merged |
| `PHASE9` | T9.1–T9.8 code complete — **pending CI green + merge to dev** |
| `PHASE9.5` | T9.A–T9.F code complete — **pending merge after PHASE9** |

**Migration sequence:** 001–029 applied. Next free: **030** (reserved T10.A `user_context`).
**Next free PP branch number:** PP-071 (PP-057–070 used by Phases 9 and 9.5).

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
| 9 — Quality, Testing & Hardening | 🟡 Code complete on PHASE9 branch — pending CI + merge |
| 9.5 — Species Card Harvest + Async Reliability | 🟡 Code complete on PHASE9.5 branch — pending merge |
| 10 — Contextual Scanning & Treatment Polish | 🔲 Not started (T10.A–T10.F, PP-071–078) |
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

1. **Merge PHASE9 → dev** once CI green (last fix commit: 4f1bbb3).
2. **Merge PHASE9.5 → dev** after PHASE9 lands.
3. **Phase 10 tasks** (T10.A–T10.F) — contextual scanning, scan redesign, treatment polish.
4. **Phase DEPLOY** — production config, Railway/Vercel deploy, beta, v1.0.0.
5. **Re-enable E2E in CI** — need `ng serve + wait-on` before Playwright runs (deferred T9.2).
6. **Re-enable Lighthouse CI** — fix dist path `dist/frontend` → `dist/plantpal` (deferred T9.3).
7. **T3.3** — manual on-device push/PWA testing — folded into Phase DEPLOY beta.
8. **Kafka/Zookeeper production story** — managed add-on or synchronous fallback for v1.0.0.

---

## Key Open Decisions

- **D10.1 — Annotation strategy:** merge annotation into identification prompt (single gpt-4o call, simpler, user's recommendation) **vs.** skip annotation call when healthStatus ≠ ISSUES_DETECTED (conservative, preserves per-stage architecture). Confirm before T10.A.
- **D10.2 — Health-scan card draft state:** client-side presentation only (no DB changes) vs. persisted draft flag. Recommend client-side.
- **D10.3 — Treatment auto-progression root cause:** must be diagnosed (read TreatmentServiceImpl + CareCardComponent + TreatmentDetailComponent) before T10.C is coded.
- **Kafka prod story:** still open, blocks T-DEPLOY.5.

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
