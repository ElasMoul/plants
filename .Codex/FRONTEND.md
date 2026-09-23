

## French user UI (2026-09-23)
- shared/i18n: French catalog, LanguageService, t/pluralSuffix pipes, language selector,
  localized Material paginator/datepicker labels, locale and placeholder tests.
- AppModule registers French locale and locale providers. SharedModule exports
  translation pipes and the selector. Header and preferences expose English/Français.
- User-facing templates/messages are localized; admin and Atlas are outside this rollout.
- Browser regression: e2e/journeys/language.spec.ts. Guide: docs/localization.md.
