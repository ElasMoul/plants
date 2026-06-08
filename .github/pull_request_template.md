## Description

<!-- What does this PR do? Why? Keep it to 2-3 sentences. -->

## Type of change

- [ ] Bug fix (non-breaking change that fixes an issue)
- [ ] New feature (non-breaking change that adds functionality)
- [ ] Breaking change (fix or feature that changes existing behaviour)
- [ ] Refactor (no functional change)
- [ ] Config / infra / CI change
- [ ] Documentation update

## Linked issues

Closes #<!-- issue number -->

## Tests

- [ ] Unit tests added / updated
- [ ] Integration tests added / updated
- [ ] All tests pass locally (`mvn clean verify`)
- [ ] Coverage remains ≥ 80% (check `target/site/jacoco/index.html`)

## Frontend impact

- [ ] No frontend changes
- [ ] Frontend changes included and tested in browser
- [ ] API contract changed — both sides updated

## Quality checklist

- [ ] Follows conventions in `CLAUDE.md` (naming, member order, patterns)
- [ ] Constructor injection only — no `@Autowired` on fields
- [ ] All list endpoints paginated — no unbounded queries
- [ ] No hard deletes — soft delete via `status = ARCHIVED`
- [ ] All responses wrapped in `ApiResponse<T>`
- [ ] No secrets or credentials committed
- [ ] Self-review completed — no debug logs, TODOs, or commented-out code left in
