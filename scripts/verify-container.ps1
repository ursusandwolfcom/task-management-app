param(
    [int]$AppPort = 18081,
    [int]$DatabasePort = 15432
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$projectName = 'task-management-check-' + [Guid]::NewGuid().ToString('N')
$environmentOverrides = @{
    APP_PORT = [string]$AppPort
    POSTGRES_PORT = [string]$DatabasePort
    POSTGRES_DB = 'task_management_check'
    POSTGRES_USER = 'task_management_check'
    POSTGRES_PASSWORD = [Guid]::NewGuid().ToString('N')
    COMPOSE_PROFILES = 'app'
}
$savedEnvironment = @{}
$logDirectory = Join-Path $projectRoot 'target/container-check'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null

function Invoke-Compose([string[]]$ComposeArguments) {
    # PowerShell 5 treats redirected native stderr as error records; check the exit code explicitly.
    $ErrorActionPreference = 'Continue'
    $composeOptions = @('compose', '--file', 'compose.yaml', '--env-file', '.env.sample',
        '--project-name', $projectName, '--profile', 'app')
    & docker @composeOptions @ComposeArguments
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
    if (($count | Out-String).Trim() -ne '1') { throw 'Liquibase tracking history was not preserved in public' }
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
    Write-Output 'Container startup: health UP, alpha disabled, UID 10001, UTC, public migration count 1.'

    $restartTime = [DateTime]::UtcNow.ToString('o')
    Invoke-Compose @('restart', 'app')
    Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', '120', 'app')
    Assert-HttpStatus '/actuator/health' 200
    Assert-MigrationHistory
    $restartLog = Invoke-Compose @('logs', '--no-color', '--since', $restartTime, 'app') | Out-String
    $restartLog | Set-Content -LiteralPath (Join-Path $logDirectory 'restart.log') -Encoding UTF8
    if ($restartLog -notmatch 'Run:\s+0' -or $restartLog -notmatch 'Previously run:\s+1') {
        throw 'Restart reapplied migrations or did not preserve Liquibase history'
    }
    Write-Output 'Container restart: health UP, Liquibase 0 new / 1 existing migration.'
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
