[CmdletBinding()]
param([ValidateRange(60,600)][int]$StartupTimeoutSeconds = 360)
$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$trainingRoot = Join-Path $PSScriptRoot 'qwen35-transfer-v1'
$serverScript = Join-Path $trainingRoot 'serving/openai_server.py'
$baseModel = Join-Path $trainingRoot 'base-model'
$adapter = Join-Path $PSScriptRoot 'local-adapter/best-adapter'
$settingsFile = Join-Path $projectRoot '.env.local'
$workspaceRoot = Split-Path -Parent (Split-Path -Parent $projectRoot)
$python = Join-Path $workspaceRoot '.venvs/qwen35-serving/Scripts/python.exe'
$port = 8998

foreach ($path in @($serverScript, $baseModel, $adapter, $python, $settingsFile)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Qwen local serving prerequisite is missing: $path" }
}

$values = @{}
foreach ($line in Get-Content -LiteralPath $settingsFile) {
    if ($line -match '^\s*([^#=\s]+)\s*=\s*(.*?)\s*$') { $values[$matches[1]] = $matches[2] }
}
$apiKey = [string]$values['RANKED_MODEL_API_KEY']
if ([string]::IsNullOrWhiteSpace($apiKey)) { throw 'RANKED_MODEL_API_KEY is required to authenticate the local inference endpoint.' }

$serverFullPath = [IO.Path]::GetFullPath($serverScript)
function Get-ServerProcess {
    @(Get-CimInstance Win32_Process -Filter "Name='python.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine.IndexOf($serverFullPath, [StringComparison]::OrdinalIgnoreCase) -ge 0 })
}
function Get-PortListeners {
    @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
}
function Test-ExpectedService {
    try {
        $health = Invoke-RestMethod -Uri "http://127.0.0.1:$port/health" -TimeoutSec 4
        if ($health.status -ne 'UP' -or $health.model -ne 'qwen35-ranked-lora' -or
            @($health.models) -notcontains 'qwen35-base') { return $false }
        $headers = @{ Authorization = 'Bearer ' + $apiKey }
        $models = Invoke-RestMethod -Uri "http://127.0.0.1:$port/v1/models" -Headers $headers -TimeoutSec 4
        $ids = @($models.data | ForEach-Object id)
        return ($ids -contains 'qwen35-base' -and $ids -contains 'qwen35-ranked-lora')
    } catch { return $false }
}

$listeners = Get-PortListeners
if ($listeners.Count -gt 0) {
    if (-not (Test-ExpectedService)) {
        throw "Port $port is occupied by a service that is not the verified Qwen base/LoRA endpoint. No process was stopped."
    }
    Write-Host "[reuse] Qwen base and LoRA models are healthy on 127.0.0.1:$port."
    return
}

$existingProcesses = Get-ServerProcess
$listenerOwners = @($listeners | Select-Object -ExpandProperty OwningProcess -Unique)
if ($listenerOwners.Count -gt 0) {
    # The Windows launcher can leave a parent Python process beside the
    # actual worker. Only a process owning 8998 is a serving instance.
    $existingProcesses = @($existingProcesses | Where-Object { $listenerOwners -contains $_.ProcessId })
}
if ($existingProcesses.Count -gt 1) { throw 'Multiple local Qwen inference processes were found; refusing to start another.' }
if ($existingProcesses.Count -eq 0) {
    $env:QWEN35_API_KEY = $apiKey
    $env:QWEN35_BASE_MODEL_DIR = $baseModel
    $env:QWEN35_ADAPTER_DIR = $adapter
    $env:QWEN35_HOST = '127.0.0.1'
    $env:QWEN35_PORT = [string]$port
    $env:QWEN35_MODEL_ID = [string]$(if ($values['RANKED_MODEL_NAME']) { $values['RANKED_MODEL_NAME'] } else { 'qwen35-ranked-lora' })
    $env:QWEN35_BASE_MODEL_ID = [string]$(if ($values['QWEN_BASE_MODEL_NAME']) { $values['QWEN_BASE_MODEL_NAME'] } else { 'qwen35-base' })
    $env:QWEN35_MAX_NEW_TOKENS = [string]$(if ($values['RANKED_MODEL_MAX_TOKENS']) { $values['RANKED_MODEL_MAX_TOKENS'] } else { '512' })
    $env:QWEN35_MIN_FREE_VRAM_MIB = '3072'
    New-Item -ItemType Directory -Path (Join-Path $projectRoot 'logs') -Force | Out-Null
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $stdout = Join-Path $projectRoot "logs/qwen35-serving-$stamp.out.log"
    $stderr = Join-Path $projectRoot "logs/qwen35-serving-$stamp.err.log"
    $process = Start-Process -FilePath $python -ArgumentList @('-X','utf8',('"' + $serverFullPath + '"')) `
        -WorkingDirectory (Split-Path $serverFullPath -Parent) -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    Write-Host "[start] Qwen inference process $($process.Id) is loading the verified base and adapter."
    Write-Host "       Logs: $stdout and $stderr"
} else {
    Write-Host "[wait] Existing Qwen process $($existingProcesses[0].ProcessId) is still loading."
}

$deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
$nextNotice = [DateTime]::UtcNow.AddSeconds(15)
do {
    if (Test-ExpectedService) {
        Write-Host "[ready] Base (adapter disabled) and LoRA models are available on 127.0.0.1:$port."
        return
    }
    if ($existingProcesses.Count -gt 0) {
        $current = Get-CimInstance Win32_Process -Filter "ProcessId=$($existingProcesses[0].ProcessId)" -ErrorAction SilentlyContinue
        if (-not $current) { throw 'The previously detected Qwen process exited before becoming healthy.' }
    } elseif ($process) {
        $process.Refresh()
        if ($process.HasExited) { throw "Qwen inference exited during startup; inspect $stdout and $stderr." }
    }
    if ([DateTime]::UtcNow -ge $nextNotice) {
        Write-Host '       Still loading the local model; no other processes were touched.'
        $nextNotice = [DateTime]::UtcNow.AddSeconds(20)
    }
    Start-Sleep -Seconds 2
} while ([DateTime]::UtcNow -lt $deadline)

throw "Qwen model did not become healthy within $StartupTimeoutSeconds seconds. Any started process was left running; inspect its logs before retrying."
