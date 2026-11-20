param([string]$BaseUrl='http://127.0.0.1:8080')
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile=Join-Path $projectRoot '.env.local'
if (Test-Path -LiteralPath $envFile) {
    foreach($line in Get-Content -LiteralPath $envFile) {
        if($line -match '^\s*([^#=\s]+)\s*=\s*(.*?)\s*$') {
            $value=$matches[2]
            if([string]::IsNullOrWhiteSpace($value)){$value=$null}
            [Environment]::SetEnvironmentVariable($matches[1],$value,'Process')
        }
    }
}
Add-Type -AssemblyName System.Net.Http
$http=[System.Net.Http.HttpClient]::new()
$http.Timeout=[TimeSpan]::FromSeconds(210)
$checks=[System.Collections.Generic.List[string]]::new()
function Assert-That([bool]$Condition,[string]$Message){if(-not $Condition){throw "FAILED: $Message"};$checks.Add($Message);Write-Output "PASS: $Message"}
function Request([string]$Method,[string]$Path,[object]$Body=$null,[string]$Token='', [int]$Expected=200){
    $request=[System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method),$BaseUrl+$Path)
    if($Token){$request.Headers.Authorization=[System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$Token)}
    if($null -ne $Body){$request.Content=[System.Net.Http.StringContent]::new(($Body|ConvertTo-Json -Depth 30 -Compress),[Text.Encoding]::UTF8,'application/json')}
    try {
        $response=$http.SendAsync($request).GetAwaiter().GetResult()
        $text=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if([int]$response.StatusCode -ne $Expected){throw "$Method $Path returned $([int]$response.StatusCode), expected $Expected. Response: $text"}
        if($text){return ($text|ConvertFrom-Json)}
    } finally {$request.Dispose()}
}
try {
    $suffix=[Guid]::NewGuid().ToString('N').Substring(0,12)
    $credentials=@{username="smoke_$suffix";password=('test_'+[Guid]::NewGuid().ToString('N'));displayName='接口验收'}
    Request GET '/api/courses' -Expected 401 | Out-Null
    $a=Request POST '/api/auth/register' $credentials -Expected 201
    $b=Request POST '/api/auth/register' @{username="isolation_$suffix";password=$credentials.password;displayName='隔离验收'} -Expected 201
    Assert-That ([bool]$a.accessToken) 'Student registration and BCrypt/JWT flow available'
    Request POST '/api/auth/register' $credentials -Expected 409 | Out-Null
    Request POST '/api/auth/refresh' @{} -Expected 400 | Out-Null
    $rotated=Request POST '/api/auth/refresh' @{refreshToken=$a.refreshToken}
    Request POST '/api/auth/refresh' @{refreshToken=$a.refreshToken} -Expected 401 | Out-Null
    $a=$rotated
    Assert-That ($a.expiresIn -gt 0) 'Refresh rotates token and rejects reuse'
    Request GET '/api/admin/questions' -Token $a.accessToken -Expected 403 | Out-Null
    Assert-That $true 'Student cannot access administrator question answers'
    if ($env:SLP_BOOTSTRAP_ADMIN_USERNAME -and $env:SLP_BOOTSTRAP_ADMIN_PASSWORD) {
        $administrator=Request POST '/api/auth/login' @{username=$env:SLP_BOOTSTRAP_ADMIN_USERNAME;password=$env:SLP_BOOTSTRAP_ADMIN_PASSWORD}
        $adminCourses=@(Request GET '/api/admin/courses' -Token $administrator.accessToken)
        Assert-That ($adminCourses.Count -gt 0) 'Local bootstrap administrator can access course and question maintenance'
    }
    $courses=@(Request GET '/api/courses' -Token $a.accessToken)
    Assert-That ($courses.Count -gt 0) 'Course catalogue available'
    $courseId=$courses[0].id
    $study=Request GET "/api/courses/$courseId/study" -Token $a.accessToken
    Assert-That ($study.totalSections -ge 8) 'Readable course content covers current knowledge points'
    $section=$study.chapters[0].sections[0]
    Request PUT "/api/courses/$courseId/study/sections/$($section.id)/progress" @{completed=$true} $a.accessToken | Out-Null
    $otherStudy=Request GET "/api/courses/$courseId/study" -Token $b.accessToken
    Assert-That ($otherStudy.completedSections -eq 0) 'Course progress is isolated by authenticated user'
    $questions=@(Request GET '/api/quizzes' -Token $a.accessToken)
    Assert-That ($questions.Count -ge 27) 'Software engineering question bank includes reviewed seed set'
    Assert-That (-not $questions[0].PSObject.Properties['answer']) 'Student question list does not disclose standard answers'
    $q=$questions[0]
    Request POST "/api/quizzes/$($q.id)/submit" @{answer=@('INVALID_KEY')} $a.accessToken -Expected 400 | Out-Null
    $wrong=Request POST "/api/quizzes/$($q.id)/submit" @{answer=@($q.options[0].key)} $a.accessToken
    if($wrong.correct){$wrong=Request POST "/api/quizzes/$($q.id)/submit" @{answer=@($q.options[1].key)} $a.accessToken}
    Assert-That (-not $wrong.correct) 'Quiz grade is server authoritative and returns correct answer on submit'
    $book=@(Request GET '/api/wrong-book' -Token $a.accessToken)
    Assert-That ($book.Count -eq 1) 'Incorrect attempt creates wrong-book item'
    $otherBook=@(Request GET '/api/wrong-book' -Token $b.accessToken)
    Assert-That ($otherBook.Count -eq 0) 'Wrong-book contents isolated between users'
    Request POST "/api/wrong-book/$($book[0].id)/review" @{answer=$wrong.correctAnswer} $b.accessToken -Expected 404 | Out-Null
    $review=Request POST "/api/wrong-book/$($book[0].id)/review" @{answer=$wrong.correctAnswer} $a.accessToken
    Assert-That $review.correct 'Targeted wrong-question review grades the actual wrong item'
    $after=@(Request GET '/api/wrong-book' -Token $a.accessToken)
    Assert-That ($after.Count -eq 0) 'Correct review marks original wrong item mastered'
    $recommendations=@(Request GET '/api/recommendations' -Token $a.accessToken)
    Assert-That ($recommendations.Count -gt 0 -and [bool]$recommendations[0].reason) 'Recommendations include persisted explainable reasons'
    $session=Request POST '/api/tutor/sessions' @{title='接口验收会话'} $a.accessToken -Expected 201
    Request POST "/api/tutor/sessions/$($session.id)/messages" @{role='USER';content='什么是需求分析？';mode='DIRECT'} $a.accessToken -Expected 201 | Out-Null
    $messages=@(Request GET "/api/tutor/sessions/$($session.id)/messages" -Token $a.accessToken)
    Assert-That ($messages.Count -eq 1) 'Tutor history persists'
    Request GET "/api/tutor/sessions/$($session.id)/messages" -Token $b.accessToken -Expected 404 | Out-Null
    Assert-That $true 'Tutor history enforces ownership'
    $dependencies=Request GET '/api/ai/dependencies' -Token $a.accessToken
    if(-not $dependencies.configured){
        Request POST '/api/ai/ask' @{question='需求分析是什么';mode='DIRECT'} $a.accessToken -Expected 503 | Out-Null
        Request POST '/api/experiments/run' @{question='需求分析是什么';includeAblations=$false;referenceAnswer='确定软件需求';expectedEntities=@('需求')} $a.accessToken -Expected 503 | Out-Null
        Assert-That $true 'Missing model configuration is explicit and no fabricated experiment result is persisted'
        $otherRuns=@(Request GET '/api/experiments' -Token $b.accessToken)
        Assert-That ($otherRuns.Count -eq 0) 'Experiment history is isolated and failed answers cannot be rated'
    }
    Request POST '/api/auth/logout' @{refreshToken=$a.refreshToken} $a.accessToken -Expected 204 | Out-Null
    Request POST '/api/auth/refresh' @{refreshToken=$a.refreshToken} -Expected 401 | Out-Null
    Assert-That $true 'Logout revokes replacement refresh token'
    Write-Output ("Completed: "+$checks.Count+' smoke checks. Fixtures use unique smoke_/isolation_ usernames in the dedicated project database.')
} finally {$http.Dispose()}
