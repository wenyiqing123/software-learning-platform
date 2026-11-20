[CmdletBinding()]
param(
    [string]$ImageTag = (Get-Date -Format 'yyyyMMdd-HHmmss'),
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$workspaceRoot = Split-Path -Parent (Split-Path -Parent $repoRoot)
$bridgeSource = Join-Path $workspaceRoot 'Mecha-QA\python-knowledge-bridge'
$modelSource = Join-Path $workspaceRoot 'Mecha-QA\output\air_ticket-finetuned-model-riddle_new\model'
$stageRoot = [System.IO.Path]::GetFullPath((Join-Path $env:TEMP "slp-bridge-stage-$([guid]::NewGuid().ToString('N'))"))

if ($ImageTag -notmatch '^[A-Za-z0-9_.-]+$') {
    throw 'ImageTag may contain only letters, digits, dots, underscores, and hyphens.'
}
if (-not (Test-Path -LiteralPath $bridgeSource -PathType Container)) {
    throw "Python Knowledge Bridge source was not found: $bridgeSource"
}
if (-not (Test-Path -LiteralPath $modelSource -PathType Container)) {
    throw "Mecha-QA scoring model was not found: $modelSource"
}

if (-not [string]::IsNullOrWhiteSpace($JavaHome)) {
    $resolvedJavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
    $env:JAVA_HOME = $resolvedJavaHome
    $env:Path = "$resolvedJavaHome\bin;$env:Path"
}
$javaVersion = (& java -version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch 'version "21\.') {
    throw 'JDK 21 is required. Set JAVA_HOME or pass -JavaHome.'
}

$platform = (& docker info --format '{{.OSType}}/{{.Architecture}}' 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $platform -notmatch '^linux/(x86_64|amd64)$') {
    throw "A running Linux Docker Engine with amd64 support is required; found '$platform'."
}

Push-Location $repoRoot
try {
    & mvn -f backend/pom.xml package
    if ($LASTEXITCODE -ne 0) { throw 'Maven package/test failed.' }

    Push-Location (Join-Path $repoRoot 'frontend')
    try {
        & npm ci
        if ($LASTEXITCODE -ne 0) { throw 'npm ci failed.' }
        & npm test
        if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed.' }
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend production build failed.' }
    } finally {
        Pop-Location
    }

    $modules = @(
        @{ Name = 'learning-service'; Port = 8081 },
        @{ Name = 'ai-service'; Port = 8082 },
        @{ Name = 'learning-gateway'; Port = 8080 }
    )
    foreach ($module in $modules) {
        $moduleDir = Join-Path $repoRoot "backend\$($module.Name)\target"
        $jar = Get-ChildItem -LiteralPath $moduleDir -Filter "$($module.Name)-*.jar" -File |
            Where-Object { $_.Name -notlike '*.original.jar' -and $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-javadoc.jar' } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($null -eq $jar) { throw "Packaged JAR not found for $($module.Name)." }

        $jarFile = [System.IO.Path]::GetRelativePath($repoRoot, $jar.FullName).Replace('\', '/')
        & docker buildx build --platform linux/amd64 --load `
            --build-arg "JAR_FILE=$jarFile" --build-arg "APP_PORT=$($module.Port)" `
            --tag "software-learning/$($module.Name):$ImageTag" `
            --file deploy/docker/java.Dockerfile .
        if ($LASTEXITCODE -ne 0) { throw "Docker build failed for $($module.Name)." }
    }

    & docker buildx build --platform linux/amd64 --load `
        --tag "software-learning/frontend:$ImageTag" `
        --file deploy/docker/frontend.Dockerfile .
    if ($LASTEXITCODE -ne 0) { throw 'Docker build failed for frontend.' }

    New-Item -ItemType Directory -Path $stageRoot -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $bridgeSource 'requirements.txt') -Destination $stageRoot
    Get-ChildItem -LiteralPath $bridgeSource -Filter '*.py' -File | Copy-Item -Destination $stageRoot
    Copy-Item -LiteralPath (Join-Path $repoRoot 'deploy\docker\python-bridge.Dockerfile') `
        -Destination (Join-Path $stageRoot 'Dockerfile')

    & docker buildx build --platform linux/amd64 --load `
        --tag "software-learning/python-bridge:$ImageTag" `
        --file (Join-Path $stageRoot 'Dockerfile') $stageRoot
    if ($LASTEXITCODE -ne 0) { throw 'Docker build failed for python-bridge.' }

    $images = @(
        "software-learning/learning-service:$ImageTag",
        "software-learning/ai-service:$ImageTag",
        "software-learning/learning-gateway:$ImageTag",
        "software-learning/frontend:$ImageTag",
        "software-learning/python-bridge:$ImageTag"
    )
    foreach ($image in $images) {
        $architecture = (& docker image inspect --format '{{.Os}}/{{.Architecture}}' $image 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or $architecture -ne 'linux/amd64') {
            throw "Image architecture check failed for ${image}: $architecture"
        }
    }

    Write-Output "Built linux/amd64 images with tag $ImageTag"
    $images | ForEach-Object { Write-Output $_ }
} finally {
    Pop-Location
    $tempRoot = [System.IO.Path]::GetFullPath($env:TEMP).TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if ($stageRoot.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path -Leaf $stageRoot) -like 'slp-bridge-stage-*' -and
        (Test-Path -LiteralPath $stageRoot)) {
        Remove-Item -LiteralPath $stageRoot -Recurse -Force
    }
}
