# Integration check: run against a healthy local stack. Never stops services.
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$launcher = Join-Path (Split-Path -Parent $projectRoot) 'start-all.ps1'
$ports = @(5173, 8080, 8081, 8082, 8999)
function Get-Listeners {
    $result = @{}
    foreach ($port in $ports) {
        $ids = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique | Sort-Object)
        if ($ids.Count -eq 0) { throw "Local stack prerequisite missing: port $port" }
        $result[$port] = $ids -join ','
    }
    return $result
}
$before = Get-Listeners
& (Join-Path $projectRoot 'scripts/start.ps1') -ReuseRunning -SkipBuild
foreach ($port in $ports) {
    if ((Get-Listeners)[$port] -ne $before[$port]) { throw "Service was unnecessarily restarted on port $port" }
}
Write-Output 'PASS: repeated backend/frontend startup keeps all existing process IDs.'
if (-not (Test-Path -LiteralPath $launcher)) { throw "Missing one-click launcher: $launcher" }
& $launcher
foreach ($port in $ports) {
    if ((Get-Listeners)[$port] -ne $before[$port]) { throw "One-click launcher unnecessarily restarted port $port" }
}
Write-Output 'PASS: one-click startup reuses frontend, backend, and Bridge.'
