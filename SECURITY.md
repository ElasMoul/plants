# Security

## Reporting Vulnerabilities

Please report security vulnerabilities by opening a GitHub issue marked **[SECURITY]**
or by contacting the maintainers directly.

## Known Exposure — GITHUB_TOKEN Rotation Required

A GitHub Personal Access Token (`GITHUB_TOKEN` with `models:read` scope) was shared in
chat sessions during development. **This token must be rotated before any production
deployment.**

To rotate: go to github.com → Settings → Developer settings → Personal access tokens,
revoke the old token, and generate a new one with the same `models:read` scope.
Update the `GITHUB_TOKEN` secret in the repository's Settings → Secrets and variables → Actions.

## Automated Scanning

- **Secret scanning:** gitleaks runs on every push and pull request (`.github/workflows/secret-scan.yml`).
  Future token leaks will be caught before merge.
- **Dependency CVEs:** OWASP Dependency-Check runs during `mvn verify`; fails on CVSS ≥ 7.
- **Docker image CVEs:** Trivy scans the backend filesystem on every CI run.
- **Dependency updates:** Dependabot opens weekly PRs for Maven, npm, and GitHub Actions.
