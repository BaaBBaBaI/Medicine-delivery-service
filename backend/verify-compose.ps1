$ErrorActionPreference = "Stop"
$compose = Join-Path $PSScriptRoot "docker-compose.yml"
Write-Host "Compose path: $compose"
docker compose -f $compose config
