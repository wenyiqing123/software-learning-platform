$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$manifest = Join-Path $projectRoot 'logs/runtime-processes.json'
$allProcesses = @(Get-CimInstance Win32_Process)
$ownedIds = [System.Collections.Generic.HashSet[int]]::new()
if (Test-Path -LiteralPath $manifest) {
    foreach ($entry in @(Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json)) {
        $process = $allProcesses | Where-Object ProcessId -eq $entry.Id | Select-Object -First 1
        if ($process -and $process.CreationDate) {
            $expected = [DateTime]::Parse($entry.StartedAtUtc).ToUniversalTime()
            $actual = $process.CreationDate.ToUniversalTime()
            if ([Math]::Abs(($actual - $expected).TotalSeconds) -lt 3) { [void]$ownedIds.Add([int]$entry.Id) }
        }
    }
}
foreach ($process in $allProcesses) {
    if ($process.Name -in @('java.exe','node.exe','cmd.exe') -and $process.CommandLine -and $process.CommandLine.Contains($projectRoot)) { [void]$ownedIds.Add([int]$process.ProcessId) }
}
do {
    $added = $false
    foreach ($process in $allProcesses) {
        if ($ownedIds.Contains([int]$process.ParentProcessId) -and -not $ownedIds.Contains([int]$process.ProcessId)) { [void]$ownedIds.Add([int]$process.ProcessId); $added=$true }
    }
} while ($added)
foreach ($processId in $ownedIds) { Stop-Process -Id $processId -ErrorAction SilentlyContinue }
Write-Output ("Stopped " + $ownedIds.Count + ' project processes.')
