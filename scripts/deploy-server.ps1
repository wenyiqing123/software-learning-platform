[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_.-]+$')]
    [string]$ImageTag,

    [string]$Server = 'root@120.26.186.201',
    [string]$JavaHome = 'D:\develop\Java\temurin-21\jdk-21.0.13+9',
    [string]$NebulaPassword = 'nebula'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$workspaceRoot = Split-Path -Parent (Split-Path -Parent $repoRoot)
$mechaQaRoot = Join-Path $workspaceRoot 'Mecha-QA'
$modelPath = Join-Path $mechaQaRoot 'output\air_ticket-finetuned-model-riddle_new\model'
$nebulaPath = Join-Path $mechaQaRoot 'Mecha-QA-main\nebula'
$releaseRoot = "/opt/software-learning-platform/releases/$ImageTag"
$remoteGraphRoot = "$releaseRoot/graph"
$artifactRoot = Join-Path $repoRoot 'deploy-artifacts'
$imageArchive = Join-Path $artifactRoot "$ImageTag.images.tar"
$graphArchive = Join-Path $artifactRoot "$ImageTag.graph.tar"
$images = @(
    "software-learning/learning-service:$ImageTag",
    "software-learning/ai-service:$ImageTag",
    "software-learning/learning-gateway:$ImageTag",
    "software-learning/frontend:$ImageTag",
    "software-learning/python-bridge:$ImageTag"
)

foreach ($path in @($modelPath, $nebulaPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Container)) {
        throw "Required read-only Mecha-QA dependency is missing: $path"
    }
}
$remoteCheck = & ssh -o BatchMode=yes -o ConnectTimeout=10 $Server 'docker info >/dev/null'
if ($LASTEXITCODE -ne 0) { throw 'The approved Ubuntu Docker server is unavailable or not prepared.' }

foreach ($image in $images) {
    $architecture = (& docker image inspect --format '{{.Os}}/{{.Architecture}}' $image 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $architecture -ne 'linux/amd64') {
        throw "Required linux/amd64 image is missing: $image"
    }
}

New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null
$artifactRootResolved = [System.IO.Path]::GetFullPath($artifactRoot)
if (-not $artifactRootResolved.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Refusing to create deployment archives outside the repository deployment-artifacts directory.'
}
if ((Test-Path -LiteralPath $imageArchive) -or (Test-Path -LiteralPath $graphArchive)) {
    throw "Deployment archive already exists for tag $ImageTag; choose a new ImageTag."
}

& docker save --output $imageArchive @images
if ($LASTEXITCODE -ne 0) { throw 'Docker image archive creation failed.' }

Push-Location $mechaQaRoot
try {
    & tar.exe -cf $graphArchive 'Mecha-QA-main/nebula' 'output/air_ticket-finetuned-model-riddle_new/model'
    if ($LASTEXITCODE -ne 0) { throw 'Mecha-QA read-only dependency archive creation failed.' }
} finally {
    Pop-Location
}

$imageHash = (Get-FileHash -LiteralPath $imageArchive -Algorithm SHA256).Hash.ToLowerInvariant()
$graphHash = (Get-FileHash -LiteralPath $graphArchive -Algorithm SHA256).Hash.ToLowerInvariant()
$imageChecksum = "$imageArchive.sha256"
$graphChecksum = "$graphArchive.sha256"
[System.IO.File]::WriteAllText($imageChecksum, "$imageHash  $([System.IO.Path]::GetFileName($imageArchive))`n", [System.Text.Encoding]::ASCII)
[System.IO.File]::WriteAllText($graphChecksum, "$graphHash  $([System.IO.Path]::GetFileName($graphArchive))`n", [System.Text.Encoding]::ASCII)

& tar.exe -tf $graphArchive
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the graph archive file list.' }

$releaseCheck = & ssh -o BatchMode=yes $Server "test ! -e '$releaseRoot'"
if ($LASTEXITCODE -ne 0) { throw "Versioned release path already exists or is unavailable: $releaseRoot" }

& "$PSScriptRoot/create-server-env.ps1" -ImageTag $ImageTag -RemoteGraphRoot $remoteGraphRoot `
    -Server $Server -NebulaPassword $NebulaPassword
if ($LASTEXITCODE -ne 0) { throw 'Server environment preparation failed.' }

& ssh -o BatchMode=yes $Server "install -d -m 0750 '$releaseRoot' '$remoteGraphRoot'"
if ($LASTEXITCODE -ne 0) { throw 'Could not create the versioned server release directory.' }

& scp -q -o BatchMode=yes $imageArchive $imageChecksum $graphArchive $graphChecksum `
    "${Server}:$releaseRoot/"
if ($LASTEXITCODE -ne 0) { throw 'Archive transfer failed.' }
& scp -q -o BatchMode=yes deploy/compose.server.yml deploy/server/provision-data.sh `
    deploy/server/nginx-software-learning.conf deploy/server/run-deploy.sh "${Server}:$releaseRoot/"
if ($LASTEXITCODE -ne 0) { throw 'Deployment configuration transfer failed.' }

$remoteCommand = "bash '$releaseRoot/run-deploy.sh' '$releaseRoot'"
& ssh -o BatchMode=yes -o ConnectTimeout=15 $Server $remoteCommand
if ($LASTEXITCODE -ne 0) {
    throw 'Remote deployment failed. Nginx replacement is automatically rolled back if validation or its route check fails.'
}

Write-Output "Deployment artifacts retained under $artifactRoot. Run credentialed smoke tests only through the SSH tunnel documented in README.md; never send credentials or bearer tokens over the public HTTP URL."
