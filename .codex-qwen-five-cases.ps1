$ErrorActionPreference = 'Stop'
$root = 'D:\develop\PythonProject\pythonallwork\毕设\software-learning-platform'
$values = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $root '.env.local')) { if ($line -match '^\s*([^#=\s]+)\s*=\s*(.*?)\s*$') { $values[$matches[1]] = $matches[2] } }
if ($values['RANKED_MODEL_ENABLED'] -ne 'false') { throw 'Persistent Qwen flag must remain off' }
$loginBody = @{username=$values['SLP_BOOTSTRAP_ADMIN_USERNAME'];password=$values['SLP_BOOTSTRAP_ADMIN_PASSWORD']} | ConvertTo-Json -Compress
$auth = Invoke-RestMethod 'http://127.0.0.1:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody -TimeoutSec 10
$headers = @{Authorization='Bearer '+$auth.accessToken}
$cases = @(
    @{id='software-analysis-deliverable';point='需求分析';question='需求分析的交付文档是什么？'},
    @{id='dfd-process-naming';point='数据流图加工命名';question='加工命名的语法结构是什么？'},
    @{id='use-case-perspective';point='用例图';question='用例图的视角是什么？'},
    @{id='ticket-system-lifecycle';point='机票预定系统';question='机票预定系统遵循的过程是什么？'},
    @{id='srs-contents';point='软件需求规格说明书';question='软件需求规格说明书包含什么？'}
)
$results = [System.Collections.Generic.List[object]]::new()
foreach ($case in $cases) {
    $body = @{question=$case.question;mode='RANKED'} | ConvertTo-Json -Compress
    $response = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/ai/ask' -Method Post -Headers $headers -ContentType 'application/json' -Body $body -TimeoutSec 120
    $pass = ($response.provenance.model -eq 'qwen35-ranked-lora' -and $response.grounding.status -eq 'EVIDENCE_CHECK_PASSED' -and -not $response.errorCode -and -not [string]::IsNullOrWhiteSpace($response.answer))
    $results.Add([pscustomobject]@{caseId=$case.id;knowledgePoint=$case.point;question=$case.question;passed=$pass;model=$response.provenance.model;answer=$response.answer;errorCode=$response.errorCode;groundingStatus=$response.grounding.status;taskType=$response.grounding.taskType;selectedIds=$response.grounding.selectedIds;selectedEvidence=$response.grounding.selectedEvidence;selectionRawResponse=$response.grounding.selectionRawResponse;generationRawResponse=$response.grounding.generationRawResponse;candidateCount=@($response.grounding.candidates).Count;modelCallCount=$response.grounding.modelCallCount;latencyMs=$response.latencyMs;tokenUsage=$response.usage})
    Write-Output (($case.id)+':'+$(if($pass){'PASS'}else{'FAIL'})+' model='+$response.provenance.model+' grounding='+$response.grounding.status+' latencyMs='+$response.latencyMs)
}
$report = [pscustomobject]@{generatedAt=(Get-Date).ToString('o');featureFlagPersisted=$values['RANKED_MODEL_ENABLED'];rankedModelEnabledInTemporaryProcess=$true;modelId='qwen35-ranked-lora';device=(Invoke-RestMethod 'http://127.0.0.1:8998/health').device;gpu=(Invoke-RestMethod 'http://127.0.0.1:8998/health').gpu;testLabelReviewStatus='UNREVIEWED_EXPLORATORY_TRAINING_LABELS';cases=$results.ToArray();passed=@($results|Where-Object passed).Count;total=$cases.Count}
$destination=Join-Path $root 'model-training\results\qwen35-integration-20261118\ranked-live-cases.json'
New-Item -ItemType Directory -Path (Split-Path $destination -Parent) -Force | Out-Null
$report | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $destination -Encoding UTF8
Write-Output ('SUMMARY='+(@{passed=$report.passed;total=$report.total;report=$destination}|ConvertTo-Json -Compress))
if($report.passed -ne $report.total){exit 1}
