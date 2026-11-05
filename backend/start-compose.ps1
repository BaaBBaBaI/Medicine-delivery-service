param(
    [int]$WaitTimeoutSeconds = 420
)

$ErrorActionPreference = "Stop"
$BackendDir = $PSScriptRoot
$RepoRoot = Split-Path -Parent $BackendDir
$ComposePath = Join-Path $BackendDir "docker-compose.yml"
$RootEnvPath = Join-Path $RepoRoot ".env"
$LocalEnvPath = Join-Path $BackendDir ".env"

Set-Location $BackendDir

if (-not (Test-Path $ComposePath)) {
    throw "docker-compose.yml not found: $ComposePath"
}

$EnvPath = if (Test-Path $RootEnvPath) { $RootEnvPath } elseif (Test-Path $LocalEnvPath) { $LocalEnvPath } else { $null }
if (-not $EnvPath) {
    Write-Host ".env not found. Creating it..." -ForegroundColor Yellow
    & (Join-Path $BackendDir "init-env.ps1")
    $EnvPath = $LocalEnvPath
}

Write-Host "Stopping stale containers (database volume is preserved)..." -ForegroundColor Cyan
& docker compose --env-file $EnvPath -f $ComposePath down --remove-orphans
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Starting PostgreSQL 17 and Medicine Delivery Spring Boot Backend..." -ForegroundColor Cyan
& docker compose --env-file $EnvPath -f $ComposePath up -d --force-recreate --wait --wait-timeout $WaitTimeoutSeconds

if ($LASTEXITCODE -ne 0) {
    Write-Host "Startup failed. Backend logs:" -ForegroundColor Red
    & docker compose --env-file $EnvPath -f $ComposePath logs --tail 160 backend
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host " MEDICINE DELIVERY SERVICE IS READY" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host " Web UI:           http://localhost:8080/"
Write-Host " Swagger UI:       http://localhost:8080/swagger-ui.html"
Write-Host " REST API:         http://localhost:8080/api"
Write-Host " OpenAPI JSON:     http://localhost:8080/v3/api-docs"
Write-Host " Health:           http://localhost:8080/actuator/health"
Write-Host " PostgreSQL JDBC:  jdbc:postgresql://localhost:55432/medicine_delivery"
Write-Host "============================================================" -ForegroundColor Green
& docker compose --env-file $EnvPath -f $ComposePath ps
