[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$configPath = Join-Path $PSScriptRoot '.env'
$secretDir = Join-Path $PSScriptRoot 'secrets'
$secretPath = Join-Path $secretDir 'admin-password.txt'
if (-not (Test-Path -LiteralPath $configPath)) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '.env.example') -Destination $configPath
}
if (-not (Test-Path -LiteralPath $secretDir)) {
    New-Item -ItemType Directory -Path $secretDir | Out-Null
}
if (-not (Test-Path -LiteralPath $secretPath)) {
    $randomBytes = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($randomBytes) } finally { $generator.Dispose() }
    $password = [Convert]::ToBase64String($randomBytes)
    [System.IO.File]::WriteAllText($secretPath, $password, (New-Object System.Text.UTF8Encoding($false)))
}
Write-Host 'Jenkins configuration is ready. Existing configuration and password were preserved.'
Write-Host 'Start: docker compose -f infra/jenkins/compose.yaml up -d --build --wait'
Write-Host 'Password file: infra/jenkins/secrets/admin-password.txt'
