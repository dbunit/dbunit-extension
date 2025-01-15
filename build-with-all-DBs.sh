#!/bin/sh

log() {
    local build=$1
    local action=$2
    echo "========> $build $action"
}

LOG_FILE_DIR="build-logs"

mkdir $LOG_FILE_DIR
echo "Writing logs to $LOG_FILE_DIR"

log "compile and UTs" "starting"
./mvnw clean install 2>&1 | tee $LOG_FILE_DIR/compile-and-UTs.log
log "compile and UTs" "done"
sleep 4


while IFS= read -r profile
do
    log $profile "starting"
    ./mvnw verify -P$profile 2>&1 | tee $LOG_FILE_DIR/$profile.log
    exitCode=$?
    log $profile "done"

    if [ $exitCode -ne 0 ] ; then
        log $profile "Build error with profile=$profile, exitCode=$exitCode"
    fi

    sleep 4
done < database-profiles.txt
