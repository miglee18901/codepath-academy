param([switch]$SkipApplication, [switch]$WithObservability)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$aiEnv = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path -LiteralPath $aiEnv)) {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $key = [Convert]::ToBase64String($bytes)
    $template = [IO.File]::ReadAllText((Join-Path $PSScriptRoot '.env.example'))
    [IO.File]::WriteAllText($aiEnv, $template.Replace('replace-with-random-key', $key))
}
Push-Location $repo
try {
    $stack = @('compose', '--env-file', $aiEnv, '-f', 'infra/ai/compose.yaml')
    & docker @stack up -d --wait --wait-timeout 240
    if ($LASTEXITCODE -ne 0) { throw 'AI infrastructure startup failed' }
    $downloaded = $false
    foreach ($model in @('embeddinggemma:300m', 'qwen3:4b-instruct-2507-q4_K_M')) {
        $installed = & docker @stack exec -T ollama ollama list
        if ($LASTEXITCODE -ne 0) { throw 'Cannot list installed models' }
        $modelPattern = '^' + [regex]::Escape($model) + '\s'
        if (-not ($installed | Where-Object { $_ -match $modelPattern })) {
            & docker @stack exec -T ollama ollama pull $model
            if ($LASTEXITCODE -ne 0) { throw "Model download failed: $model" }
            $downloaded = $true
            $installed = & docker @stack exec -T ollama ollama list
            if ($LASTEXITCODE -ne 0) { throw 'Cannot verify installed model' }
        }
        $lock = Get-Content -Raw (Join-Path $PSScriptRoot 'models.lock.json') | ConvertFrom-Json
        $expected = $lock.models.PSObject.Properties[$model].Value.Substring(0, 12)
        $line = $installed | Where-Object { $_ -match $modelPattern } | Select-Object -First 1
        if (-not $line -or ($line -split '\s+')[1] -ne $expected) {
            throw "Model digest differs from models.lock.json: $model. Review model update and reindex before changing the lock."
        }
    }
    if ($downloaded) {
        # Release download/verification buffers before memory checks for model loading.
        & docker @stack restart ollama
        if ($LASTEXITCODE -ne 0) { throw 'Cannot restart Ollama after initial model download' }
        & docker @stack up -d --wait --wait-timeout 120
        if ($LASTEXITCODE -ne 0) { throw 'Ollama did not become ready after model download' }
    }
    New-Item -ItemType Directory -Path 'artifacts/qa' -Force | Out-Null
    & docker @stack exec -T ollama ollama list | Set-Content 'artifacts/qa/ai-models.txt'
    & docker @stack images --format json | Set-Content 'artifacts/qa/ai-images.json'
    if (-not $SkipApplication) {
        $rootEnv = Join-Path $repo '.env'
        $rootText = if (Test-Path -LiteralPath $rootEnv) { [IO.File]::ReadAllText($rootEnv) } else { '' }
        foreach ($flag in @('AI_SEARCH_ENABLED', 'AI_ASSISTANT_ENABLED', 'AI_INDEXING_ENABLED')) {
            $pattern = '(?m)^' + $flag + '=.*$'
            if ($rootText -match $pattern) { $rootText = [regex]::Replace($rootText, $pattern, $flag + '=true') }
            else { $rootText += "`n$flag=true`n" }
        }
        [IO.File]::WriteAllText($rootEnv, $rootText)
        $app = @('compose', '-f', 'docker-compose.yml', '-f', 'compose.ai.yml')
        if ($WithObservability) { $app += @('-f', 'compose.observability.yml') }
        & docker @app up -d --build --wait --wait-timeout 240 backend frontend
        if ($LASTEXITCODE -ne 0) { throw 'Application startup failed' }
    }
    Write-Host 'AI stack ready. Initial indexing runs automatically. See infra/ai/README.md.'
} finally { Pop-Location }
