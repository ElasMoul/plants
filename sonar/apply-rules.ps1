# Create/refresh the "PlantPal way" quality profiles on a SonarQube server from sonar/rules.json.
# Idempotent: safe to re-run after editing rules.json. Removing a rule from the file does NOT
# deactivate it on the server — deactivate it in the UI (or re-create the profile).
#
# Usage (from the repo root; needs an admin user token, not an analysis token):
#   $env:SONAR_ADMIN_TOKEN = "<user token of an admin>"   # My Account -> Security -> User Token
#   .\sonar\apply-rules.ps1                               # -HostUrl http://localhost:9000
param([string]$HostUrl = 'http://localhost:9000')

if (-not $env:SONAR_ADMIN_TOKEN) { throw 'SONAR_ADMIN_TOKEN is not set.' }
$auth = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("$($env:SONAR_ADMIN_TOKEN):")) }
$config = Get-Content (Join-Path $PSScriptRoot 'rules.json') -Raw | ConvertFrom-Json

function Invoke-Sonar([string]$Method, [string]$Path, [hashtable]$Body = @{}) {
    Invoke-RestMethod -Method $Method -Uri "$HostUrl$Path" -Headers $auth -Body $Body
}

function Get-ProfileKey([string]$Language, [string]$Name) {
    $found = Invoke-Sonar Get '/api/qualityprofiles/search' @{ language = $Language; qualityProfile = $Name }
    if ($found.profiles) { return $found.profiles[0].key }
    return $null
}

foreach ($lang in $config.profiles.PSObject.Properties.Name) {
    $spec = $config.profiles.$lang
    $key = Get-ProfileKey $lang $config.profileName
    if (-not $key) {
        $key = (Invoke-Sonar Post '/api/qualityprofiles/create' @{ language = $lang; name = $config.profileName }).profile.key
        Write-Host "[$lang] created profile '$($config.profileName)'"
    }
    Invoke-Sonar Post '/api/qualityprofiles/change_parent' @{
        language = $lang; qualityProfile = $config.profileName; parentQualityProfile = $config.parent } | Out-Null

    foreach ($r in $spec.activate) {
        $body = @{ key = $key; rule = $r.rule }
        if ($r.params) {
            $body.params = ($r.params.PSObject.Properties | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join ';'
        }
        # Template rules (e.g. java:S3546) and removed rules return 400 — report and continue.
        try {
            Invoke-Sonar Post '/api/qualityprofiles/activate_rule' $body | Out-Null
        } catch {
            Write-Warning "[$lang] could not activate $($r.rule): $($_.ErrorDetails.Message)"
        }
    }
    Write-Host "[$lang] $($spec.activate.Count) extra rules processed on top of '$($config.parent)'"

    foreach ($project in $spec.projects) {
        Invoke-Sonar Post '/api/qualityprofiles/add_project' @{
            language = $lang; qualityProfile = $config.profileName; project = $project } | Out-Null
        Invoke-Sonar Post '/api/qualitygates/select' @{ gateName = $config.qualityGate; projectKey = $project } | Out-Null
        Write-Host "[$lang] $project -> profile '$($config.profileName)', gate '$($config.qualityGate)'"
    }
}
