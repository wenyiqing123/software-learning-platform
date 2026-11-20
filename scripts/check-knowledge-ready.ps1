[CmdletBinding()]
param(
    [Uri]$BridgeBaseUrl = 'http://127.0.0.1:8999',
    [ValidateRange(1,240)][int]$TimeoutSeconds = 90
)
$ErrorActionPreference = 'Stop'
if (-not $BridgeBaseUrl.IsLoopback -or $BridgeBaseUrl.Scheme -ne 'http') {
    throw '图谱启动检查只允许本机 HTTP Bridge 地址。'
}
$baseUrl = $BridgeBaseUrl.AbsoluteUri.TrimEnd('/')
try {
    $health = Invoke-RestMethod -Uri ($baseUrl + '/health') -TimeoutSec 5
} catch {
    throw 'Python Bridge 未启动或健康检查失败，请在现有 LLM 环境启动 python-knowledge-bridge/run.ps1。'
}
if ($health.status -ne 'UP') { throw 'Python Bridge 未就绪。' }
Write-Host '[检查] 实际检索需求分析，等待评分模型首次加载 ...'
$body = @{ question='需求分析'; entities=@('需求分析'); historyEntities=@(); historyMessages=@(); relation=$null; spaceId=1; topK=200 } | ConvertTo-Json -Compress
try {
    $result = Invoke-RestMethod -Method Post -Uri ($baseUrl + '/api/knowledge/retrieve') -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec $TimeoutSeconds
} catch {
    throw 'Python Bridge 存活，但真实图谱检索失败。NebulaGraph 重启后旧客户端可能失效；请在 LLM 环境重启现有 Bridge，再双击启动。详情见 logs/python-bridge-local.err.log。'
}
if (-not $result.hasEvidence -or @($result.triples).Count -eq 0) {
    throw '未检索到需求分析的知识证据，请检查原图空间；不报告启动成功。'
}
Write-Host ('[图谱检索就绪] 需求分析返回 ' + @($result.triples).Count + ' 条三元组。')
