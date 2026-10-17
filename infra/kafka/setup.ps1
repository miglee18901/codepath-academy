param([switch]$WithObservability,[switch]$WithAi)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
docker compose -f (Join-Path $PSScriptRoot 'compose.yaml') up -d --wait
if ($LASTEXITCODE -ne 0) { throw 'Kafka startup failed' }
Push-Location $projectRoot
try {
    $composeArgs = @('-f','docker-compose.yml','-f','compose.kafka.yml')
    if ($WithAi) {
        docker compose -f infra/ai/compose.yaml up -d --wait
        if ($LASTEXITCODE -ne 0) { throw 'AI infrastructure startup failed' }
        $composeArgs += @('-f','compose.ai.yml')
    }
    if ($WithObservability) { $composeArgs += @('-f','compose.observability.yml','-f','compose.kafka.observability.yml') }
    docker compose @composeArgs up -d --build --wait backend frontend worker
    if ($LASTEXITCODE -ne 0) { throw 'Application startup failed' }
} finally { Pop-Location }
