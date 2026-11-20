$ErrorActionPreference = 'Stop'
$env:OLLAMA_HOST = '127.0.0.1:11435'
$runtime = Join-Path $PSScriptRoot 'runtime/ollama-0.40.16/ollama.exe'
if (-not (Test-Path -LiteralPath $runtime)) { throw 'Portable Ollama runtime is missing.' }
Invoke-RestMethod 'http://127.0.0.1:11435/api/version' -TimeoutSec 5 | Out-Null
& $runtime pull 'qwen3.5:0.8b'
if ($LASTEXITCODE -ne 0) { throw 'Model pull failed. Inspect the Ollama server output.' }
