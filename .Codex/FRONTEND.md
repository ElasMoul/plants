

## French user UI (2026-09-23)
- shared/i18n: French catalog, LanguageService, t/pluralSuffix pipes, language selector,
  localized Material paginator/datepicker labels, locale and placeholder tests.
- AppModule registers French locale and locale providers. SharedModule exports
  translation pipes and the selector. Header and preferences expose English/Français.
- User-facing templates/messages are localized; admin and Atlas are outside this rollout.
- Browser regression: e2e/journeys/language.spec.ts. Guide: docs/localization.md.

## French AI content (2026-09-23)
- AiLanguageInterceptor requests localized metadata, polls jobs, registers translated
  prose before delivering unchanged DTOs. English bypasses this path.
- AiTextPipe/aiDiagram translate display text and diagram labels; original DTOs feed
  actions. List-detail parsing and read-aloud use translated text.
- AiTranslationState/notice expose pending/failure/retry; navigation clears stale errors.
- ChatService supplies current en/fr language. SpeciesModule supplies treatment and
  reminder services for direct species detail navigation with care cards.
- Tests: interceptor spec plus language.spec browser journeys. See docs/localization.md.
