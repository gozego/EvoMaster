#!/bin/bash

mvn clean install -DskipTests

source .env

echo $LOCKBOX_SWAGGER_URL

token="bearer $LOCKBOX_TOKEN"

echo $token

# shellcheck disable=SC2140
java -jar core/target/evomaster.jar  --blackBox true --bbSwaggerUrl "$LOCKBOX_SWAGGER_URL"  --outputFolder "src/lockbox" --header0 "Authorization: $token" --outputFormat JAVA_JUNIT_4 --maxTime 30s --ratePerMinute 60
