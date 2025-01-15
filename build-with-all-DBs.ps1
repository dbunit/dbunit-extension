function log {
    param(
        [string]$build,
        [string]$action
    )
    Write-Output "========> $build $action"
}

$logFileDir="build-logs"

New-Item -Path $logFileDir -ItemType "directory" -Force
Write-Output "Writing logs to $logFileDir"

log "compile and UTs" "starting"
./mvnw clean install *> $logFileDir\compile-and-UTs.log
log "compile and UTs" "done"
timeout /T 4

$profiles = Get-Content -Path ".\database-profiles.txt"
Foreach ($profile in $profiles) {
    log $profile "starting"
    ./mvnw verify "-P$profile" *> $logFileDir\$profile.log
    $exitCode=$LastExitCode
    log $profile, "done"

    if ($exitCode -ne 0) {
        log $profile "Build error with profile=$profile, exitCode=$exitCode"
    }

    timeout /T 4
}
