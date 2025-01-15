function log {
    param(
        [string]$build,
        [string]$action
    )
    Write-Output "========> $build $action"
}

$LOG_FILE_DIR="build-logs"

New-Item -Path $LOG_FILE_DIR -ItemType "directory" -Force
Write-Output "Writing logs to $LOG_FILE_DIR"

log "compile and UTs" "starting"
./mvnw clean install *> $LOG_FILE_DIR\compile-and-UTs.log
log "compile and UTs" "done"
timeout /T 4

$profiles = Get-Content -Path ".\database-profiles.txt"
Foreach ($profile in $profiles) {
    log $profile "starting"
    ./mvnw verify "-P$profile" *> $LOG_FILE_DIR\$profile.log
    log $profile, "done"
    timeout /T 4

    if ($LASTEXITCODE -ne 0) {
        Write-Output "Build may have failed for $profile"
        break
    }
}
