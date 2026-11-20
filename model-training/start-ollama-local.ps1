[CmdletBinding()]
param([ValidateRange(1024,65535)][int]$Port = 11435, [switch]$UseSystemProxy)
$ErrorActionPreference = 'Stop'
$runtime = Join-Path $PSScriptRoot 'runtime/ollama-0.40.16/ollama.exe'
if (-not (Test-Path -LiteralPath $runtime)) { throw "Portable runtime is missing: $runtime" }
$listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
if ($listener) {
    $version = Invoke-RestMethod "http://127.0.0.1:$Port/api/version" -TimeoutSec 5
    Write-Host "An Ollama server is already listening on port $Port, version $($version.version)."
    return
}
$env:OLLAMA_HOST = "127.0.0.1:$Port"
$env:OLLAMA_MODELS = 'D:/model'
$env:OLLAMA_NUM_PARALLEL = '1'
$env:OLLAMA_MAX_LOADED_MODELS = '1'
$env:OLLAMA_CONTEXT_LENGTH = '2048'
$env:NO_PROXY = '127.0.0.1,localhost,::1'
if ($UseSystemProxy) {
    $proxy = Get-ItemProperty 'HKCU:/Software/Microsoft/Windows/CurrentVersion/Internet Settings'
    if ($proxy.ProxyEnable -eq 1 -and $proxy.ProxyServer -match '^127\.0\.0\.1:\d+$') {
        $env:HTTPS_PROXY = 'http://' + $proxy.ProxyServer
    } else { throw 'No single loopback system proxy endpoint was found.' }
}
Write-Host "Ollama local API: http://127.0.0.1:$Port ; model store: D:/model ; Ctrl+C stops this server."
& $runtime serve
if ($LASTEXITCODE -ne 0) { throw "Ollama exited with code $LASTEXITCODE" }
