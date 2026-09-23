# PlantPal languages

The standard user app supports English and French. Choose **Français** in the
header or under **Language / Langue** in settings. The header selector is also
available before login. The choice stays on this browser and origin after logout.
Changing language reloads the current page; save unfinished edits first.

This rollout translates the user interface, forms, common messages, calendars,
dates, pagination, and accessibility labels. Voice input and read-aloud use the
selected document language. User-entered content, scientific names and the separate
Pl@ntNet common-name language setting are preserved. AI descriptions, care cards,
health notes, treatment advice and step instructions have saved French display versions.
New chat replies are generated directly in the selected language. Unknown server messages may remain English.
The admin console and Atlas are not translated in this rollout.

## Development

Messages live in `frontend/src/app/shared/i18n/fr.ts`, keyed by their English source.
Use `{{ 'Save changes' | t }}` for templates and `translate('Save changes')` for
application-owned TypeScript messages. Use numbered parameters for dynamic values,
not concatenated translated fragments. Never translate API values or comparisons.
English fallback allows new copy to remain readable, but add its French translation
in the same change. Tests check numbered placeholder parity and language persistence.

`LanguageService` owns supported languages, locales and document direction. Locale
providers and module-level labels initialize at startup, so language changes reload.
The UI preference remains browser-local. Migration 036 adds persistent AI translation jobs;
no new dependency or per-account language preference is introduced.

Arabic will require a translated catalog, language-specific plural messages, and
RTL layout/keyboard/accessibility verification. The direction metadata is prepared;
Arabic is intentionally not offered yet. Atlas and account-synced preferences are
separate future work.

## Validation

Production build passed (existing bundle/CommonJS warnings); 566 frontend unit tests,
483 backend unit tests, four translation database integration tests and 14 Chromium
journeys passed. These cover language switching, translated saved content, pending
jobs, failure/retry UI, ownership, cache reuse and existing admin workflows.
Hosted DeepSeek was also verified using synthetic plant-care text. See `docs/screenshots/french-plant-form.png`.

## AI content in French

Select Français, then open an existing scan, species page or treatment. The first
view prepares a French version in the background and shows a progress notice.
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
with X-Content-Language: fr. The frontend polls the translation job, then applies
text only at display time. It never submits translated DTOs back as domain changes.
Arabic AI output, RTL and Atlas remain future work.
