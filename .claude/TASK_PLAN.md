# PlantPal — Task Plan

**Legend:**
- 👤 **Manual** — you do this yourself
- 🤖 **AI** — Claude Code generates entirely from the prompt
- 🤝 **Assisted** — you lead, Claude Code helps
- 💡 **Suggestion** — architectural note

**Branch format:** `feature/PP-{num}-{short-description}`
**Commit format:** `feat(scope): description` (Conventional Commits)

> Completed phases are one-liners — see Archive/TASK_PLAN_3.md for original prompts,
> STATE.md for what was actually built, git history for code.
> **Active phases with full prompts: Phase 10 (Contextual Scanning) and Phase DEPLOY (Launch).**

---

## ✅ COMPLETED PHASES (one-liner per task)

**Phase 0** — Docker Compose, Spring Boot + Angular skeletons, CI/CD, VAPID. (T0.1–T0.6)
**Phase 1** — JWT, Spring Security 6, Plant CRUD + ITs. (T1.1–T1.8)
**Phase 2** — Kafka async identification, gpt-4o vision + DeepSeek-R1, polygon annotation, Redis photos. (T2.1–T2.11)
**Phase 3** — Reminder CRUD + scheduler, VAPID push, actionable care plans + Mermaid diagrams. (T3.1–T3.5; T3.3 manual testing deferred to DEPLOY)
**Phase 4** — AI chat, plant-context injection, SSE streaming + conversation history. (T4.1–T4.3)
**Pre-Phase-5 cleanup** — double-completion fix, duplicate treatment fix, species care cards, chat SSE, 4 ITs, JaCoCo 55%. (`PP-038`)
**Phase 6** — Species entity (shared), Treatment entity, 5-tab nav, Home, species Garden, Plant icon-bar, 3-path identification flow, event-driven lifecycle sync. (T6.1–T6.14)
**Phase 7** — Vision/Reasoning preference split, no-fallback, RateLimitException, model badges, batch scan, multi-treatment picker. (T7.1–T7.4, PP-039–041)
**Phase 8** — PlantNet v2 (ranked candidates, organs, flora, disease cross-check), GBIF/POWO/IUCN, quota, Claude provider. (T8.0–T8.7, PP-042–049)
**Phase 8.5** — Per-stage status D5, non-fatal annotation/candidate stages, predictedOrgans fix, token-budget + queue + jitter, async PlantNet, retry endpoint, stage-aware UI. (T8.A–T8.G, PP-050–056)
**Phase 9** — Frontend unit tests (Jest), Playwright E2E + visual + a11y + Lighthouse, Sentry, supply-chain scanning, ITs + JaCoCo 80%, AI eval suite + prompt-injection. (T9.1–T9.8, PP-057–064)
**Phase 9.5** — PlantNet candidate harvest → Species, GenerationStatus on Species + Treatment, regenerate endpoints, Species overview redesign, Treatment description poll/retry, Task Step page, plant-edit back-nav. (T9.A–T9.F, PP-065–070, migrations 028–029)

---

## PHASE 10 — Contextual Scanning, Scan Redesign & Treatment Polish

> **T10.A–I:** ✅ Complete (PP-071–078), merged to dev — incl. post-phase bugfixes
> (frontend UX `6c10241`, `@Async`+`@Transactional` race `d2c03e3`).
>
> **Goal:** Three interconnected improvements:
> 1. **Contextual input** — user attaches text or voice ("this leaf is very yellow, I put the
>    plant in the bathroom for humidity — did that affect it?") before scanning. The AI incorporates
>    this to give a targeted, relevant response. Voice uses the browser's Web Speech API
>    (transcribed client-side → sent as text, no backend audio handling needed).
> 2. **Scan history redesign** — the Plant page Scan tab becomes a proper history list →
>    clicking any scan opens a dedicated scan detail page (same design as Treatment/Plant pages).
>    The detail page shows the context input, annotated photo, and care cards with redesigned CTAs.
>    Cards from first-identification scans: "Initiate treatment" (direct, no intermediate
>    "ask for cure"). Cards from health scans: "Add to care plan" (draft→confirmed flow);
>    disease cards that have been added → "Initiate treatment"; treatment in progress → tag.
> 3. **Treatment UX polish** — fix auto-progression bug (DRAFT unexpectedly becomes IN_PROGRESS),
>    fix description loss on status change, fix back-nav in step details, add Scenario 1 redirect
>    (first-scan disease card → initiate treatment → goes to Treatment page), update Mermaid
>    prompts for mobile.
>
> **Architecture decisions — confirm before running agents:**
>
> **D10.1 — Annotation merge vs. skip:** User's recommendation: ask for annotation in the SAME
> prompt as identification (single gpt-4o call returns both care plan AND annotation_regions).
> If no disease, annotation_regions is empty/null. Conservative alternative: keep separate calls
> but skip annotation when healthStatus ≠ ISSUES_DETECTED (safe, preserves per-stage tracking).
> **Confirm which approach before T10.A.**
> Rec if conservative: skip-when-no-disease first; full merge as follow-up (bigger prompt change).
>
> **D10.2 — Health-scan card draft state:** Cards from health scans start as "draft" (not yet
> added to the plant's care plan). Draft state = client-side only — no new DB column, no new
> DTO field. The `CareCardComponent` receives an `@Input() isDraft = false`; the scan detail
> page sets it. "Add to care plan" calls the existing `addCareCard()` endpoint and flips the
> local state. Confirm.
>
> **D10.3 — Treatment auto-progression:** Must DIAGNOSE before coding T10.C. The agent reads
> `TreatmentServiceImpl.createTreatment()` + `CareCardComponent` + `TreatmentDetailComponent`
> and reports what is auto-calling `craftPlan()` before writing any fix.
>
> **Migration:** 030 (`user_context` on identifications). Next free PP: 071.

---

### T10.A — Backend: Scan user context field + annotation skip 🤖 AI
**Branch:** `feature/PP-071-scan-user-context`
**Migration:** 030

```
// Phase 10 — T10.A: add optional user-context text to identifications + skip
// annotation when no disease detected (D10.1 conservative option).

// VERIFY migration number first: grep db.changelog-master.xml for the last <include>.
// Sequence ends at 029. Next should be 030. Confirm before writing the file.

1. Migration 030_add_identification_user_context.sql:
   ALTER TABLE identifications ADD COLUMN user_context TEXT;  -- nullable
   Append as the LAST entry in db.changelog-master.xml (never insert between existing).

2. Entity + DTOs:
   - Identification entity: add userContext (String, @Column(name="user_context")).
   - SubmitIdentificationRequest: add userContext (nullable String, no validation).
   - IdentificationEvent (Kafka payload): add userContext field so the consumer has it.
   - IdentificationResponse: expose userContext so the scan detail page can display it.

3. Thread through pipeline:
   - submitIdentification(): copy request.getUserContext() onto the IdentificationEvent
     and onto the saved Identification entity.
   - processIdentification(): pass userContext to the AI client identification calls.

4. AI prompt modification — GitHubModelsClient.identifyPlant(), OllamaClient.identifyPlant(),
   AnthropicClient.identifyPlant():
   When userContext is non-blank, append to the user message (NOT system prompt — user content):
     "The user wants to know: {userContext}. Consider this when assessing health and
      generating care advice — address their specific concern directly."
   When null/blank: no change to prompt.

5. Annotation skip (D10.1 conservative):
   In processIdentification(), after the core identification stage sets healthStatus:
   - If healthStatus == HEALTHY or UNKNOWN: set annotationStatus = SKIPPED, skip
     the analyzeRegions() call entirely, log "healthStatus={status}, annotation skipped".
   - Only call analyzeRegions() when healthStatus == ISSUES_DETECTED.
   Keep annotationStatus tracking exactly as T8.A established — only the condition changes.
   This halves annotation API calls for healthy plant scans at zero additional complexity.

6. Unit tests:
   - submitIdentification(): userContext stored on entity and event.
   - identifyPlant() prompt: includes "The user wants to know: ..." when context present;
     unchanged when null.
   - processIdentification(): annotationStatus=SKIPPED when healthStatus=HEALTHY;
     annotationStatus tracked normally when ISSUES_DETECTED.
   Full unit suite must stay green.
```

---

### T10.B — Frontend: Voice/text context in scan dialog 🤝 Assisted
**Branch:** `feature/PP-072-scan-context-input`
**Depends on:** T10.A merged first (to wire the field into the submit payload).

```
// Phase 10 — T10.B: add optional text/voice context to the photo upload dialog.
// User types "why is this leaf yellow?" OR taps a mic to speak it.
// Web Speech API transcribes voice to text client-side — no backend audio handling.

// Read PhotoUploadComponent (features/identification/) and startIdentification() payload
// shape before starting.

1. PhotoUploadComponent: add an optional "What do you want to know?" section below the
   photo picker (collapsed by default; expandable — context is always optional).
   - Label: "Add context (optional)" with helper text: "Tell the AI what to look for."
   - Textarea: bound to a contextText FormControl (nullable string, no required validation).
   - Character hint: "max 500 characters" (hint annotation, not a hard validation block).
   - Mic button — only rendered when window.SpeechRecognition || window.webkitSpeechRecognition
     is available (feature-detect, not user-agent string):
       - Tap to start → 'Listening…' chip indicator appears.
       - SpeechRecognition: continuous=false, lang=navigator.language, interimResults=false.
       - On result: set contextText to the transcript (replace, don't append).
       - Auto-stops on silence / max speech time. Tap mic again to stop manually.
       - On error (permission denied, no mic): MatSnackBar message ("Microphone unavailable").
         Never crash. The textarea still works without the mic.
     If API unsupported: mic button hidden entirely. Textarea remains.

2. Thread into the identification payload:
   - In startIdentification() (or wherever the /analyze body is assembled), include
     contextText when non-blank as the userContext field.
   - In batch mode: contextText applies to ALL items (users should be aware — a small
     hint "Context applies to all scans" when batch mode is active).

3. ng build + ng lint + tsc --noEmit clean.
   NgModule: PhotoUploadComponent is in IdentificationModule; no new module.
   Do NOT test Web Speech API in unit tests (JSDOM doesn't support it) — mock or skip.
   Do NOT use providedIn:'root' for any new service.
```

---

### T10.C — Backend: Fix treatment auto-progression + description persistence 🤖 AI
**Branch:** `feature/PP-073-treatment-draft-fix`

```
// Phase 10 — T10.C: two related treatment bugs.
// BUG 1: Treatment auto-changes from DRAFT → IN_PROGRESS without the user clicking
//         "Craft Treatment Plan". Description may also be lost in this transition.
// BUG 2: description must survive the DRAFT→IN_PROGRESS transition even if it transitions.

// STEP 0 — DIAGNOSE FIRST, report before patching:
// a) TreatmentServiceImpl.createTreatment(): does it call craftPlan() directly,
//    or fire anything that changes the status beyond DRAFT?
// b) TreatmentController: any auto-transition from POST /treatments → IN_PROGRESS?
// c) CareCardComponent (frontend): after calling TreatmentService.createTreatment(),
//    does it immediately call craftPlan() without waiting for an explicit user action?
// d) TreatmentDetailComponent: does ngOnInit or the description poll auto-trigger craftPlan()?
// Report what you find. If the bug is purely frontend, say so and fix only frontend.

BACKEND (only if backend is culpable):
1. createTreatment() must NEVER call craftPlan() or change status beyond DRAFT.
   Its only side effects: persist the Treatment in DRAFT + fire disease-description
   generation async (fire-and-forget, correct per T6.2, T9.B). Verify + fix if wrong.

2. craftPlan() transition DRAFT → IN_PROGRESS: must NOT clear or overwrite
   diseaseDescription. It is generated independently. The plan adds steps; it doesn't
   replace the description. Verify TreatmentServiceImpl.craftPlan() save call;
   fix if it null-outs diseaseDescription.

FRONTEND (only if frontend is culpable):
3. CareCardComponent: after createTreatment() succeeds, navigate to /treatment/:id.
   Do NOT call craftPlan() from this component. craftPlan() belongs to the user's
   explicit button click on the Treatment page itself.

4. TreatmentDetailComponent: description poll (T7.4) checks status === 'DRAFT' &&
   descriptionStatus === 'PENDING' — it must NOT transition status as a side effect.
   No auto-trigger of craftPlan() from ngOnInit or from the poll callback.

VERIFY: create a treatment (via disease card) → stays DRAFT, description generates async
→ navigate to Treatment page → DRAFT shown + description fills in → click "Craft Treatment
Plan" → IN_PROGRESS + description still visible + steps generated.
mvn test + ng build both clean.
```

---

### T10.D — Frontend: Scan tab → history list 🤝 Assisted
**Branch:** `feature/PP-074-scan-history`
**Depends on:** T10.A (for userContext in response), T10.C (stable treatment state).

```
// Phase 10 — T10.D: redesign the Plant page Scan tab from its current content into a
// proper history list, matching the existing identification-list design language.

// STEP 0 — DIAGNOSE: Read plant-detail.component.ts (the current Scans section), the
// existing identification.service.ts (find getPlantIdentifications or equivalent), and
// the existing identify-list component that shows the history. Report:
// - Current Scans section: what component, what service call, what template.
// - What "existing history list" looks like — find the identify-list design.
// Confirm before building.

1. Scan tab: vertical list of all identifications for this plant, newest-first.
   Each list item (match the identify-list design exactly — same card/row shape):
   - Scan thumbnail (identificationResponse.photoUrl or placeholder)
   - Date: relative ("2 days ago") with absolute on hold/tooltip
   - Health status chip: HEALTHY (green) / ISSUES_DETECTED (amber) / UNKNOWN (grey)
     — reuse HealthBadge tokens if the util exists
   - Care card count: "N cards" badge when > 0
   - User context preview (if userContext non-blank): first 40 chars + "…", grey italic
   - Active treatment tag: if an active Treatment exists for any disease from this scan,
     show "Treatment in progress" tag (derive from getActiveTreatments(plantId) cached
     from the plant-detail already-loaded state — no extra HTTP call)

2. Empty state: "No scans yet. Tap the camera icon to scan this plant."

3. Click → navigate to /plants/:plantId/scans/:scanId (T10.E route, next task).
   No router state — detail loads purely from route params.

4. Pagination: if the service returns Page<>, render first 20 + "Load more" button.
   If plain list, render all.

5. ng build + ng lint clean. Keep the *ngSwitch activeSection structure — only the
   scans section content changes.
```

---

### T10.E — Frontend: Dedicated scan detail page 🤝 Assisted
**Branch:** `feature/PP-074-scan-history` (same branch as T10.D)
**Depends on:** T10.A (userContext field), T10.D (navigation source).

```
// Phase 10 — T10.E: new dedicated scan detail page at /plants/:plantId/scans/:scanId.
// Design: sticky-header + content sections, same language as Treatment/Plant detail.
// Shows the user's context, annotated photo, and care cards in one coherent view.

// Read treatment-detail.component.ts/html/scss (T6.12) for layout pattern.
// Read the existing annotated-photo + care-plan rendering (identification-result or
// preview-card components) before starting.
// Confirm plant routing structure (plant.module.ts, plant-routing.module.ts).

// STEP 0 — CONFIRM DATA SOURCE:
// a) Does GET /api/v1/identifications/:id exist and return the full payload?
// b) Does IdentificationResponse include care_plan (care cards), annotation_regions, userContext?
// c) What field signals the scan type (Flow 1/2 = identification vs Flow 3 = health check)?
//    Look for a field like 'flow', 'plantId-was-known', or derive from whether plantId
//    was null at scan creation time. Report before building.

1. Route: /plants/:plantId/scans/:scanId
   Add to plant-routing.module.ts. Component: ScanDetailComponent in features/plant/.

2. ngOnInit: load identification by scanId (IdentificationService.getById(scanId)).
   Verify plantId matches route param (if mismatch, navigate back with error).
   Also load getActiveTreatments(plantId) to know which diseases are being treated.

3. Layout:
   a) STICKY HEADER (same IntersectionObserver + .collapsed pattern as Plant/Treatment page):
      - Hero image: the scan's photo (identificationResponse.photoUrl)
      - Scan date (formatted) + health status chip
      - Collapses to smaller photo + condensed date on scroll
   
   b) USER CONTEXT BLOCK (only if userContext non-blank):
      Small card below header (not part of sticky):
      mic/text icon + "What you asked:" label + full userContext text
   
   c) ANNOTATED PHOTO:
      Reuse PhotoAnnotatorComponent (or equivalent polygon-overlay component).
      If annotationStatus = FAILED or SKIPPED: show "Overlay unavailable" chip (T8.G pattern).
      If annotationStatus = PENDING: show spinner (still processing).
   
   d) CARE CARDS (same CareCardComponent as everywhere else):
      Determine scan flow from the data (STEP 0 finding):
      
      FIRST IDENTIFICATION (Flow 1/2 — species was being determined):
        - Disease/pest cards: "Initiate treatment" button directly.
          On click: POST /treatments → on success → navigate(/treatment/:id).
          No intermediate "ask for cure" step.
        - Non-disease cards: display only (no action CTA).
        - @Input() showTreatmentCta=false on CareCardComponent (the existing input from T7
          follow-up), @Input() isDraft=false.
      
      HEALTH SCAN (Flow 3 — plant was already known):
        - All cards start as draft (@Input() isDraft=true).
        - "Add to care plan" button on each:
          → calls existing addCareCard() endpoint
          → on success: flip isDraft=false locally (no reload)
          → if card type is disease/pest: button changes to "Initiate treatment"
            (show after isDraft cleared, same click → POST /treatments → navigate).
          → if active Treatment already exists for this disease: show "Treatment in
            progress" tag instead of any CTA (use the getActiveTreatments() result
            loaded in ngOnInit to check).
        - CareCardComponent: add @Input() isDraft = false and @Input() treatmentActive = false
          to drive the CTA + tag display. Gate the existing checkActiveTreatment() HTTP call
          behind !isDraft to avoid unnecessary calls for draft cards.

4. BACK NAVIGATION:
   Back button (a plain back arrow in the sticky header, not router-link):
   → navigate to /plants/:plantId?section=scans
   plant-detail must handle this ?section= param on init to activate the right section
   (add queryParams subscription to plant-detail if not already present — T10.F handles this).

5. Add ScanDetailComponent to plant.module.ts declarations.
   IdentificationService + TreatmentService are already provided in plant.module — no new providers.

6. ng build + ng lint + tsc --noEmit clean.
```

---

### T10.F — Frontend: Treatment UX polish + back-nav fixes 🤝 Assisted
**Branch:** `feature/PP-075-treatment-ux-polish`
**Depends on:** T10.C (treatment bug fix). Independent of T10.D/T10.E.

```
// Phase 10 — T10.F: four small but high-impact UX fixes.

// Read treatment-detail.component.ts, plant-detail.component.ts, the step-detail page
// (from T9.E), and all three AI system prompt constants before starting.

// FIX 1 — Back from step detail → plan tab.
// When the Treatment page navigates to the step-detail page (T9.E), pass
// ?returnUrl=/treatment/:id?section=plan so step-page's Back button returns to the
// treatment's plan section, not the overview.
// treatment-detail must read ?section=plan on init and set activeSection='plan' if present.
// The ?section= param pattern is the same as what T10.E sets for scan navigation.

// FIX 2 — plant-detail: respond to ?section= query param on init.
// plant-detail.component.ts: in ngOnInit, subscribe to ActivatedRoute.queryParams;
// if 'section' is present and valid (matches an icon-bar section key), set activeSection.
// This makes Back from ScanDetailComponent (T10.E step 4) land on the Scans section,
// and Back from step-detail (Fix 1 above) land on the plan section in Treatment detail.
// NOTE: treatment-detail is a separate component — fix both separately.

// FIX 3 — Scenario 1 redirect (first-scan disease → initiate treatment → Treatment page).
// CareCardComponent: when "Initiate treatment" is clicked (the existing createTreatment()
// call path on the Plant page Overview/Care section):
// → on success: navigate to /treatment/:treatmentId.
// Currently it may just show a toast. Add the navigate() call after success.
// This is the same redirect that T10.E wires for the scan-detail page; confirm both paths
// use the same pattern.

// FIX 4 — Mobile Mermaid prompt constraints + rendering.
// BACKEND: in GitHubModelsClient (PLANT_IDENTIFICATION_SYSTEM_PROMPT),
// DeepSeekClient (CARE_PLAN_SYSTEM_PROMPT, CURE_ADVICE_SYSTEM_PROMPT):
// Add/extend the existing Mermaid constraint block with:
//   "Mermaid diagrams must be optimized for mobile screens (narrow portrait viewports):
//    - Use flowchart TD (vertical, top-down) — never LR on mobile.
//    - Maximum 5 nodes per diagram.
//    - Node labels: max 15 characters; abbreviate if needed (e.g. 'Apply fungicide' → 'Apply fung.').
//    - No subgraphs, no click events, no style blocks, no double quotes in labels.
//    - Simple linear flows; branching at most 2 levels deep."
// FRONTEND: MermaidDiagramComponent (shared/):
// - Ensure the host element has width:100% and overflow-x:auto so wide diagrams
//   scroll horizontally rather than overflowing. Add if missing.
// - Do NOT add pan/zoom (over-engineering for 5-node diagrams).

ng build + ng lint + tsc --noEmit clean.
Backend: mvn test clean (prompt string changes are string-only, no logic change).
```

---

### T10.G — Frontend: Fix voice input mic permissions 🤖 AI
**Branch:** `feature/PP-076-voice-permission-fix`
**Scope:** `PhotoUploadComponent` only — no backend, no new module.

```
// Phase 10 — T10.G: the mic button in PhotoUploadComponent uses Web Speech API but
// may fail silently or show an unhelpful error on devices where mic permission hasn't
// been explicitly granted. Fix the permission flow and improve error messages.

// READ photo-upload.component.ts (features/identification/components/photo-upload/)
// before writing any code — the current startListening() is already implemented.
// Do NOT rewrite what already works; only patch the permission-check gap.

ROOT CAUSE TO FIX:
  Web Speech API's recognition.start() triggers an OS mic prompt in some browsers
  but NOT in others (especially mobile WebKit). If the user previously dismissed
  the prompt without explicitly allowing/denying, recognition.start() may silently
  error with 'not-allowed' and the snackbar just says "Microphone unavailable"
  — no guidance on how to fix it.

FIX — modify startListening() in PhotoUploadComponent:

1. Add component property: requestingPermission = false.
   In the template: update the mic button's [disabled] to also disable during
   requestingPermission (so the user can't double-click). The listening-chip
   'Listening…' is shown by the existing 'listening' flag — add a second chip
   (or swap the text) for 'Requesting access…' when requestingPermission=true.

2. Modify startListening() to pre-check mic permission using
   navigator.mediaDevices.getUserMedia BEFORE creating the SpeechRecognition:

   private startListening(): void {
     if (!SpeechRecognitionAPI) return;
     if ('mediaDevices' in navigator && navigator.mediaDevices.getUserMedia) {
       this.requestingPermission = true;
       navigator.mediaDevices.getUserMedia({ audio: true })
         .then(stream => {
           // Permission granted — stop the audio tracks immediately (we don't
           // need the audio data, only the permission grant). Then start recognition.
           stream.getTracks().forEach(t => t.stop());
           this.requestingPermission = false;
           this.doStartRecognition();
         })
         .catch((err: DOMException) => {
           this.requestingPermission = false;
           if (err.name === 'NotFoundError' || err.name === 'DevicesNotFoundError') {
             this.snackBar.open('No microphone found on this device.', 'Dismiss',
               { duration: 5000 });
           } else {
             // NotAllowedError / PermissionDeniedError / SecurityError
             this.snackBar.open(
               'Microphone access blocked — tap the lock icon in your browser\'s ' +
               'address bar and allow microphone access, then try again.',
               'Dismiss', { duration: 8000 });
           }
         });
     } else {
       // Older browser / HTTP (non-localhost) — fall back to current behavior;
       // recognition.onerror handles 'not-allowed' if it fires.
       this.doStartRecognition();
     }
   }

3. Extract the SpeechRecognition setup into doStartRecognition() (private):
   Identical to the current body of startListening() after the getUserMedia check.
   The onerror handler inside doStartRecognition() can be simplified to only handle
   non-permission errors (e.g. 'network', 'audio-capture') since the permission
   case is now caught by the getUserMedia rejection above. Keep the 'not-allowed'
   branch in onerror too as a fallback (belt-and-suspenders).

4. stopListening() and ngOnDestroy(): no change needed.

5. Template additions:
   - Add [disabled]="listening || requestingPermission" to the mic button.
   - Add a second chip below the 'Listening…' chip (or replace it) when
     requestingPermission=true: same style, text "Requesting access…", mic icon.

Do NOT add getUserMedia audio to the actual context recording — this is purely a
permission pre-check. Do NOT add any new Angular services. Do NOT change the textarea.

ng build + ng lint + tsc --noEmit clean.
No unit tests needed for getUserMedia (JSDOM doesn't support it).
```

---

### T10.H — Frontend: TTS "Read aloud" button across all content surfaces 🤖 AI
**Branch:** `feature/PP-077-read-aloud`
**Depends on:** T10.G (same branch cycle) — independent of T10.G, can run in parallel.

```
// Phase 10 — T10.H: add a Web Speech Synthesis API "read aloud" speaker button to
// every text-heavy content surface in the app so users can listen to care advice,
// species descriptions, treatment info, task steps, and reminders hands-free.

// READ shared.module.ts + treatment-step-list.component.ts + care-card.component.ts
// BEFORE writing any code. Understand the SharedModule exports list (add new
// component to it) and the surfaces listed below.

// ════════════════════════════════════════════
// STEP 1 — SpeechService (shared/services/)
// ════════════════════════════════════════════
// File: frontend/src/app/shared/services/speech.service.ts
// providedIn: 'root' — one global singleton; cancels prior speech on any new call.

@Injectable({ providedIn: 'root' })
export class SpeechService implements OnDestroy {
  readonly isSupported = 'speechSynthesis' in window;
  speaking = false;
  currentText = '';

  speak(text: string): void
    // 1. window.speechSynthesis.cancel() to stop any current utterance.
    // 2. If text blank or !isSupported: return.
    // 3. Create SpeechSynthesisUtterance(text):
    //    - utterance.rate = 0.95 (slightly slower for plant advice readability)
    //    - utterance.lang = document.documentElement.lang || navigator.language
    //    - utterance.onstart  → this.speaking = true; this.currentText = text
    //    - utterance.onend    → this.speaking = false; this.currentText = ''
    //    - utterance.onerror  → this.speaking = false; this.currentText = ''
    // 4. window.speechSynthesis.speak(utterance)

  stop(): void
    // window.speechSynthesis.cancel(); this.speaking=false; this.currentText='';

  isReadingText(text: string): boolean
    // return this.speaking && this.currentText === text;

  ngOnDestroy(): void { this.stop(); }
}

// ════════════════════════════════════════════
// STEP 2 — ReadAloudButtonComponent (shared/components/read-aloud-button/)
// ════════════════════════════════════════════
// Selector: app-read-aloud-button
// Template: a mat-icon-button, hidden entirely when !speechService.isSupported.
// @Input() text: string      — the full text to speak (parent assembles it).
// @Input() ariaLabel = 'Read aloud'

// Template (inline is fine — very short):
//   <button *ngIf="speechService.isSupported"
//     mat-icon-button type="button"
//     [attr.aria-label]="isReading ? 'Stop reading' : ariaLabel"
//     [matTooltip]="isReading ? 'Stop reading' : 'Read aloud'"
//     [class.reading]="isReading"
//     (click)="toggle()">
//     <mat-icon>{{ isReading ? 'stop_circle' : 'volume_up' }}</mat-icon>
//   </button>

// CSS (.reading): color the icon with the app's primary green to signal active state.
// Size: the button inherits the surrounding font context — no explicit size override.

// Component logic:
//   get isReading(): boolean { return this.speechService.isReadingText(this.text); }
//   toggle(): void {
//     if (this.isReading) { this.speechService.stop(); }
//     else { this.speechService.speak(this.text); }
//   }

// Declare + export in SharedModule (add to declarations[] AND exports[]).
// SpeechService is providedIn:'root' — SharedModule does NOT add it to providers[].

// ════════════════════════════════════════════
// STEP 3 — Wire into content surfaces
// ════════════════════════════════════════════
// For each surface below: add <app-read-aloud-button [text]="..."> adjacent to the
// heading or the text block it covers. Keep the button unobtrusive — icon only,
// same line as the heading where possible, NOT in a separate row.

// ── 3a. CareCardComponent (care-card.component.html) ─────────────────────────
// Add the button in the card header row, next to the card title text.
// Text: card.title + '. ' + (card.advice ?? '')
// ariaLabel: 'Read aloud ' + card.title
// The header row already has a mat-icon for the card type — place the read-aloud
// button after it on the right (push with flex spacer or position absolute-right).

// ── 3b. SpeciesDetailComponent overview tab ───────────────────────────────────
// Find the description block (species.description prose) in species-detail.
// Add the button next to the "About this species" or equivalent section heading.
// Text: species.description (may be null while generating — guard: *ngIf="species.description")
// Only show when descriptionStatus === 'READY' (don't offer to read "Generating…" filler).

// ── 3c. TreatmentDetailComponent ─────────────────────────────────────────────
// Find the disease description block (treatment.diseaseDescription).
// Add button next to the "About this disease" or description heading.
// Text: treatment.diseaseDescription
// Guard: *ngIf="treatment.diseaseDescription && treatment.descriptionStatus === 'READY'"

// ── 3d. TreatmentStepListComponent (treatment-step-list.component.html) ───────
// For EACH step row: add a read-aloud button inline with the step instruction text.
// Text: 'Step ' + step.stepOrder + ': ' + stepInstruction(step)
//       + (step.stepDetail ? '. ' + step.stepDetail : '')
// Use the existing stepInstruction(step) helper from the component.
// The step row already has a "Mark done" button and optional "detail" icon button —
// place read-aloud as the LAST icon in that row (smallest visual weight).

// ── 3e. Dashboard reminders (dashboard feature) ────────────────────────────────
// Locate the care-due reminder list on the Home/Dashboard page.
// Add button per reminder row. Text: reminder.instruction ?? reminder.careType
// Only if instruction is available (guard: *ngIf="reminder.instruction").
// If the dashboard uses a compact card, add button to the card's action row.

// ── 3f. ScanDetailComponent userContext block ──────────────────────────────────
// The "What you asked:" block (added T10.E) that shows the user's context text.
// Add button immediately after the userContext text.
// Text: identification.userContext
// Guard: *ngIf="identification.userContext"

// ════════════════════════════════════════════
// STEP 4 — Build checks
// ════════════════════════════════════════════
// ng build clean (no TS errors, no unused imports).
// ng lint clean.
// tsc --noEmit clean.
// Do NOT write unit tests for SpeechSynthesis — JSDOM does not support it; mock or skip.
// The SharedModule exports ReadAloudButtonComponent — verify it's consumable in at
// least two separate lazy feature modules (IdentificationModule, PlantModule) without
// a module re-provide (it should be fine as the component uses a root-provided service).
```

---

### T10.I — Frontend: Fix SpeechRecognition mic not working 🤖 AI
**Branch:** `feature/PP-078-mic-bug`
**Depends on:** T10.G (merged — getUserMedia pre-check is in place but mic still fails).

```
// Phase 10 — T10.I: after T10.G landed, user reports "Microphone unavailable" is still
// shown when tapping the mic button in the scan context section. T10.G added getUserMedia
// pre-check and better error messages, but the root cause of the underlying SpeechRecognition
// failure was not fixed — only the diagnostic path improved.

// STEP 0 — DIAGNOSE BEFORE FIXING:
// a) Open browser DevTools Console while tapping mic. Report the exact event.error value
//    from the SpeechRecognition onerror handler ('not-allowed', 'audio-capture', 'network',
//    'service-not-allowed', etc.) and the browser + OS.
// b) Is the app running on HTTP or HTTPS? SpeechRecognition in Chrome requires HTTPS
//    (exception: localhost). If running on a LAN IP (e.g. 192.168.x.x) over HTTP,
//    SpeechRecognition is blocked by browser policy — getUserMedia will succeed (it has
//    its own permission) but SpeechRecognition still fires 'not-allowed' or 'service-not-allowed'.
// c) Is the browser Firefox? Firefox does NOT implement SpeechRecognition
//    (window.SpeechRecognition and window.webkitSpeechRecognition are both undefined).
//    The mic button should be hidden — if it isn't, speechSupported is incorrectly true.
// d) Was mic permission previously denied in the browser's site permissions? A hard denial
//    blocks getUserMedia AND SpeechRecognition. The user must manually re-allow in browser
//    site settings — our app cannot override this.

// MOST LIKELY FIX (HTTP/LAN scenario):
// The dev server is accessed over LAN HTTP (http://192.168.x.x:4200), not localhost.
// SpeechRecognition is only available on HTTPS or localhost. getUserMedia works because
// it has a separate (lower) permission threshold.
//
// FIX OPTIONS:
// Option A — dev only: access via http://localhost:4200 instead of LAN IP.
// Option B — prod-ready: the Angular dev server can serve HTTPS with a self-signed cert
//   (ng serve --ssl). Document this in README / .env.example for mobile testing.
// Option C — graceful degradation: detect the insecure context and hide the mic button
//   entirely (window.isSecureContext === false AND host !== 'localhost').
//
// Implement Option C regardless (prevents the broken state from ever reaching users):
//   Update speechSupported check in PhotoUploadComponent:
//   readonly speechSupported = !!SpeechRecognitionAPI && (
//     window.isSecureContext || location.hostname === 'localhost'
//   );
//   This hides the mic button on plain HTTP LAN access but preserves it on localhost dev
//   and any HTTPS deployment. Also add a dev-only console.warn when SpeechRecognitionAPI
//   exists but isSecureContext is false, so the cause is obvious in DevTools.

// ng build + ng lint clean.
// No unit tests needed (browser API mocking in JSDOM is already excluded).
```

---

## PHASE DEPLOY — Launch Preparation 🟡 IN PROGRESS
> **Runs last** — after all numbered phases merged to dev.
> Goal: deploy to production, beta-test, release v1.0.0.
> **T-DEPLOY.1–4 ✅ complete, merged to dev (PR #125, 2026-07-15)** — actual branches were
> PP-089 (T-DEPLOY.1) + PP-090 (T-DEPLOY.2–4); the PP-079/080 numbers below were consumed
> by platform-delta work in the meantime. Migration used was 032, not 031 (031 = business
> tier). T-DEPLOY.5–8 remain — all manual-gated (Kafka decision, GITHUB_TOKEN rotation,
> Railway/Vercel, beta, release).

### T-DEPLOY.1 — Production configuration 🤖 AI ✅ (PP-089)
**Branch:** `feature/PP-089-prod-config` (plan said PP-079)
```
1. application-staging.yml + application-prod.yml: ${DATABASE_URL}, HikariCP
   (max 20/min 5/timeout 20000), ${REDIS_URL}, Liquibase enabled, show-sql false,
   logging INFO com.plantpal/WARN spring/JSON, Actuator health+info only,
   JPA ddl-auto=validate, CORS from ${ALLOWED_ORIGINS}.
2. logback-spring.xml: JSON in staging/prod, pattern in dev, MDC correlationId
   (timestamp, level, correlationId, userId, message, exception) on every line.
   This correlationId is what T9.6's Sentry integration links against.
3. app.rate-limit.* (ai-calls-per-hour 20, auth-attempts-per-minute 5,
   chat-messages-per-hour 10).
```

### T-DEPLOY.2 — Performance optimizations 🤝 Assisted ✅ (PP-090)
**Branch:** `feature/PP-090-perf-security-docs` (plan said PP-080)
```
1. Migration (VERIFY next free — 030 used by T10.A user_context; so 031 here):
   composite idx identifications(plant_id, created_at DESC); partial idx
   reminders(next_due_at) WHERE enabled; idx care_logs(user_id, performed_at DESC);
   idx treatments(plant_id, status) + species(scientific_name) if not covered.
2. @Cacheable: ChatServiceImpl.buildGardenContext ("garden::{userId}", 5 min),
   SpeciesServiceImpl.getSpecies ("species::{id}", 10 min). @CacheEvict on mutations.
3. Angular: verify lazy-loading (ng build --stats-json), OnPush on list components,
   trackBy on *ngFor. (Lighthouse budgets from T9.3 now guard this.)
```

### T-DEPLOY.3 — Security hardening 🤖 AI ✅ (PP-090)
**Branch:** `feature/PP-090-perf-security-docs` (same branch). GITHUB_TOKEN rotation (#4) still manual/outstanding.
```
1. Security headers in SecurityConfig: X-Content-Type-Options nosniff, X-Frame-Options
   DENY, HSTS (prod only), basic CSP for own origins.
2. Bucket4j on AuthController: login 5/min/IP, register 3/hour/IP, 429 + Retry-After.
3. Input sanitization: plant nickname/notes/location strip HTML (OWASP Java HTML Sanitizer);
   chat messages max 2000 chars at controller level. (Prompt-injection: T9.8.)
4. Confirm GITHUB_TOKEN rotation from T9.7 happened before prod.
```

### T-DEPLOY.4 — Complete API documentation 🤖 AI ✅ (PP-090)
**Branch:** `feature/PP-090-perf-security-docs` (same branch)
```
@Operation/@ApiResponse/@Parameter on every controller (all modules including Phase 8
PlantNet proxy/quota endpoints). @ApiResponse for 200/201/400/401/403/404/429/500.
@Schema examples on key DTOs. OpenApiConfig: "PlantPal API" v1.0.0, JWT authorize
button, dev + prod server URLs.
```

### T-DEPLOY.5 — Production deployment 👤 Manual
Railway (Postgres + Redis add-ons, env vars from `.env.example`, auto-deploy on `main`)
+ Vercel (Angular build, prod API URL). Verify `/actuator/health` UP, login works, identification completes.
> **Decide first:** Kafka/Zookeeper production story — managed add-on, or synchronous
> identification fallback for v1.0.0 (revisit async at scale). Still open.

### T-DEPLOY.6 — Beta testing 👤 Manual
5–10 plant owners. Full journey (Flow 1/2/3), disease path, mobile (Chrome Android +
**Safari iOS** — covered by T9.2 WebKit E2E), PWA install (closes **T3.3**), chat.

### T-DEPLOY.7 — Beta bug fixes 🤝 Assisted
Per bug: `bugfix/PP-{N}` from `dev`. Add a failing test first (T9.1/T9.2 harness now
exists). Fix. PR with root cause.

### T-DEPLOY.8 — Release v1.0.0 👤 Manual
`release/v1.0.0` from `dev`, `mvn versions:set 1.0.0`, CHANGELOG, merge `--no-ff` to
`main`, tag `v1.0.0`, merge back to `dev`, delete release branch.

---

## Status Summary

| Phase | Status |
|---|---|
| 0–10 | ✅ All complete (merged to dev) |
| DEPLOY — Launch | 🟡 T-DEPLOY.1–4 ✅ (PP-089/090, PR #125); 5–8 manual-gated |

---

## Enterprise Patterns Checklist (pre-DEPLOY audit)
Backend:
- [ ] All list endpoints paginated (Pageable)
- [ ] All deletes soft (status = ARCHIVED)
- [ ] Audit fields on all entities (see CLAUDE.md exceptions)
- [ ] Redis cache on hot read paths
- [ ] Rate limiting on AI + auth endpoints
- [ ] ITs wired into mvn verify (T9.5 ✅)
- [ ] JaCoCo 80% (T9.5 ✅)
- [ ] Raw AI responses stored
- [ ] ResourceNotFoundException never reveals existence vs. ownership
- [ ] Kafka prod story decided (T-DEPLOY.5)

Security:
- [ ] Secrets in env vars — GITHUB_TOKEN rotated (T9.7)
- [ ] Secret scanning gitleaks CI (T9.7 ✅)
- [ ] OWASP + Dependabot + Trivy (T9.7 ✅)
- [ ] Security headers (T-DEPLOY.3)
- [ ] Input sanitization + prompt-injection (T-DEPLOY.3 / T9.8 ✅)

Quality:
- [ ] Frontend unit tests (T9.1 ✅)
- [ ] E2E Playwright + WebKit (T9.2 ✅ code; CI disabled pending ng serve setup)
- [ ] Visual regression + a11y + Lighthouse (T9.3 ✅ code; Lighthouse CI disabled)
- [ ] Sentry + correlation IDs (T9.6 ✅)
- [ ] Structured JSON logging (T-DEPLOY.1)
- [ ] AI eval suite nightly (T9.8 ✅)

Docs / ops:
- [ ] Swagger every endpoint (T-DEPLOY.4)
- [ ] Docker + docker-compose for local dev ✅
