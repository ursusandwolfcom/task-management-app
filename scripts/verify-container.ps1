param(
    [int]$AppPort = 18081,
    [int]$DatabasePort = 15432,
    [string]$VerifiedRevision,
    [string]$VerifiedJarSha256
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$artifactStage = 'build'
if ($VerifiedRevision -or $VerifiedJarSha256) {
    if ($VerifiedRevision -notmatch '^[a-f0-9]{40}$' -or $VerifiedJarSha256 -notmatch '^[a-fA-F0-9]{64}$') {
        throw 'Verified artifact mode requires a full tested revision and SHA-256 hash.'
    }
    $currentRevision = git -C $projectRoot rev-parse HEAD
    if ($LASTEXITCODE -ne 0 -or $currentRevision -ne $VerifiedRevision) {
        throw 'Verified artifact revision does not match the current checkout.'
    }
    $pendingChanges = git -C $projectRoot status --porcelain
    if ($LASTEXITCODE -ne 0 -or $pendingChanges) {
        throw 'Verified artifact mode requires a clean source checkout.'
    }
    $verifiedJar = Join-Path $projectRoot 'target/task-management-app-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $verifiedJar) -or
            (Get-FileHash -LiteralPath $verifiedJar -Algorithm SHA256).Hash -ne $VerifiedJarSha256) {
        throw 'Verified artifact hash does not match the application JAR.'
    }
    $artifactStage = 'verified'
}
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
    ARTIFACT_STAGE = $artifactStage
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
    if (($count | Out-String).Trim() -ne '4') { throw 'Liquibase tracking history was not preserved in public' }
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

function Invoke-TaskSmokeApi([string]$Path, [string]$Method = 'Get', $Payload = $null) {
    $requestArguments = @{
        Uri = 'http://127.0.0.1:' + $AppPort + $Path
        Method = $Method
        Headers = $script:taskSmokeHeaders
    }
    if ($null -ne $Payload) {
        $requestArguments.ContentType = 'application/json'
        $requestArguments.Body = $Payload | ConvertTo-Json -Compress
    }
    Invoke-RestMethod @requestArguments
}

function Start-TaskPersistenceSmoke {
    $loginArguments = @{
        Method = 'Post'; Uri = 'http://127.0.0.1:' + $AppPort + '/api/auth/login'
        ContentType = 'application/json'
        Body = (@{
            identifier = $environmentOverrides.BOOTSTRAP_ADMIN_EMAIL
            password = $environmentOverrides.BOOTSTRAP_ADMIN_PASSWORD
        } | ConvertTo-Json)
    }
    $login = Invoke-RestMethod @loginArguments
    $script:taskSmokeHeaders = @{ Authorization = 'Bearer ' + $login.accessToken }
    $ownerProfile = Invoke-TaskSmokeApi '/api/users/me'
    $project = Invoke-TaskSmokeApi '/api/projects' 'Post' @{
        name = 'Container task persistence'; startDate = '2026-10-14'; endDate = '2027-10-14'; timezone = 'UTC'
    }
    $task = Invoke-TaskSmokeApi '/api/tasks' 'Post' @{
        projectId = $project.id; name = 'Persisted attempt'; priority = 'HIGH'
        estimatedMinutes = 525600; assigneeId = $ownerProfile.id
    }
    $task = Invoke-TaskSmokeApi ('/api/tasks/' + $task.id + '/start') 'Post' @{ version = $task.version }
    $task = Invoke-TaskSmokeApi ('/api/tasks/' + $task.id + '/fail') 'Post' @{ version = $task.version }
    $script:taskSmokeFirstAttempt = (Invoke-TaskSmokeApi ('/api/tasks/' + $task.id + '/attempts')).content[0]
    $task = Invoke-TaskSmokeApi ('/api/tasks/' + $task.id + '/retry') 'Post' @{ version = $task.version }
    $script:taskSmokeId = $task.id
    if ($task.status -ne 'NOT_STARTED' -or $task.attemptCount -ne 1 -or $task.estimatedMinutes -ne 525600) {
        throw 'Task retry lost execution facts or physical-year estimate.'
    }
    $successor = Invoke-TaskSmokeApi '/api/tasks' 'Post' @{
        projectId = $project.id; name = 'Persisted blocked successor'; priority = 'CRITICAL'
        estimatedMinutes = 15; assigneeId = $ownerProfile.id
    }
    $project = Invoke-TaskSmokeApi ('/api/projects/' + $project.id)
    $change = Invoke-TaskSmokeApi ('/api/tasks/' + $successor.id + '/dependencies') 'Post' @{
        dependsOnTaskId = $task.id; inputRevision = $project.inputRevision
    }
    $script:taskSmokeProjectId = $project.id
    $script:taskSmokeSuccessorId = $successor.id
    $script:taskSmokeGraphRevision = $change.inputRevision
    $available = Invoke-TaskSmokeApi ('/api/projects/' + $project.id + '/available-tasks')
    $order = Invoke-TaskSmokeApi ('/api/projects/' + $project.id + '/execution-order')
    if (@($available.tasks).Count -ne 1 -or $available.tasks[0].id -ne $task.id -or
            @($order.taskIds).Count -ne 2 -or $order.taskIds[0] -ne $task.id -or
            $order.taskIds[1] -ne $successor.id -or $available.inputRevision -ne $change.inputRevision) {
        throw 'Persisted dependency readiness/order/revision is inconsistent before restart.'
    }
    Write-Output 'Graph persistence: dependency blocks higher-priority successor without changing execution facts.'
    Write-Output 'Task persistence: create/assign/start/fail/retry passed before container restart.'
}

function Complete-TaskPersistenceSmoke {
    $available = Invoke-TaskSmokeApi ('/api/projects/' + $script:taskSmokeProjectId + '/available-tasks')
    if ($available.inputRevision -ne $script:taskSmokeGraphRevision -or
            @($available.blockingReasons).Count -ne 1 -or
            $available.blockingReasons[0].taskId -ne $script:taskSmokeSuccessorId -or
            $available.blockingReasons[0].predecessorIds[0] -ne $script:taskSmokeId) {
        throw 'Restart lost the persisted dependency or graph revision.'
    }
    $task = Invoke-TaskSmokeApi ('/api/tasks/' + $script:taskSmokeId)
    $task = Invoke-TaskSmokeApi ('/api/tasks/' + $task.id + '/start') 'Post' @{ version = $task.version }
    $task = Invoke-TaskSmokeApi ('/api/tasks/' + $task.id + '/complete') 'Post' @{ version = $task.version }
    $history = Invoke-TaskSmokeApi ('/api/tasks/' + $task.id + '/attempts')
    if ($task.status -ne 'COMPLETED' -or $task.attemptCount -ne 2 -or $history.totalElements -ne 2) {
        throw 'Restart or second execution lost persisted attempts.'
    }
    if ($history.content[0].outcome -ne 'COMPLETED' -or $history.content[1].outcome -ne 'FAILED' -or
        $history.content[1].actualStart -ne $script:taskSmokeFirstAttempt.actualStart -or
        $history.content[1].actualEnd -ne $script:taskSmokeFirstAttempt.actualEnd) {
        throw 'Restart or second execution changed the persisted failed attempt.'
    }
    $next = Invoke-TaskSmokeApi ('/api/projects/' + $script:taskSmokeProjectId + '/next-task')
    if ($next.task.id -ne $script:taskSmokeSuccessorId -or
            $next.inputRevision -ne ($script:taskSmokeGraphRevision + 2)) {
        throw 'Actual predecessor completion did not unlock the persisted successor after restart.'
    }
    $null = Invoke-TaskSmokeApi ('/api/tasks/' + $script:taskSmokeSuccessorId + '/dependencies/' +
            $script:taskSmokeId + '?inputRevision=' + $next.inputRevision) 'Delete'
    Write-Output 'Graph persistence: restart preserved dependency; actual completion unlocked next; removal passed.'
    Write-Output 'Task persistence: restart/start/complete preserved both execution attempts.'
}

Push-Location $projectRoot
try {
    foreach ($key in $environmentOverrides.Keys) {
        $savedEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $environmentOverrides[$key], 'Process')
    }
    if ($artifactStage -eq 'verified') {
        Write-Output ('Packaging the already verified JAR for revision ' + $VerifiedRevision + '...')
    } else {
        Write-Output 'Building the Java 21 application image with clean verify and quality gates...'
    }
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
    foreach ($path in @('/app/', '/app/assets/index.js', '/alpha/', '/alpha/app.js', '/alpha/plan', '/v3/api-docs',
        '/swagger-ui/index.html', '/webjars/swagger-ui/index.html')) {
        Assert-HttpStatus $path 404
    }
    $uid = Invoke-Compose @('exec', '-T', 'app', 'id', '-u')
    if (($uid | Out-String).Trim() -ne '10001') { throw 'Application container must run as UID 10001' }
    if ($artifactStage -eq 'verified') {
        $containerHash = Invoke-Compose @('exec', '-T', 'app', 'sha256sum', '/app/app.jar')
        $actualHash = (($containerHash | Out-String).Trim() -split '\s+')[0]
        if ($actualHash -ne $VerifiedJarSha256) { throw 'Running container JAR differs from the verified artifact.' }
        Write-Output 'Container artifact SHA-256 matches the already verified JAR.'
    }
    $timezone = Invoke-Compose @('exec', '-T', 'app', 'date', '+%Z')
    if (($timezone | Out-String).Trim() -ne 'UTC') { throw 'Container timezone must be UTC' }
    Invoke-Compose @('exec', '-T', 'app', 'sh', '-c',
        'test ! -e /app/.env && test ! -e /app/docs && test ! -e /build')
    Assert-MigrationHistory
    Assert-IdentityBootstrap
    Start-TaskPersistenceSmoke
    Write-Output 'Container startup: health UP, identity protected, alpha disabled, UID 10001, UTC, migrations 4.'

    $restartTime = [DateTime]::UtcNow.ToString('o')
    Invoke-Compose @('restart', 'app')
    Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', '120', 'app')
    Assert-HttpStatus '/actuator/health' 200
    Assert-MigrationHistory
    $restartLog = Invoke-Compose @('logs', '--no-color', '--since', $restartTime, 'app') | Out-String
    $restartLog | Set-Content -LiteralPath (Join-Path $logDirectory 'restart.log') -Encoding UTF8
    if ($restartLog -notmatch 'Run:\s+0' -or $restartLog -notmatch 'Previously run:\s+4') {
        throw 'Restart reapplied migrations or did not preserve Liquibase history'
    }
    Complete-TaskPersistenceSmoke
    Write-Output 'Container restart: health UP, Liquibase 0 new / 4 existing migrations.'
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
        $script:taskSmokeHeaders = $null
        Pop-Location
    }
}
