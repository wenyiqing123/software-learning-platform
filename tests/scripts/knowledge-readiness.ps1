# Regression: a successful launcher must leave the actual read-only graph retrieval usable.
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
& (Join-Path (Split-Path -Parent $projectRoot) 'start-all.ps1')
$body = @{ question='需求分析'; entities=@('需求分析'); historyEntities=@(); historyMessages=@(); spaceId=1; topK=200 } | ConvertTo-Json -Compress
$result = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8999/api/knowledge/retrieve' -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 30
if (-not $result.hasEvidence -or @($result.triples).Count -eq 0) { throw 'Launcher returned without usable software-engineering graph evidence.' }
Write-Output ('PASS: launcher left real graph retrieval ready; triples=' + @($result.triples).Count)
foreach ($line in Get-Content -LiteralPath (Join-Path $projectRoot '.env.local')) {
    if ($line -match '^\s*([^#=\s]+)\s*=\s*(.*?)\s*$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}
$loginBody = @{username=$env:SLP_BOOTSTRAP_ADMIN_USERNAME; password=$env:SLP_BOOTSTRAP_ADMIN_PASSWORD} | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:5173/api/auth/login' -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($loginBody))
try {
    $query = [Uri]::EscapeDataString('需求分析')
    $graph = Invoke-RestMethod -Uri ('http://127.0.0.1:5173/api/knowledge/graph?entity=' + $query) -Headers @{Authorization='Bearer ' + $login.accessToken} -TimeoutSec 30
    if (-not $graph.available -or @($graph.edges).Count -eq 0) { throw 'Frontend graph URL returned no real edges.' }
    Write-Output ('PASS: frontend graph URL status=200; nodes=' + @($graph.nodes).Count + '; edges=' + @($graph.edges).Count)
} finally {
    $logoutBody = @{refreshToken=$login.refreshToken} | ConvertTo-Json -Compress
    Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:5173/api/auth/logout' -ContentType 'application/json' -Body $logoutBody | Out-Null
}
