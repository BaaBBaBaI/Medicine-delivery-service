param(
    [string]$AdminEmail = "admin@example.org",
    [string]$AdminName = "System Administrator",
    [int]$Port = 8080,
    [int]$PostgresPort = 55432,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$EnvPath = Join-Path $PSScriptRoot ".env"
$RootEnvPath = Join-Path (Split-Path -Parent $PSScriptRoot) ".env"

if ((Test-Path $EnvPath) -and -not $Force) {
    throw ".env already exists. Use -Force to replace it."
}

function New-RandomBytes([int]$Length) {
    $bytes = New-Object byte[] $Length
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return $bytes
}

function New-Base64Secret([int]$Length) {
    return [Convert]::ToBase64String((New-RandomBytes $Length))
}

$DbPassword = New-Base64Secret 36
$JwtSecret = New-Base64Secret 64
$RandomPasswordPart = ([Convert]::ToHexString((New-RandomBytes 18))).ToLowerInvariant()
$AdminPassword = "Aa1!$RandomPasswordPart"

$content = @"
POSTGRES_DB=medicine_delivery
POSTGRES_USER=medicine_delivery_app
POSTGRES_PASSWORD=$DbPassword
POSTGRES_PORT=$PostgresPort
JWT_SECRET_BASE64=$JwtSecret
JWT_ISSUER=medicine-delivery-service
ACCESS_TOKEN_MINUTES=15
REFRESH_TOKEN_DAYS=14
BOOTSTRAP_ADMIN_EMAIL=$AdminEmail
BOOTSTRAP_ADMIN_NAME="$AdminName"
BOOTSTRAP_ADMIN_PASSWORD=$AdminPassword
CORS_ALLOWED_ORIGINS=http://localhost:$Port
COOKIE_SECURE=false
PERMANENT_DELETE_ENABLED=true
APP_PORT=$Port
"@

[IO.File]::WriteAllText($EnvPath, $content, (New-Object Text.UTF8Encoding($false)))
[IO.File]::WriteAllText($RootEnvPath, $content, (New-Object Text.UTF8Encoding($false)))

Write-Host "Created: $EnvPath and $RootEnvPath" -ForegroundColor Green
Write-Host "Bootstrap admin email: $AdminEmail" -ForegroundColor Cyan
Write-Host "Temporary admin password: $AdminPassword" -ForegroundColor Yellow
