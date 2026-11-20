[CmdletBinding()]
param([switch]$SkipBuild, [switch]$ReuseRunning, [switch]$QwenMatrixForThisRun, [int]$StartupTimeoutSeconds = 90)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $projectRoot '.env.local'
$logDirectory = Join-Path $projectRoot 'logs'
$manifestPath = Join-Path $logDirectory 'runtime-processes.json'

function Test-LocalPort([int]$Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $pending = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        return ($pending.AsyncWaitHandle.WaitOne(400) -and $client.Connected)
    } catch { return $false } finally { $client.Dispose() }
}
function Wait-Endpoint([string]$Url, [string]$Name, [System.Diagnostics.Process]$Process = $null) {
    $deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    do {
        if ($null -ne $Process) {
            $Process.Refresh()
            if ($Process.HasExited) { throw "$Name exited during startup. Inspect logs/$Name.err.log and logs/$Name.out.log." }
        }
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) { Write-Host "$Name is ready."; return }
        } catch { }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "$Name did not become healthy at $Url. Inspect logs/$Name.out.log."
}
function Assert-ProjectPort([int]$Port) {
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    $isProject = $false
    foreach ($ownerId in @($listeners | Select-Object -ExpandProperty OwningProcess -Unique)) {
        $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$ownerId" -ErrorAction SilentlyContinue
        if ($owner -and $owner.CommandLine -and $owner.CommandLine.IndexOf($projectRoot, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
            $isProject = $true
        }
    }
    if (-not $isProject) { throw "Port $Port is occupied by another application. No process was stopped." }
}
function Test-QwenMatrixActive {
    try {
        $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/ai/matrix-health' -TimeoutSec 5
        return [bool]($health.status -eq 'UP' -and $health.qwenMatrixEnabled -and $health.qwenMatrixConfigured -and
            $health.qwenMatrixBaseModel -eq 'qwen35-base' -and $health.qwenMatrixAdapterModel -eq 'qwen35-ranked-lora')
    } catch { return $false }
}

# Check frontend first. Repeated one-click launches reuse this project's running processes.
$runningPorts = [System.Collections.Generic.HashSet[int]]::new()
foreach ($port in @(5173, 8081, 8082, 8080)) {
    if (Test-LocalPort $port) {
        if (-not $ReuseRunning) { throw "Port $port is occupied. Use -ReuseRunning to reuse this project's healthy services." }
        Assert-ProjectPort $port
        [void]$runningPorts.Add($port)
        Write-Host "Reusing project listener on port $port."
    }
}
if (-not (Test-Path -LiteralPath $envFile)) { throw 'Create .env.local from .env.example and set local credentials first.' }
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*([^#=\s]+)\s*=\s*(.*?)\s*$') {
        $value = $matches[2]
        if ([string]::IsNullOrWhiteSpace($value)) { $value = $null }
        [Environment]::SetEnvironmentVariable($matches[1], $value, 'Process')
    }
}
if ($QwenMatrixForThisRun) {
    # Spring reads the ranked.matrix namespace. Keep the persistent .env.local switch untouched.
    $env:RANKED_MATRIX_ENABLED = 'true'
    # The one-click launcher owns the local inference process; do not reuse a stale remote URL.
    $env:RANKED_MODEL_BASE_URL = 'http://127.0.0.1:8998/v1'
    if (-not $env:RANKED_MATRIX_BASE_MODEL_NAME) { $env:RANKED_MATRIX_BASE_MODEL_NAME = 'qwen35-base' }
    if ($runningPorts.Contains(8082) -and -not (Test-QwenMatrixActive)) {
        # A reused AI process may have been started without the matrix flag. Restart only this project's AI process.
        Assert-ProjectPort 8082
        $aiOwner = Get-NetTCPConnection -State Listen -LocalPort 8082 -ErrorAction SilentlyContinue |
            Select-Object -First 1 -ExpandProperty OwningProcess
        $aiProcess = if ($aiOwner) { Get-CimInstance Win32_Process -Filter "ProcessId=$aiOwner" -ErrorAction SilentlyContinue } else { $null }
        if (-not $aiProcess -or -not $aiProcess.CommandLine -or $aiProcess.CommandLine.IndexOf($projectRoot, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
            throw 'Port 8082 is not owned by this project; refusing to restart it for Qwen matrix.'
        }
        Stop-Process -Id $aiOwner -Force
        Start-Sleep -Seconds 2
        [void]$runningPorts.Remove(8082)
        Write-Host 'Restarting project ai-service with Qwen matrix enabled.'
    }
}
$jdkDirectory = $env:JAVA21_HOME
if (-not $jdkDirectory) { $jdkDirectory = 'D:/develop/Java/temurin-21/jdk-21.0.13+9' }
$javaExecutable = Join-Path $jdkDirectory 'bin/java.exe'
if (-not (Test-Path -LiteralPath $javaExecutable)) { throw "JDK 21 not found: $jdkDirectory" }
foreach ($key in @('MYSQL_USERNAME','MYSQL_PASSWORD','JWT_SECRET','RABBITMQ_USERNAME','RABBITMQ_PASSWORD')) {
    if (-not [Environment]::GetEnvironmentVariable($key, 'Process')) { throw "Missing required local setting: $key" }
}
$env:JAVA_HOME = $jdkDirectory
$env:PATH = "$jdkDirectory/bin;$env:PATH"
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$services = @(
    @{ Name='learning-service'; Port=8081 }, @{ Name='ai-service'; Port=8082 }, @{ Name='learning-gateway'; Port=8080 }
)
$missingServices = @($services | Where-Object { -not $runningPorts.Contains($_.Port) })
if ($missingServices.Count -gt 0 -and -not $SkipBuild) {
    # Package only missing services; Windows may lock JARs belonging to reused JVMs.
    $modules = ($missingServices | ForEach-Object { $_.Name }) -join ','
    & mvn -f (Join-Path $projectRoot 'backend/pom.xml') -pl $modules -am '-DskipTests' package
    if ($LASTEXITCODE -ne 0) { throw 'Backend packaging failed.' }
}
if (-not $runningPorts.Contains(5173) -and -not (Test-Path -LiteralPath (Join-Path $projectRoot 'frontend/node_modules'))) {
    Push-Location (Join-Path $projectRoot 'frontend')
    try { & npm ci; if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' } } finally { Pop-Location }
}
$startedProcesses = [System.Collections.Generic.List[object]]::new()
$retainedProcesses = [System.Collections.Generic.List[object]]::new()
if ($ReuseRunning -and (Test-Path -LiteralPath $manifestPath)) {
    foreach ($entry in (Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json)) {
        $process = Get-Process -Id $entry.Id -ErrorAction SilentlyContinue
        if ($process -and [Math]::Abs(($process.StartTime.ToUniversalTime() - ([DateTime]$entry.StartedAtUtc).ToUniversalTime()).TotalSeconds) -lt 3) {
            $retainedProcesses.Add($entry)
        }
    }
}
function Save-RuntimeManifest {
    $entries = @($retainedProcesses.ToArray()) + @($startedProcesses.ToArray())
    ConvertTo-Json -InputObject $entries -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
}
try {
    foreach ($service in $services) {
        $url = 'http://127.0.0.1:' + $service.Port + '/actuator/health'
        if ($runningPorts.Contains($service.Port)) { Wait-Endpoint $url $service.Name; continue }
        $jar = Join-Path $projectRoot ('backend/' + $service.Name + '/target/' + $service.Name + '-0.1.0-SNAPSHOT.jar')
        if (-not (Test-Path -LiteralPath $jar)) { throw "Missing packaged JAR: $jar. Run without -SkipBuild." }
        $process = Start-Process -FilePath $javaExecutable -ArgumentList @('-jar', ('"' + $jar + '"')) -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDirectory ($service.Name + '.out.log')) -RedirectStandardError (Join-Path $logDirectory ($service.Name + '.err.log'))
        $startedProcesses.Add(@{ Id=$process.Id; Name=$service.Name; StartedAtUtc=$process.StartTime.ToUniversalTime().ToString('o') })
        Save-RuntimeManifest
        Wait-Endpoint $url $service.Name $process
    }
    if ($runningPorts.Contains(5173)) {
        Wait-Endpoint 'http://127.0.0.1:5173/login' 'frontend'
    } else {
        $frontend = Start-Process -FilePath $env:ComSpec -ArgumentList @('/c', 'npm run dev -- --host 127.0.0.1 --port 5173 --strictPort') -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDirectory 'frontend.out.log') -RedirectStandardError (Join-Path $logDirectory 'frontend.err.log')
        $startedProcesses.Add(@{ Id=$frontend.Id; Name='frontend'; StartedAtUtc=$frontend.StartTime.ToUniversalTime().ToString('o') })
        Save-RuntimeManifest
        Wait-Endpoint 'http://127.0.0.1:5173/login' 'frontend' $frontend
    }
    Save-RuntimeManifest
} catch {
    foreach ($entry in $startedProcesses) { Stop-Process -Id $entry.Id -ErrorAction SilentlyContinue }
    throw
}
Write-Host 'Ready: http://127.0.0.1:5173/login'
if ($QwenMatrixForThisRun) {
    $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/ai/matrix-health' -TimeoutSec 10
    if ($health.status -ne 'UP' -or -not $health.qwenMatrixEnabled -or -not $health.qwenMatrixConfigured -or
        $health.qwenMatrixBaseModel -ne 'qwen35-base' -or $health.qwenMatrixAdapterModel -ne 'qwen35-ranked-lora') {
        throw 'Qwen matrix is not active in this AI process. It may be reusing an AI service launched without -QwenMatrixForThisRun.'
    }
    Write-Host 'Qwen 2x3 comparison matrix is enabled in this process only; .env.local was not changed.'
}
if (-not $env:OPENAI_API_KEY) { Write-Warning 'Model is not configured. Configure OPENAI_* for live AI answers.' }
