[CmdletBinding()]
param(
    [ValidateSet('init', 'up', 'down', 'status', 'logs', 'models')]
    [string]$Action = 'status',
    [switch]$WithJenkins,
    [string]$Service,
    [switch]$NoBuild
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$config = Join-Path $repo '.env.learning'
function Random-Secret {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
function Existing-Value($Path, $Key) {
    if (Test-Path -LiteralPath $Path) {
        foreach ($line in [IO.File]::ReadAllLines($Path)) {
            if ($line.StartsWith($Key + '=')) { return $line.Substring($Key.Length + 1).Trim() }
        }
    }
    return $null
}
function Compose([string[]]$Arguments) {
    & docker @script:compose @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed: $($Arguments -join ' ')" }
}
function Models {
    $lock = Get-Content -Raw (Join-Path $repo 'infra/ai/models.lock.json') | ConvertFrom-Json
    foreach ($model in $lock.models.PSObject.Properties) {
        $installed = & docker @script:compose exec -T ollama ollama list
        if ($LASTEXITCODE -ne 0) { throw 'Cannot list Ollama models' }
        $pattern = '^' + [regex]::Escape($model.Name) + '\s'
        if (-not ($installed | Where-Object { $_ -match $pattern })) {
            Compose @('exec', '-T', 'ollama', 'ollama', 'pull', $model.Name)
            $installed = & docker @script:compose exec -T ollama ollama list
            if ($LASTEXITCODE -ne 0) { throw 'Cannot verify Ollama models' }
        }
        $line = $installed | Where-Object { $_ -match $pattern } | Select-Object -First 1
        if (-not $line -or ($line -split '\s+')[1] -ne $model.Value.Substring(0, 12)) {
            throw "Model digest differs from infra/ai/models.lock.json: $($model.Name)"
        }
    }
}
Push-Location $repo
try {
    if ($Action -in @('init', 'up')) {
        if (-not (Test-Path -LiteralPath $config)) {
            $qdrant = Existing-Value (Join-Path $repo 'infra/ai/.env') 'AI_QDRANT_API_KEY'
            $grafana = Existing-Value (Join-Path $repo 'infra/observability/.env') 'GRAFANA_ADMIN_PASSWORD'
            if (-not $qdrant -or $qdrant -eq 'replace-with-random-key') { $qdrant = Random-Secret }
            if (-not $grafana -or $grafana -eq 'replace-with-a-long-random-password') { $grafana = Random-Secret }
            [IO.File]::WriteAllText($config, "LEARNING_QDRANT_API_KEY=$qdrant`nLEARNING_GRAFANA_PASSWORD=$grafana`n", (New-Object Text.UTF8Encoding($false)))
        }
        $secretDirectory = Join-Path $repo 'infra/jenkins/secrets'
        $jenkinsSecret = Join-Path $secretDirectory 'admin-password.txt'
        if (-not (Test-Path -LiteralPath $jenkinsSecret)) {
            New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null
            [IO.File]::WriteAllText($jenkinsSecret, (Random-Secret), (New-Object Text.UTF8Encoding($false)))
        }
        foreach ($volume in @('codepath-kafka_kafka_data', 'codepath-ai_qdrant_data', 'codepath-ai_ollama_data', 'codepath-observability_prometheus_data', 'codepath-observability_loki_data', 'codepath-observability_tempo_data', 'codepath-observability_grafana_data', 'shared-jenkins_jenkins_home')) {
            & docker volume create $volume | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "Cannot prepare volume $volume" }
        }
    }
    if (-not (Test-Path -LiteralPath $config)) { throw 'Run ./scripts/learning.ps1 init first' }
    $script:compose = @('compose')
    if (Test-Path -LiteralPath (Join-Path $repo '.env')) { $script:compose += @('--env-file', '.env') }
    $script:compose += @('--env-file', '.env.learning', '-f', 'compose.learning.yaml')
    if ($WithJenkins -or $Action -in @('down', 'status', 'logs')) { $script:compose += @('--profile', 'ci') }
    switch ($Action) {
        'init' { Compose @('config', '--quiet'); Write-Host 'Ready. Secrets: .env.learning and infra/jenkins/secrets/admin-password.txt' }
        'up' {
            foreach ($legacyProject in @('codepath-ai', 'codepath-kafka', 'codepath-observability', 'shared-jenkins')) {
                $running = & docker ps --filter "label=com.docker.compose.project=$legacyProject" --format '{{.Names}}'
                if ($LASTEXITCODE -ne 0) { throw 'Cannot check running Docker projects' }
                if ($running) { throw "Stop legacy project $legacyProject before starting the learning stack (keep its volumes)." }
            }
            Compose @('config', '--quiet')
            Compose @('up', '-d', '--wait', '--wait-timeout', '240', 'mysql', 'redis', 'mailpit', 'kafka', 'qdrant', 'ollama', 'grafana', 'otel-collector')
            Models
            $start = @('up', '-d', '--wait', '--wait-timeout', '360')
            if (-not $NoBuild) { $start += '--build' }
            Compose $start
            Write-Host 'Website: http://localhost:5173 | API: http://localhost:8080 | Grafana: http://localhost:3000'
            Write-Host 'Mail: http://localhost:8025 | Alert mail: http://localhost:18026 | Jenkins (-WithJenkins): http://localhost:8090'
        }
        'models' { Models }
        'down' { Compose @('down') }
        'status' { Compose @('ps', '-a') }
        'logs' {
            $argsList = @('logs', '--tail', '100', '--follow')
            if ($Service) { $argsList += $Service }
            Compose $argsList
        }
    }
} finally { Pop-Location }
