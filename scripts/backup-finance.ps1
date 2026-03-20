param(
    [string]$HostName = "localhost",
    [int]$Port = 5432,
    [string]$Database = "Finance",
    [string]$Username = "postgres",
    [string]$Password = "1234",
    [string]$BackupRoot = "D:\New folder (2)\backups"
)

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$targetDir = Join-Path $BackupRoot (Get-Date -Format "yyyy-MM-dd")
New-Item -ItemType Directory -Path $targetDir -Force | Out-Null

$backupFile = Join-Path $targetDir "$Database-$timestamp.sql"
$env:PGPASSWORD = $Password

try {
    & pg_dump -h $HostName -p $Port -U $Username -d $Database -f $backupFile
    if ($LASTEXITCODE -ne 0) {
        throw "pg_dump failed with exit code $LASTEXITCODE"
    }
    Write-Host "Backup created at $backupFile"
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}