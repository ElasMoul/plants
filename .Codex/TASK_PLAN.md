# Administration console — completed plan

- [x] Inspect PlantPal domain, authentication, both frontends, and model settings.
- [x] Create `codex/plantpal-admin-dashboard` from `dev`.
- [x] Add roles, migration, first-admin provisioning, and live-token status enforcement.
- [x] Build reporting, paginated account management, model catalog, and activity APIs.
- [x] Build responsive overview, People, account details, AI studio, and activity pages.
- [x] Apply catalog visibility to Classic and Atlas Settings and reject new hidden selections.
- [x] Test authorization, suspension/reactivation, versions, search, audit, and bootstrap.
- [x] Verify builds, full backend gate, unit suites, browser flows, and accessibility.
- [x] Commit implementation and synchronize feature documentation.

Initial console setup was followed by explicit owner-requested local provisioning;
see the expansion below and STATE.md for the current administrator identity.


## Administration expansion — completed 2026-09-22
- [x] Make admin@plantpal.com the local administrator, preserving the existing account.
- [x] Route administrator login directly to the console.
- [x] Add recoverable delete/restore and explicit disable/enable controls.
- [x] Persist per-user plant, scan and total AI ceilings with concurrent enforcement.
- [x] Manage users' gardens through audited, owner-scoped endpoints.
- [x] Verify native DeepSeek model/API; add direct Flash support and settings choices.
- [x] Verify unit/integration/browser behavior and restore the local preview.
- [x] Commit implementation and synchronize documentation.


## Hosted provider and plant viewer — complete 2026-09-23
- [x] Use DEEPSEEK_HOSTED_API_KEY / DEEPSEEK_HOSTED_BASE_URL with Anthropic format.
- [x] Test the configured native DeepSeek key with one small live request.
- [x] Add View plant modal with photo, details, activity and treatment status.
- [x] Verify bounded owner-scoped API, archived access, mobile and accessibility.
- [x] Rebuild/restart preview and synchronize documentation.


## French user interface — complete 2026-09-23
- [x] Branch from dev after the admin merge.
- [x] Add English/French catalog, browser language preference and selectors.
- [x] Translate standard user screens and application-generated messages.
- [x] Localize dates, calendars, pagination, speech language and plural labels.
- [x] Preserve protocol values, user content and admin workflows.
- [x] Validate unit tests, production build, browser journeys and mobile layout.
- [x] Commit implementation and synchronize documentation.
- Future: Atlas localization; Arabic catalog/plural rules and RTL layout verification;
  account-synced language; localized AI output and server errors.

## French AI content — complete 2026-09-23

- [x] Preserve canonical analyses and persist French presentation versions (036).
- [x] Translate existing/new care, health, species and treatment prose asynchronously.
- [x] Enforce ownership, quotas, batch limits, safe retries and amount/unit checks.
- [x] Display French cards, steps, diagram labels and read-aloud; explicit failure notice.
- [x] Generate new chat replies in the selected language.
- [x] Verify database cache/ownership, UI journeys, production build and hosted DeepSeek.
- [ ] Owner tests French before starting Arabic.

## Arabic and compact selector — ready for owner testing 2026-09-23

- [x] Replace selector labels with EN / FR / AR and remove symbol.
- [x] Complete Arabic user catalog, RTL, calendar/speech locale and counted forms.
- [x] Add Arabic saved AI translations, cache isolation, retry target and chat prompts.
- [x] Apply migration 037; verify backend, frontend, RTL browser and admin regressions.
- [ ] Owner tests Arabic/French; merge to dev only after owner confirms results.

## PR #159 recovery — complete locally
- [x] Fix five Spotless violations; unfiltered check and full backend verify passed.
- [x] Preserve checkout-blocking files in a named stash and recover feature checkout.
- [x] Back up divergent main commits, then align local main with origin/main.
- [ ] Await GitHub checks and owner approval before merging.
