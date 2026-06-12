# PlantPal — Shared Project State
> Updated after each session. All agents read this first.
> Last updated: [date]

## Current Phase
Phase 2 — AI Plant Identification (in progress)

## Completed Tasks
- T0.1 GitHub repo + branch protection ✅
- T0.2 Docker Compose (PostgreSQL 15 + Redis 7) ✅
- T0.3 Spring Boot backend skeleton ✅
- T0.4 Angular frontend skeleton ✅
- T0.5 CI/CD pipelines + Dockerfiles ✅
- T1.1 Liquibase migrations (users, plants, identifications, reminders, push_subscriptions) ✅
- T1.2 User module (entity, DTOs, MapStruct mapper, repository) ✅
- T1.3 Spring Security 6 + JWT authentication ✅
- T1.4 Plant module — full backend CRUD ✅
- T1.7 Plant Angular feature module ✅
- T1.8 Auth Angular feature module ✅
- PlantNet integration — full backend ✅
- T2.4 Identification Angular feature module (in progress)

## Active Branches
- feature/PP-010-identification-frontend (T2.4 — in progress)

## Key Decisions Since Project Start
- Plant identification uses PlantNet API (not Claude Vision)
- Care planning + chat uses Ollama phi3 (local dev) 
- AI provider abstraction kept — Claude API for prod, Ollama for dev
- JaCoCo gate temporarily at 10% until integration tests run in CI
- Identification raw_response stored as TEXT (not JSONB) for simplicity

## Open Items
- JaCoCo gate needs to be restored to 80% with proper exclusions
- Branch protection configured on main + dev
- Integration tests not running in CI (Testcontainers phase isolation issue)

## Repo Structure
plants/
  backend/          Spring Boot 3.2, Java 21
  frontend/         Angular 16+, NgModules
  docker-compose.yml
  .github/workflows/
  .claude/          ← agent memory (this folder)