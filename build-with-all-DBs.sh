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
    log $profile "done"
    sleep 4

    if [[ $? -ne 0 ]]; then
        echo "Build may have failed for $profile"
        break
    fi
done < database-profiles.txt
