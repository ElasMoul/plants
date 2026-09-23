

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

## Arabic and compact selector (2026-09-23)
- ar.ts: full 586-entry Arabic catalog; language service adds ar-MA/RTL and counted forms.
- LanguageSwitchComponent: EN/FR/AR options with native-language titles, no symbol.
- Logical spacing/position CSS and directional-icon class support RTL; email/password/code
  retain LTR. Arabic locale registered for Angular dates and Material calendar.
- AI interceptor sends current fr/ar target; Arabic notice, display pipes and chat language.
- Arabic browser journeys cover login switch, form/calendar/mobile overflow, core routes,
  scientific-name/quantity preservation in AI text and switching back to LTR.
