# PlantPal languages

The standard user app supports English and French. Choose **Français** in the
header or under **Language / Langue** in settings. The header selector is also
available before login. The choice stays on this browser and origin after logout.
Changing language reloads the current page; save unfinished edits first.

This rollout translates the user interface, forms, common messages, calendars,
dates, pagination, and accessibility labels. Voice input and read-aloud use the
selected document language. User-entered content and existing AI-generated text
are preserved. The interface language does not alter AI prompts or the separate
Pl@ntNet common-name language setting. Unknown server messages may remain English.
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
No schema migration, new dependency, or per-account preference was introduced.

Arabic will require a translated catalog, language-specific plural messages, and
RTL layout/keyboard/accessibility verification. The direction metadata is prepared;
Arabic is intentionally not offered yet. Atlas and account-synced preferences are
separate future work.

## Validation

Production build passed; 562 unit tests across 46 suites and 12 Chromium journeys
passed, including English/French switching, validation, user screens, date selection,
mobile overflow and existing admin workflows. See `docs/screenshots/french-plant-form.png`.
