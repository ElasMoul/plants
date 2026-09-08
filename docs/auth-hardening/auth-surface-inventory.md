# PlantPal auth surface inventory

Inventory baseline: `dev` at `327c77e` on 2026-09-08. Access classifications come from
`SecurityConfig` for backend endpoints and from Angular route guards (including inherited
lazy-route guards) for the classic frontend. `protected` means anonymous access must be denied;
`public` means anonymous access is intentionally allowed.

The Atlas frontend does not install Angular Router. Its single document is therefore recorded as
a public, routerless shell; live data calls still cross the protected backend endpoint boundary.
Redirect declarations are classified by their effective target, with that indirection made
explicit in the Guard column.

## Frontend route records

| ID | App | Method | Path | Declaration | Guard | Access | Source |
|---|---|---|---|---|---|---|---|
| FE-001 | classic | ROUTE | `/` | LandingComponent | none | public | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/landing/landing-routing.module.ts` |
| FE-002 | classic | ROUTE | `/home` | HomeComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/dashboard/dashboard-routing.module.ts` |
| FE-003 | classic | ROUTE | `/home/overview` | GardenDashboardComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/dashboard/dashboard-routing.module.ts` |
| FE-004 | classic | REDIRECT | `/dashboard` | `/home/overview` | redirect to AuthGuard target | protected | `frontend/src/app/app-routing.module.ts` |
| FE-005 | classic | ROUTE | `/garden` | SpeciesListComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/species/species-routing.module.ts` |
| FE-006 | classic | ROUTE | `/garden/species/:id` | SpeciesDetailComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/species/species-routing.module.ts` |
| FE-007 | classic | ROUTE | `/plants/new` | PlantFormComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/plant/plant-routing.module.ts` |
| FE-008 | classic | ROUTE | `/plants/:id/edit` | PlantFormComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/plant/plant-routing.module.ts` |
| FE-009 | classic | ROUTE | `/plants/:id/scans/:scanId` | ScanDetailComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/plant/plant-routing.module.ts` |
| FE-010 | classic | ROUTE | `/plants/:id` | PlantDetailComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/plant/plant-routing.module.ts` |
| FE-011 | classic | ROUTE | `/identify` | IdentificationPageComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/identification/identification-routing.module.ts` |
| FE-012 | classic | ROUTE | `/identify/:id` | IdentificationDetailPageComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/identification/identification-routing.module.ts` |
| FE-013 | classic | ROUTE | `/treatment/:id` | TreatmentDetailComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/treatment/treatment-routing.module.ts` |
| FE-014 | classic | ROUTE | `/reminders` | ReminderListComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/reminder/reminder-routing.module.ts` |
| FE-015 | classic | ROUTE | `/treatment-plans/:id` | TreatmentPlanDetailComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/reminder/reminder-routing.module.ts` |
| FE-016 | classic | ROUTE | `/plans/:planId/steps/:stepId` | TaskStepComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/reminder/reminder-routing.module.ts` |
| FE-017 | classic | ROUTE | `/chat` | ChatHomeComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/chat/chat-routing.module.ts` |
| FE-018 | classic | ROUTE | `/preferences` | PreferencesPageComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/preferences/preferences-routing.module.ts` |
| FE-019 | classic | ROUTE | `/voice-test` | VoiceTestComponent | AuthGuard (inherited) | protected | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/voice-test/voice-test-routing.module.ts` |
| FE-020 | classic | ROUTE | `/login` | LoginComponent | none | public | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/auth/auth-routing.module.ts` |
| FE-021 | classic | ROUTE | `/register` | RegisterComponent | none | public | `frontend/src/app/app-routing.module.ts`; `frontend/src/app/features/auth/auth-routing.module.ts` |
| FE-022 | classic | REDIRECT | `/**` | `/garden` | redirect to AuthGuard target | protected | `frontend/src/app/features/auth/auth-routing.module.ts` |
| FE-023 | atlas | DOCUMENT | `/` | App bootstrap | none | public | `frontend/projects/atlas/src/main.ts`; `frontend/projects/atlas/src/app/app.ts` |

## Backend endpoint records

| ID | Method | Path | Controller | Security rule | Access | Source |
|---|---|---|---|---|---|---|
| BE-001 | POST | `/api/v1/auth/register` | AuthController | POST permitAll | public | `backend/src/main/java/com/plantpal/user/controller/AuthController.java` |
| BE-002 | POST | `/api/v1/auth/login` | AuthController | POST permitAll | public | `backend/src/main/java/com/plantpal/user/controller/AuthController.java` |
| BE-003 | GET | `/api/v1/users/me/preferences` | UserController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/user/controller/UserController.java` |
| BE-004 | PUT | `/api/v1/users/me/preferences` | UserController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/user/controller/UserController.java` |
| BE-005 | POST | `/api/v1/chat` | ChatController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/chat/controller/ChatController.java` |
| BE-006 | POST | `/api/v1/chat/stream` | ChatController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/chat/controller/ChatController.java` |
| BE-007 | GET | `/api/v1/dashboard` | DashboardController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/dashboard/controller/DashboardController.java` |
| BE-008 | GET | `/api/v1/plants` | PlantController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/plant/controller/PlantController.java` |
| BE-009 | POST | `/api/v1/plants` | PlantController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/plant/controller/PlantController.java` |
| BE-010 | GET | `/api/v1/plants/{id}` | PlantController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/plant/controller/PlantController.java` |
| BE-011 | PUT | `/api/v1/plants/{id}` | PlantController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/plant/controller/PlantController.java` |
| BE-012 | DELETE | `/api/v1/plants/{id}` | PlantController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/plant/controller/PlantController.java` |
| BE-013 | POST | `/api/v1/plants/from-identification` | PlantController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/plant/controller/PlantController.java` |
| BE-014 | GET | `/api/v1/plantnet/projects` | PlantNetConfigController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/identification/controller/PlantNetConfigController.java` |
| BE-015 | GET | `/api/v1/plantnet/languages` | PlantNetConfigController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/identification/controller/PlantNetConfigController.java` |
| BE-016 | GET | `/api/v1/plantnet/quota` | PlantNetConfigController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/identification/controller/PlantNetConfigController.java` |
| BE-017 | POST | `/api/v1/identifications/analyze` | IdentificationController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/identification/controller/IdentificationController.java` |
| BE-018 | GET | `/api/v1/identifications` | IdentificationController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/identification/controller/IdentificationController.java` |
| BE-019 | GET | `/api/v1/identifications/{id}` | IdentificationController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/identification/controller/IdentificationController.java` |
| BE-020 | GET | `/api/v1/identifications/plant/{plantId}` | IdentificationController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/identification/controller/IdentificationController.java` |
| BE-021 | POST | `/api/v1/identifications/{id}/cure-advice` | IdentificationController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/identification/controller/IdentificationController.java` |
| BE-022 | POST | `/api/v1/identifications/{id}/care-plan/cards` | IdentificationController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/identification/controller/IdentificationController.java` |
| BE-023 | GET | `/api/v1/identifications/{id}/species-match` | IdentificationController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/identification/controller/IdentificationController.java` |
| BE-024 | POST | `/api/v1/identifications/{id}/resolve-species` | IdentificationController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/identification/controller/IdentificationController.java` |
| BE-025 | GET | `/api/v1/identifications/{id}/plant-match` | IdentificationController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/identification/controller/IdentificationController.java` |
| BE-026 | POST | `/api/v1/identifications/{id}/retry` | IdentificationController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/identification/controller/IdentificationController.java` |
| BE-027 | POST | `/api/v1/identifications/{id}/resolve-plant` | IdentificationController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/identification/controller/IdentificationController.java` |
| BE-028 | POST | `/api/v1/treatments` | TreatmentController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/treatment/controller/TreatmentController.java` |
| BE-029 | POST | `/api/v1/treatments/{id}/craft-plan` | TreatmentController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/treatment/controller/TreatmentController.java` |
| BE-030 | GET | `/api/v1/treatments/{id}` | TreatmentController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/treatment/controller/TreatmentController.java` |
| BE-031 | GET | `/api/v1/plants/{id}/active-treatment` | TreatmentController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/treatment/controller/TreatmentController.java` |
| BE-032 | GET | `/api/v1/plants/{id}/active-treatments` | TreatmentController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/treatment/controller/TreatmentController.java` |
| BE-033 | PATCH | `/api/v1/treatments/{id}/complete` | TreatmentController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/treatment/controller/TreatmentController.java` |
| BE-034 | POST | `/api/v1/treatments/{id}/regenerate-description` | TreatmentController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/treatment/controller/TreatmentController.java` |
| BE-035 | POST | `/api/v1/treatment-plans` | TreatmentPlanController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/reminder/controller/TreatmentPlanController.java` |
| BE-036 | GET | `/api/v1/treatment-plans/{id}` | TreatmentPlanController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/reminder/controller/TreatmentPlanController.java` |
| BE-037 | POST | `/api/v1/reminders` | ReminderController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/reminder/controller/ReminderController.java` |
| BE-038 | GET | `/api/v1/reminders` | ReminderController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/reminder/controller/ReminderController.java` |
| BE-039 | POST | `/api/v1/reminders/{id}/complete` | ReminderController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/reminder/controller/ReminderController.java` |
| BE-040 | DELETE | `/api/v1/reminders/{id}` | ReminderController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/reminder/controller/ReminderController.java` |
| BE-041 | POST | `/api/v1/care/done` | CareLogController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/reminder/controller/CareLogController.java` |
| BE-042 | GET | `/api/v1/care/plant/{plantId}` | CareLogController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/reminder/controller/CareLogController.java` |
| BE-043 | POST | `/api/v1/notifications/subscribe` | NotificationController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/reminder/controller/NotificationController.java` |
| BE-044 | GET | `/api/v1/species/{id}` | SpeciesController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/species/controller/SpeciesController.java` |
| BE-045 | GET | `/api/v1/species/mine` | SpeciesController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/species/controller/SpeciesController.java` |
| BE-046 | POST | `/api/v1/species/{id}/regenerate-description` | SpeciesController | anyRequest authenticated | protected | `backend/src/main/java/com/plantpal/species/controller/SpeciesController.java` |
| BE-047 | GET | `/api/v1/photos/{filename}` | PhotoController | path permitAll | public | `backend/src/main/java/com/plantpal/shared/controller/PhotoController.java` |
| BE-048 | GET | `/photos/{filename}` | PhotoController | path permitAll | public | `backend/src/main/java/com/plantpal/shared/controller/PhotoController.java` |

## Security-filter public surfaces without controller declarations

These explicit `permitAll` matchers are infrastructure surfaces, not controller endpoint rows:
`/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`, and `/swagger-ui.html`. CORS preflight
uses the configured `OPTIONS` method but is not separately permitted; it remains governed by the
security filter chain and CORS processing.

## Audit rules

- A backend controller mapping absent from the backend table is verifier failure.
- A backend row with a method, path, controller, access class, or security-rule mismatch is
  verifier failure.
- Frontend lazy children inherit the guard on their root declaration. A redirect row names the
  guard on its effective target instead of pretending the redirect declaration has its own guard.
- Query parameters do not create distinct route or endpoint records.
- Any change to route declarations, controller mappings, or `SecurityConfig` requires regenerating
  and reviewing this inventory.
