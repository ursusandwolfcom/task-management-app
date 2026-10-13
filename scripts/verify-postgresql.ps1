param([string]$LogPath)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$database = 'task_management_test_' + [Guid]::NewGuid().ToString('N')
if (-not $LogPath) { $LogPath = Join-Path $projectRoot '.session/verification/postgresql.log' }
$LogPath = [IO.Path]::GetFullPath($LogPath)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $LogPath) | Out-Null
$created = $false
Push-Location $projectRoot
try {
    $databaseOwner = (& docker compose exec -T postgres printenv POSTGRES_USER | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $databaseOwner) { throw 'Cannot identify the local PostgreSQL owner.' }
    & docker compose exec -T postgres createdb -U $databaseOwner -- $database
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create a disposable PostgreSQL test database.' }
    $created = $true
    $arguments = @('--batch-mode', '--no-transfer-progress',
        ('-Dspring.config.import=optional:file:' + ($projectRoot -replace '\\', '/') + '/.env[.properties]'),
        ('-Dspring.datasource.url=jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/' + $database),
        '-Dspring.datasource.username=${POSTGRES_USER:task_management}',
        '-Dspring.datasource.password=${POSTGRES_PASSWORD}', 'clean', 'verify')
    & .\mvnw.cmd @arguments *> $LogPath
    if ($LASTEXITCODE -ne 0) { throw ('PostgreSQL verification failed; inspect ' + $LogPath) }
    Write-Output 'PostgreSQL: BUILD SUCCESS in a disposable database.'
    Select-String -LiteralPath $LogPath -Pattern '\[INFO\] Tests run:|Checkstyle violations' |
        ForEach-Object { $_.Line }
} finally {
    try {
        if ($created) {
            if ($database -notmatch '^task_management_test_[a-f0-9]{32}$') {
                throw 'Refusing cleanup of an unexpected database.'
            }
            & docker compose exec -T postgres dropdb -U $databaseOwner -- $database
            if ($LASTEXITCODE -ne 0) { throw 'Could not remove the disposable PostgreSQL test database.' }
        }
    } finally { Pop-Location }
}
