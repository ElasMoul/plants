# Scan PlantPal with a local SonarQube Community Edition server (http://localhost:9000).
#
# Usage (from the repo root):
#   $env:SONAR_TOKEN = "<your token>"      # My Account -> Security -> Global Analysis Token
#   .\scripts\sonar-scan.ps1               # backend + frontend
#   .\scripts\sonar-scan.ps1 -Only backend # or -Only frontend
#   .\scripts\sonar-scan.ps1 -SkipTests    # reuse existing coverage reports
param(
    [ValidateSet('all', 'backend', 'frontend')]
    [string]$Only = 'all',
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

if (-not $env:SONAR_TOKEN) {
    throw 'SONAR_TOKEN is not set. Create a token in SonarQube (My Account -> Security) first.'
}

if ($Only -in 'all', 'backend') {
    Push-Location (Join-Path $root 'backend')
    try {
        # verify = unit + integration tests + JaCoCo report (Docker needed for Testcontainers)
        if (-not $SkipTests) { mvn clean verify --batch-mode "-Ddependency.check.skip=true" }
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
        mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar --batch-mode
        if ($LASTEXITCODE -ne 0) { throw 'Backend Sonar analysis failed.' }
    } finally { Pop-Location }
}

if ($Only -in 'all', 'frontend') {
    Push-Location (Join-Path $root 'frontend')
    try {
        # Coverage for Sonar only — the 15% gate lives in `npm run test:coverage`.
        if (-not $SkipTests) { npx jest --coverage "--coverageThreshold={}" }
        if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed.' }
        npx --yes @sonar/scan
        if ($LASTEXITCODE -ne 0) { throw 'Frontend Sonar analysis failed.' }
    } finally { Pop-Location }
}

Write-Host 'Done - open http://localhost:9000 to see the results.'
