#!/bin/sh
# run the IT setup for the specified profile

PROFILE=$1

if [ -z "$PROFILE" ]; then
   echo "Error: must specify Maven database profile"
   cat database-profiles.txt
fi


echo "-------------------------------------"
echo "Running IT setup for $profile"
echo "-------------------------------------"

./mvnw pre-integration-test -P"$PROFILE"
