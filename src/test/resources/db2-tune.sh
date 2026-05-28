#!/bin/bash
# Tune DB2 for test workloads: larger log buffer and less aggressive soft checkpoint.
# This script runs via /var/custom during container setup (before "Setup has completed.").
su - "${DB2INSTANCE}" -c "db2 UPDATE DATABASE CONFIGURATION FOR ${DBNAME} USING LOGBUFSZ 1024"
su - "${DB2INSTANCE}" -c "db2 UPDATE DATABASE CONFIGURATION FOR ${DBNAME} USING SOFTMAX 0"
