param(
    [int]$AppPort = 18081,
    [int]$DatabasePort = 15432
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$projectName = 'task-management-check-' + [Guid]::NewGuid().ToString('N')
$keyBytes = New-Object byte[] 32
$keyRng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $keyRng.GetBytes($keyBytes) } finally { $keyRng.Dispose() }
$environmentOverrides = @{
    APP_PORT = [string]$AppPort
    POSTGRES_PORT = [string]$DatabasePort
    POSTGRES_DB = 'task_management_check'
    POSTGRES_USER = 'task_management_check'
    POSTGRES_PASSWORD = [Guid]::NewGuid().ToString('N')
    JWT_SIGNING_KEY_BASE64 = [Convert]::ToBase64String($keyBytes)
    COMPOSE_PROFILES = 'app'
    BOOTSTRAP_ADMIN_USERNAME = 'container_admin'
    BOOTSTRAP_ADMIN_EMAIL = 'container-admin@example.com'
    BOOTSTRAP_ADMIN_PASSWORD = [Guid]::NewGuid().ToString('N')
    BOOTSTRAP_ADMIN_FIRST_NAME = 'Container'
    BOOTSTRAP_ADMIN_LAST_NAME = 'Check'
}
$savedEnvironment = @{}
$logDirectory = Join-Path $projectRoot 'target/container-check'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null

function Invoke-Compose([string[]]$ComposeArguments) {
    # PowerShell 5 treats redirected native stderr as error records; check the exit code explicitly.
    $ErrorActionPreference = 'Continue'
    $composeOptions = @('compose', '--file', 'compose.yaml', '--env-file', '.env.sample',
        '--project-name', $projectName, '--profile', 'app')
    & docker @composeOptions @ComposeArguments 2>&1 | ForEach-Object { $_.ToString() }
    if ($LASTEXITCODE -ne 0) {
        throw ('Container command failed: ' + ($ComposeArguments -join ' '))
    }
}

function Assert-HttpStatus([string]$Path, [int]$Expected) {
    $actual = 0
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri ('http://127.0.0.1:' + $AppPort + $Path)
        $actual = [int]$response.StatusCode
    } catch {
        if (-not $_.Exception.Response) { throw }
        $actual = [int]$_.Exception.Response.StatusCode
    }
    if ($actual -ne $Expected) { throw ('Unexpected HTTP status for ' + $Path + ': ' + $actual) }
}

function Assert-MigrationHistory {
    $count = Invoke-Compose @('exec', '-T', 'postgres', 'psql', '-U', 'task_management_check',
        '-d', 'task_management_check', '-tAc', 'SELECT count(*) FROM public.databasechangelog;')
    if (($count | Out-String).Trim() -ne '3') { throw 'Liquibase tracking history was not preserved in public' }
}

function Assert-IdentityBootstrap {
    $bootstrapArguments = @('run', '--rm', '--no-deps', '-e', 'SPRING_PROFILES_ACTIVE=bootstrap',
        '-e', 'SPRING_MAIN_WEB_APPLICATION_TYPE=none', '-e', 'BOOTSTRAP_ADMIN_USERNAME',
        '-e', 'BOOTSTRAP_ADMIN_EMAIL', '-e', 'BOOTSTRAP_ADMIN_PASSWORD',
        '-e', 'BOOTSTRAP_ADMIN_FIRST_NAME', '-e', 'BOOTSTRAP_ADMIN_LAST_NAME', 'app')
    Invoke-Compose $bootstrapArguments *> (Join-Path $logDirectory 'bootstrap.log')
    $repeatLog = Join-Path $logDirectory 'bootstrap-repeat.log'
    $repeatFailed = $false
    try { Invoke-Compose $bootstrapArguments *> $repeatLog } catch { $repeatFailed = $true }
    if (-not $repeatFailed -or [IO.File]::ReadAllText($repeatLog) -notmatch 'ADMIN_ALREADY_EXISTS') {
        throw 'Repeated bootstrap must fail without modifying the existing ADMIN'
    }
    $loginBody = @{
        identifier = $environmentOverrides.BOOTSTRAP_ADMIN_EMAIL
        password = $environmentOverrides.BOOTSTRAP_ADMIN_PASSWORD
    } | ConvertTo-Json
    $tokens = Invoke-RestMethod -Method Post -Uri ('http://127.0.0.1:' + $AppPort + '/api/auth/login') `
        -ContentType 'application/json' -Body $loginBody
    $profile = Invoke-RestMethod -Uri ('http://127.0.0.1:' + $AppPort + '/api/users/me') `
        -Headers @{ Authorization = 'Bearer ' + $tokens.accessToken }
    if ($profile.role -ne 'ADMIN' -or $profile.username -ne 'container_admin') {
        throw 'Bootstrap account cannot authenticate with the expected role'
    }
    $missingKeyLog = Join-Path $logDirectory 'missing-key.log'
    $missingKeyFailed = $false
    try {
        Invoke-Compose @('run', '--rm', '--no-deps', '-e', 'JWT_SIGNING_KEY_BASE64=', 'app') *> $missingKeyLog
    } catch { $missingKeyFailed = $true }
    if (-not $missingKeyFailed -or [IO.File]::ReadAllText($missingKeyLog) -notmatch 'JWT signing key must') {
        throw 'Runtime without a signing key must fail closed'
    }
    Write-Output 'Identity command: bootstrap/login/me passed; duplicate bootstrap and missing signing key rejected.'
}

Push-Location $projectRoot
try {
    foreach ($key in $environmentOverrides.Keys) {
        $savedEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $environmentOverrides[$key], 'Process')
    }
    Write-Output 'Building the Java 21 application image with clean verify and quality gates...'
    $buildLog = Join-Path $logDirectory 'build.log'
    try {
        Invoke-Compose @('build', 'app') *> $buildLog
    } catch {
        Get-Content -LiteralPath $buildLog -Tail 40
        throw
    }
    Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', '120')
    Assert-HttpStatus '/actuator/health' 200
    Assert-HttpStatus '/api/users/me' 401
    $health = Invoke-RestMethod -Uri ('http://127.0.0.1:' + $AppPort + '/actuator/health')
    if ($health.status -ne 'UP') { throw 'Container health is not UP' }
    foreach ($path in @('/alpha/', '/alpha/app.js', '/alpha/plan', '/v3/api-docs',
        '/swagger-ui/index.html', '/webjars/swagger-ui/index.html')) {
        Assert-HttpStatus $path 404
    }
    $uid = Invoke-Compose @('exec', '-T', 'app', 'id', '-u')
    if (($uid | Out-String).Trim() -ne '10001') { throw 'Application container must run as UID 10001' }
    $timezone = Invoke-Compose @('exec', '-T', 'app', 'date', '+%Z')
    if (($timezone | Out-String).Trim() -ne 'UTC') { throw 'Container timezone must be UTC' }
    Invoke-Compose @('exec', '-T', 'app', 'sh', '-c',
        'test ! -e /app/.env && test ! -e /app/docs && test ! -e /build')
    Assert-MigrationHistory
    Assert-IdentityBootstrap
    Write-Output 'Container startup: health UP, identity protected, alpha disabled, UID 10001, UTC, migrations 3.'

    $restartTime = [DateTime]::UtcNow.ToString('o')
    Invoke-Compose @('restart', 'app')
    Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', '120', 'app')
    Assert-HttpStatus '/actuator/health' 200
    Assert-MigrationHistory
    $restartLog = Invoke-Compose @('logs', '--no-color', '--since', $restartTime, 'app') | Out-String
    $restartLog | Set-Content -LiteralPath (Join-Path $logDirectory 'restart.log') -Encoding UTF8
    if ($restartLog -notmatch 'Run:\s+0' -or $restartLog -notmatch 'Previously run:\s+3') {
        throw 'Restart reapplied migrations or did not preserve Liquibase history'
    }
    Write-Output 'Container restart: health UP, Liquibase 0 new / 3 existing migrations.'
} finally {
    try {
        # Only this run's randomly named, disposable Compose project may be removed.
        if ($projectName -notmatch '^task-management-check-[a-f0-9]{32}$') {
            throw 'Refusing cleanup of an unexpected Compose project'
        }
        Invoke-Compose @('down', '--volumes', '--remove-orphans')
    } finally {
        foreach ($key in $savedEnvironment.Keys) {
            [Environment]::SetEnvironmentVariable($key, $savedEnvironment[$key], 'Process')
        }
        Pop-Location
    }
}
