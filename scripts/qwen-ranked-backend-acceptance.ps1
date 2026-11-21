$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$root = Split-Path -Parent $PSScriptRoot
$values = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $root '.env.local')) { if ($line -match '^\s*([^#=\s]+)\s*=\s*(.*?)\s*$') { $values[$matches[1]] = $matches[2] } }
$loginBody = @{ username = $values['SLP_BOOTSTRAP_ADMIN_USERNAME']; password = $values['SLP_BOOTSTRAP_ADMIN_PASSWORD'] } | ConvertTo-Json -Compress
$auth = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody -TimeoutSec 10
$headers = @{ Authorization = 'Bearer ' + $auth.accessToken }
$deps = Invoke-RestMethod -Uri "$base/api/ai/dependencies" -Headers $headers -TimeoutSec 10
if (-not $deps.rankedModelEnabled -or -not $deps.rankedModelConfigured -or $deps.rankedModelStatus -ne 'CONFIGURED') { throw 'Ranked Qwen is not configured in temporary AI process' }
Write-Output ('DEPENDENCIES=' + (@{enabled=$deps.rankedModelEnabled;configured=$deps.rankedModelConfigured;model=$deps.rankedModel;status=$deps.rankedModelStatus;python=$deps.pythonKnowledgeStatus} | ConvertTo-Json -Compress))
function Invoke-Ask([string]$Question, [string]$Mode) { $body = @{ question = $Question; mode = $Mode } | ConvertTo-Json -Depth 8 -Compress; return Invoke-RestMethod -Uri "$base/api/ai/ask" -Method Post -Headers $headers -ContentType 'application/json' -Body $body -TimeoutSec 120 }
$single = Invoke-Ask '需求分析的交付文档是什么？' 'RANKED'
if ($single.provenance.model -ne 'qwen35-ranked-lora' -or -not $single.grounding -or $single.grounding.status -ne 'EVIDENCE_CHECK_PASSED') { throw 'Single-fact RANKED provenance or grounding failed' }
Write-Output ('SINGLE_RANKED=' + (@{model=$single.provenance.model;status=$single.grounding.status;tripleCount=$single.triples.Count;answer=$single.answer;latencyMs=$single.latencyMs;callCount=$single.grounding.modelCallCount} | ConvertTo-Json -Compress))
$multi = Invoke-Ask '需求分析的方法有哪些？分别是什么？' 'RANKED'
if ($multi.provenance.model -ne 'qwen35-ranked-lora' -or -not $multi.grounding -or $multi.grounding.status -ne 'EVIDENCE_CHECK_PASSED') { throw 'Multi-fact RANKED provenance or grounding failed' }
Write-Output ('MULTI_RANKED=' + (@{model=$multi.provenance.model;status=$multi.grounding.status;selectedCount=$multi.grounding.selectedIds.Count;answer=$multi.answer;latencyMs=$multi.latencyMs} | ConvertTo-Json -Compress))
$noEvidence = Invoke-Ask '量子场论中的规范场有哪些？' 'RANKED'
if ($noEvidence.errorCode -ne 'KNOWLEDGE_INSUFFICIENT' -or $noEvidence.provenance.model -ne 'qwen35-ranked-lora') { throw 'No-evidence RANKED did not fail closed through Qwen' }
Write-Output ('NO_EVIDENCE=' + (@{model=$noEvidence.provenance.model;errorCode=$noEvidence.errorCode;answerEmpty=[string]::IsNullOrEmpty($noEvidence.answer)} | ConvertTo-Json -Compress))
foreach ($mode in @('DIRECT','TRIPLES')) { $response = Invoke-Ask '需求分析的交付文档是什么？' $mode; if ($response.provenance.model -ne $deps.model) { throw "$mode unexpectedly left the default model route" }; Write-Output ($mode + '_MODEL=' + $response.provenance.model) }
$compareBody = @{ question = '需求分析的交付文档是什么？'; includeAblations = $false } | ConvertTo-Json -Compress
$comparison = Invoke-RestMethod -Uri "$base/api/ai/compare" -Method Post -Headers $headers -ContentType 'application/json' -Body $compareBody -TimeoutSec 180
if ($comparison.Count -ne 3 -or @($comparison | Where-Object { $_.provenance.model -ne $deps.model }).Count -gt 0) { throw 'Comparison group left the default model route' }
Write-Output ('COMPARE_MODELS=' + (($comparison | ForEach-Object {$_.provenance.model}) -join ','))
$streamBody = @{ question = '需求分析的交付文档是什么？'; mode = 'RANKED' } | ConvertTo-Json -Compress
$streamResponse = Invoke-WebRequest -Uri "$base/api/ai/ask/stream" -Method Post -Headers $headers -ContentType 'application/json' -Body $streamBody -TimeoutSec 120
$stream = [string]$streamResponse.Content
$done = $stream -match '(?m)^event:\s*done\s*$'; $errorEvent = $stream -match '(?m)^event:\s*error\s*$'; $delta = $stream -match '(?m)^event:\s*delta\s*$'
if (-not $done -or $errorEvent -or -not $delta) { throw 'Qwen RANKED SSE did not finish with a validated answer' }
$frames = $stream -split '\r?\n\r?\n'; $doneFrame = ($frames | Where-Object { $_ -match '(?m)^event:\s*done\s*$' } | Select-Object -Last 1)
$doneLine = ($doneFrame -split '\r?\n' | Where-Object { $_ -match '^data:' } | Select-Object -Last 1)
$doneData = ($doneLine -replace '^data:\s*','') | ConvertFrom-Json
if ($doneData.provenance.model -ne 'qwen35-ranked-lora' -or $doneData.grounding.status -ne 'EVIDENCE_CHECK_PASSED') { throw 'SSE done payload provenance or grounding failed' }
Write-Output ('RANKED_SSE=' + (@{model=$doneData.provenance.model;grounding=$doneData.grounding.status;delta=$delta;done=$done;answer=$doneData.answer} | ConvertTo-Json -Compress))
Write-Output 'BACKEND_QWEN_ACCEPTANCE=PASS'
