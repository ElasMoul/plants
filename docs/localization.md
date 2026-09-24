# PlantPal languages

The standard user app supports EN, FR and AR. Registration detects the first supported
browser language unless the user chooses one. The language is saved on the account;
settings/header changes save it before reloading. Login restores the account preference.
Admin and Atlas remain outside this localization rollout.

## Interface and plant content

Changing language updates static interface text, dates and layout. Arabic uses RTL.
Existing plant content automatically displays a saved version in the selected language.
If that version is missing, its current text remains until explicitly translated. Scientific names, nicknames and
user notes remain unchanged. New AI content is prepared in the saved account language;
English canonical records are retained. Existing plants are never batch translated.

Each plant section has a translate icon beside read-aloud (or at the section start
when there is no audio control). There are no section language dropdowns.
- The icon is visible only when displayed text differs from the account/app language.
- Saved target versions display automatically without any AI request; clicking requests a missing translation.
- Once the section matches the selected app language, the icon disappears.
- Each section retains at most one EN, FR and AR version, created progressively.
- On app language changes, a saved target takes priority; the icon appears only if that target is missing.

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

Translation progress/failure messages sit below care-card headers, keeping their text and audio/translation icons readable on narrow screens.
