param([switch]$SkipBackend)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$envFile = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $password = [Convert]::ToBase64String($bytes)
    $template = Get-Content -Raw (Join-Path $PSScriptRoot '.env.example')
    [IO.File]::WriteAllText($envFile, $template.Replace('replace-with-a-long-random-password', $password))
}
docker compose --env-file $envFile -f "$PSScriptRoot/compose.yaml" up -d
if ($LASTEXITCODE -ne 0) { throw 'Observability stack failed to start' }
if (-not $SkipBackend) {
    Push-Location $root
    try {
        docker compose -f docker-compose.yml -f compose.observability.yml up -d --build --wait --wait-timeout 300 backend frontend
        if ($LASTEXITCODE -ne 0) { throw 'Application failed to start' }
        node scripts/observability_test.cjs
        if ($LASTEXITCODE -ne 0) { throw 'Observability smoke test failed' }
    } finally { Pop-Location }
}
Write-Host 'Grafana: http://localhost:3000 (credentials in infra/observability/.env)'
Write-Host 'Local alert inbox: http://localhost:18026'
Write-Host 'Verify: node scripts/observability_test.cjs'
