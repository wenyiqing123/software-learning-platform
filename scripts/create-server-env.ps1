[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_.-]+$')]
    [string]$ImageTag,

    [Parameter(Mandatory = $true)]
    [string]$RemoteGraphRoot,

    [string]$Server = 'root@120.26.186.201',
    [string]$LocalEnvFile = (Join-Path $PSScriptRoot '..\.env.local'),
    [string]$NebulaPassword = 'nebula'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Read-EnvFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required environment file is missing: $Path"
    }
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*#' -or $line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') { continue }
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                                      ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$Matches[1]] = $value
    }
    return $values
}

function New-HexSecret([int]$ByteCount) {
    return [Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes($ByteCount)).ToLowerInvariant()
}

function New-ScopedUsername([string]$Prefix) {
    return "$Prefix$((New-HexSecret 6))"
}

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$localValues = Read-EnvFile ([System.IO.Path]::GetFullPath($LocalEnvFile))
$requiredKeys = @('OPENAI_API_KEY', 'SLP_BOOTSTRAP_ADMIN_USERNAME', 'SLP_BOOTSTRAP_ADMIN_PASSWORD')
foreach ($key in $requiredKeys) {
    if (-not $localValues.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($localValues[$key])) {
        throw "The local environment does not contain $key."
    }
}
foreach ($key in $requiredKeys) {
    if ($localValues[$key] -match '[\r\n]') { throw "The local $key value cannot contain a newline." }
}
if ($localValues['SLP_BOOTSTRAP_ADMIN_PASSWORD'].Length -lt 12) {
    throw 'The configured bootstrap administrator password is shorter than 12 characters.'
}
if ([string]::IsNullOrWhiteSpace($NebulaPassword) -or $NebulaPassword -match '[\r\n]') {
    throw 'A valid NebulaGraph password is required.'
}
if ($RemoteGraphRoot -notmatch '^/opt/software-learning-platform/releases/[A-Za-z0-9_.-]+/graph$') {
    throw 'RemoteGraphRoot must be a graph directory inside a versioned release under /opt/software-learning-platform.'
}

$remotePath = '/opt/software-learning-platform/.env.server'
$exists = & ssh -o BatchMode=yes -o ConnectTimeout=10 $Server "test -f '$remotePath'"
if ($LASTEXITCODE -eq 0) {
    $currentUsers = & ssh -o BatchMode=yes $Server "grep -E '^(MYSQL_USERNAME|RABBITMQ_USERNAME|REDIS_USERNAME)=' '$remotePath'"
    if ($LASTEXITCODE -ne 0) { throw 'Could not read the server application usernames.' }
    $usernameMap = @{}
    foreach ($line in $currentUsers) {
        if ($line -match '^(MYSQL_USERNAME|RABBITMQ_USERNAME|REDIS_USERNAME)=(.*)$') {
            $usernameMap[$Matches[1]] = $Matches[2]
        }
    }
    foreach ($key in @('MYSQL_USERNAME', 'RABBITMQ_USERNAME', 'REDIS_USERNAME')) {
        if (-not $usernameMap.ContainsKey($key)) { throw "The server environment is missing $key." }
    }
    $updates = @(
        "sed -i 's|^IMAGE_TAG=.*|IMAGE_TAG=$ImageTag|' '$remotePath'",
        "sed -i 's|^MECHA_QA_ROOT=.*|MECHA_QA_ROOT=$RemoteGraphRoot|' '$remotePath'"
    )
    if ($usernameMap['MYSQL_USERNAME'] -eq 'software_learning_app') {
        $updates += "sed -i 's|^MYSQL_USERNAME=.*|MYSQL_USERNAME=$(New-ScopedUsername 'slp_mysql_')|' '$remotePath'"
    }
    if ($usernameMap['RABBITMQ_USERNAME'] -eq 'software_learning_app') {
        $updates += "sed -i 's|^RABBITMQ_USERNAME=.*|RABBITMQ_USERNAME=$(New-ScopedUsername 'slp_rabbit_')|' '$remotePath'"
    }
    if ($usernameMap['REDIS_USERNAME'] -eq 'software_learning') {
        $updates += "sed -i 's|^REDIS_USERNAME=.*|REDIS_USERNAME=$(New-ScopedUsername 'slp_redis_')|' '$remotePath'"
    }
    $updates += "chmod 0600 '$remotePath'"
    & ssh -o BatchMode=yes $Server ($updates -join ' && ')
    if ($LASTEXITCODE -ne 0) { throw 'Could not update non-secret release settings in the server environment.' }
    Write-Output 'Existing server environment retained; only the image tag and graph release path were updated.'
    return
} elseif ($LASTEXITCODE -ne 1) {
    throw 'Could not check the server environment or connect to the server.'
}

$jwtSecret = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
$variables = [ordered]@{
    IMAGE_TAG = $ImageTag
    MECHA_QA_ROOT = $RemoteGraphRoot
    MYSQL_HOST = 'agent-chat-mysql'
    MYSQL_PORT = '3306'
    MYSQL_DATABASE = 'software_learning'
    MYSQL_USERNAME = New-ScopedUsername 'slp_mysql_'
    MYSQL_PASSWORD = New-HexSecret 32
    RABBITMQ_HOST = 'rabbitmq'
    RABBITMQ_PORT = '5672'
    RABBITMQ_VHOST = '/software-learning'
    RABBITMQ_USERNAME = New-ScopedUsername 'slp_rabbit_'
    RABBITMQ_PASSWORD = New-HexSecret 32
    REDIS_HOST = 'redis'
    REDIS_PORT = '6379'
    REDIS_USERNAME = New-ScopedUsername 'slp_redis_'
    REDIS_PASSWORD = New-HexSecret 32
    JWT_SECRET = $jwtSecret
    NEBULA_HOST = 'agent-chat-nebula-graphd'
    NEBULA_PORT = '9669'
    NEBULA_ACCOUNT = 'root'
    NEBULA_PASSWORD = $NebulaPassword
    OPENAI_BASE_URL = if ($localValues.ContainsKey('OPENAI_BASE_URL')) { $localValues['OPENAI_BASE_URL'] } else { 'https://api.deepseek.com' }
    OPENAI_API_KEY = $localValues['OPENAI_API_KEY']
    OPENAI_MODEL = if ($localValues.ContainsKey('OPENAI_MODEL')) { $localValues['OPENAI_MODEL'] } else { 'deepseek-flash' }
    SLP_BOOTSTRAP_ADMIN_USERNAME = $localValues['SLP_BOOTSTRAP_ADMIN_USERNAME']
    SLP_BOOTSTRAP_ADMIN_PASSWORD = $localValues['SLP_BOOTSTRAP_ADMIN_PASSWORD']
    SLP_BOOTSTRAP_ADMIN_DISPLAY_NAME = '课程管理员'
}
foreach ($entry in $variables.GetEnumerator()) {
    if ($entry.Value -match '[\r\n]') { throw "Invalid newline in server configuration variable $($entry.Key)." }
}

$tempRoot = [System.IO.Path]::GetFullPath($env:TEMP).TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
$uploadFile = [System.IO.Path]::GetFullPath((Join-Path $env:TEMP "slp-env-upload-$([guid]::NewGuid().ToString('N')).tmp"))
if (-not $uploadFile.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Refusing to create a temporary credential file outside the system temp directory.'
}
$remoteUpload = "/opt/software-learning-platform/.env.server.upload.$([guid]::NewGuid().ToString('N'))"

try {
    $content = ($variables.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join "`n"
    [System.IO.File]::WriteAllText($uploadFile, $content + "`n", [System.Text.UTF8Encoding]::new($false))
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls $uploadFile /inheritance:r /grant:r "$($identity):(R,W)" 'NT AUTHORITY\SYSTEM:(F)' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not restrict the temporary credential file ACL.' }

    & ssh -o BatchMode=yes -o ConnectTimeout=10 $Server 'install -d -m 0700 /opt/software-learning-platform'
    if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the server deployment directory.' }
    & scp -q -o BatchMode=yes $uploadFile "${Server}:$remoteUpload"
    if ($LASTEXITCODE -ne 0) { throw 'Secure copy of the server environment file failed.' }
    & ssh -o BatchMode=yes $Server "chmod 0600 '$remoteUpload' && mv -n '$remoteUpload' '$remotePath' && chmod 0600 '$remotePath'"
    if ($LASTEXITCODE -ne 0) { throw 'Could not securely install the server environment file.' }

    $mode = (& ssh -o BatchMode=yes $Server "stat -c '%a' '$remotePath'" 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $mode -ne '600') { throw 'Server environment permissions are not 0600.' }
    Write-Output "Server environment installed with mode $mode at $remotePath. Secret values were not displayed."
} finally {
    if (Test-Path -LiteralPath $uploadFile) { Remove-Item -LiteralPath $uploadFile -Force }
}
