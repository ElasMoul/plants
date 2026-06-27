# PlantPal — State [ARCHIVE — snapshot before Phase 10 planning session]
# Archived: 2026-06-27. See live STATE.md for current version.

[Full session-by-session history archived here. See git log for code-level history.]

## Summary at archive date:

Phases 0–4, 6, 7, 8, 8.5, 9, and 9.5 are all complete.
- Phase 9 code is on the PHASE9 branch — pending CI green + merge to dev.
- Phase 9.5 code is on the PHASE9.5 branch (T9.A–T9.F) — pending merge after Phase 9.
- dev branch is clean with all phases 0–8.5 merged.
- Migration sequence ends at 029 (028+029 from Phase 9.5).
- Next free PP branch: PP-071 (PP-065–070 used by Phase 9.5).
- Phase 10 (Launch) not started. Renamed to Phase DEPLOY in the new plan.

## Key bugfixes (session highlights — see Archive/STATE_1.md for full detail):

Phase 7 follow-up bugs fixed (all on feature/PP-041 or adjacent):
- DeepSeek-R1 1-call/60s upstream cap → RateLimitException + retryAfterSeconds + Retry-After header parsing
- Model preference save 400ing + vision/reasoning not driving actual model selection
- PlantNet missing from vision picker (re-added PLANTNET enum)
- LenientJsonParser.mergeConcatenatedObjects() for Ollama concatenated JSON output
- Species list images fallback to plant photoUrl

Phase 8.5 resilience:
- Per-stage status (identificationStatus/annotationStatus/candidateStatus) on Identification
- Non-fatal annotation and PlantNet stages — only core stage can fail the record
- PlantNetResponse.predictedOrgans retyped to List<PlantNetPredictedOrgan>
- Token-budget Bucket4j on GitHub Models outbound; finite aiTaskExecutor queue; jittered GOAWAY retry
- PlantNet async fire-and-forget (not on critical path)
- Retry endpoint POST /identifications/{id}/retry
- Stage-aware UI (Overlay unavailable chip, Retry button, failureReason tooltip)

Phase 9.5:
- T9.A: PlantNet candidate harvest → Species (family, genus, imageUrl, identity_source)
- T9.B: GenerationStatus (PENDING/READY/FAILED) on Species.descriptionStatus + Treatment.descriptionStatus; narrowed enrichment to prose only; regenerate endpoints
- T9.C: Species overview redesign — real PlantNet hero, candidate strip, status-driven prose
- T9.D: Treatment description status-driven UI + poll + retry
- T9.E: Dedicated Task Step page (full-width Mermaid, AI deep-links, both call sites switched)
- T9.F: plant-edit back-nav fix (replaceUrl + paramMap subscription)

## Active branches at archive date:
- dev — clean, all phases 0–8.5 merged
- PHASE9 — T9.1–T9.8, pending CI + merge to dev
- PHASE9.5 — T9.A–T9.F, pending merge after PHASE9

## Migration sequence at archive date: 001–029 applied, 030 next free.
