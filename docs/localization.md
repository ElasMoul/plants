# PlantPal languages

The standard user app supports English, French and Modern Standard Arabic. Choose
**EN**, **FR** or **AR** in the compact header selector or language settings.
The selector has no globe symbol; native-language titles identify each option. The header selector is also
available before login. With no saved choice, the app detects the first supported
browser language (en/fr/ar). Registration saves it on the account; a manual choice
overrides detection. Signed-in changes are saved before reloading, and login restores
the account language. The local browser choice remains available after logout.
Changing language reloads the current page; save unfinished edits first.

This rollout translates the user interface, forms, common messages, calendars,
dates, pagination, and accessibility labels. Voice input and read-aloud use the
selected document language. User-entered content, scientific names and the separate
Pl@ntNet common-name language setting are preserved. AI descriptions, care cards,
health notes, treatment advice and step instructions have saved French and Arabic display versions.
New chat replies are generated directly in the selected language. Unknown server messages may remain English.
The admin console and Atlas are not translated in this rollout.

## Development

Messages live in `frontend/src/app/shared/i18n/fr.ts` and `ar.ts`, keyed by their
English source. Arabic has all 590 catalog entries, with matching placeholders.
Use `{{ 'Save changes' | t }}` for templates and `translate('Save changes')` for
application-owned TypeScript messages. Use numbered parameters for dynamic values,
not concatenated translated fragments. Never translate API values or comparisons.
English fallback allows new copy to remain readable, but add its French translation
in the same change. Tests check numbered placeholder parity and language persistence.

`LanguageService` owns supported languages, locales and document direction. Locale
providers and module-level labels initialize at startup, so language changes reload.
The account language is persisted; anonymous visitors retain a browser-local choice. Migration 036 adds persistent AI translation jobs; migration 037 permits Arabic;
migration 038 adds the account language and translation reuse index, without rewriting plants.

Arabic sets document direction to RTL and uses the ar-MA locale for dates and speech.
Logical CSS spacing and positioning mirror the user interface; navigation arrows
mirror while email, password and code inputs remain LTR. Counted plant, tip, day
and issue messages handle Arabic zero, singular, dual, few and many forms.
Atlas remains separate future work.

## Validation

Production build passed (existing bundle/CommonJS warnings); 572 frontend unit tests, 485 backend unit tests, 51 backend integration tests
(including eight translation tests) and 18 Chromium journeys passed. Three optional
live evaluation tests remain skipped. Full backend verify includes Spotless and coverage. These cover language switching, translated saved content, pending
jobs, failure/retry UI, ownership, cache reuse and existing admin workflows.
Hosted DeepSeek was also verified using synthetic plant-care text. See `docs/screenshots/french-plant-form.png`.

## AI content in French and Arabic

Select FR or AR, then open an existing scan, species page or treatment. The first
view prepares a version in the selected language in the background and shows a progress notice.
Subsequent views reuse the saved translation. Switching to English shows the original.
This also applies to newly generated analyses, without repeating identification.
User notes and plant nicknames remain as entered. Diagram labels and read-aloud use
translated text; diagram identifiers and the underlying treatment data stay intact.

Translation uses configured native hosted DeepSeek first (DEEPSEEK_HOSTED_API_KEY
and DEEPSEEK_HOSTED_BASE_URL), then Anthropic or Ollama when available. Each new
translation batch consumes the user's AI allowance, never their scan allowance;
cached reads consume neither. Shared species translations can be reused across users.
Private scan and treatment translations remain scoped to their owner.

If a provider or quota prevents translation, the original text remains visible with
a French notice and retry control. Failed jobs are not automatically billed again on
every view. Retry has a short cooldown; an interrupted pending job can be retried
once its ten-minute stale window expires. Numeric values and measurement units are
validated before saving; translation is instructed to preserve botanical names and
warnings and must not perform a fresh diagnosis.

The API keeps canonical data unchanged and adds localization metadata for requests
with X-Content-Language: fr or ar. Cache keys include language; retries retain
the saved target, so Arabic never overwrites French. The frontend polls the translation job, then applies
text only at display time. It never submits translated DTOs back as domain changes.
Arabic AI output and RTL are included. Atlas remains future work.


Arabic preview screenshot: `docs/screenshots/arabic-plant-form.png`.
The owner will test this feature branch before approving a merge to dev.


## Account language and new Arabic content

The account preference is `users.language` (migration 038), exposed at registration,
auth responses and `/users/me/preferences`. Supported values are en/fr/ar. Existing
accounts default to English until changed; existing plants/scans are never backfilled.

For Arabic-preferring users, successful new scan generation automatically schedules
saved Arabic text alongside the canonical English source. The same applies to newly
generated cure advice, treatment descriptions, plan steps, annotation retries, and
species prose associated with an Arabic user's scan. Arabic common names are saved
in the same source-to-translation records and rendered on plant/scan/species views;
Latin scientific names and user nicknames remain unchanged. There is no startup sweep.
Changing language affects subsequent generation; it does not regenerate old analyses.

`GeneratedTranslationListener` handles content events after commit on the AI executor.
The existing `ai_translations` table stores both source text and Arabic text. Complete
or pending jobs can be reused for a subset of their text, and new response combinations
reuse individual saved translations with ownership/language checks. Translation remains
subject to provider availability and the user's AI allowance; failure/retry behavior is
unchanged. There is no extra scan merely to produce the Arabic version.

Read-aloud explicitly selects a voice matching the selected language, including when
voices arrive asynchronously. It speaks translated text, preserves cancellation, and
shows an inline error if no matching voice exists. Install an Arabic system/browser
voice when that message appears; the app never substitutes an English voice for Arabic.
