# PlantPal languages

The standard user app supports EN, FR and AR. Registration detects the first supported
browser language unless the user chooses one. The language is saved on the account;
settings/header changes save it before reloading. Login restores the account preference.
Admin and Atlas remain outside this localization rollout.

## Interface and plant content

Changing language updates static interface text, dates and layout. Arabic uses RTL.
Existing plant content keeps its selected version. Scientific names, nicknames and
user notes remain unchanged. New AI content is prepared in the saved account language;
English canonical records are retained. Existing plants are never batch translated.

Each plant section has a compact language control:
- **Translate to FR/AR** appears only when that target version is missing or failed.
- The initials selector switches between saved versions without another AI call.
- Each section has at most one EN, FR and AR version, created progressively.
- A section's selected version survives an app language change in that browser.

Controls cover plant/common names, health and annotation text, species descriptions,
care overviews/cards/warnings, cure advice, treatment descriptions/names, plan titles,
diagrams and individual steps. Cards merged from older scans keep their original
section identity. Adding a new card does not translate the older cards.

Read-aloud uses the displayed section language and explicitly selects a matching
system voice. If no matching voice is installed, it displays an error. Device voice
availability still depends on the browser/OS. New chat replies use the app language.

## Translation behavior

There is no yellow global translation notice and opening an old plant never starts
translation. Explicit translation uses configured native hosted DeepSeek first
(`DEEPSEEK_HOSTED_API_KEY`, `DEEPSEEK_HOSTED_BASE_URL`), with Anthropic/Ollama fallback.
Cached reads consume no AI allowance; newly translated batches consume AI allowance,
not scan allowance. Failed jobs require an explicit retry. Status checks only observe
an existing job and stop after two minutes in the browser. Interrupted jobs become
retryable after the existing server stale-job window/cooldown.

English source text stays intact. Translation preserves scientific names, warnings,
quantities and units; numeric values and units are validated before saving. A changed
source invalidates its stale translated versions without creating additional language
slots. Translation does not perform another identification or diagnosis.

## Implementation

Migration 038 stores users.language. Migration 039 adds plant_text_sections,
plant_text_versions and generated_cure_advice. The per-section language primary key
and language constraint allow at most three variants. Existing ai_translations jobs
provide reusable provider results; no historical data migration is performed.

GET `/api/v1/content-sections/{kind}/{id}/{section}` resolves authorized server content
and reads metadata. POST on its `/translate` child uses the saved account target.
Client-authored translation text is never accepted. GeneratedTranslationListener
prepares new content after generation; card/annotation updates specify their section.
The old X-Content-Language response-localization mechanism is removed.

Frontend SectionLanguageDirective supplies section-local display state to aiText,
aiDetail, aiDiagram and read-aloud. LanguageService handles static catalogs and account
settings separately. Selected content language persists per section in localStorage.

## Validation and preview

Full backend verify passed: 485 unit tests, 55 integration passes, 3 optional live eval
skips, coverage, Checkstyle and unfiltered Spotless. Frontend: 572 tests, production
build, lint and 17 Chromium language/admin/session journeys passed. Existing bundle
and CommonJS warnings remain. Translation-provider results were mocked in this run.

Preview: http://127.0.0.1:4210 (backend 8190). Branch:
`codex/explicit-section-translations`. Owner testing precedes publishing or merging.
