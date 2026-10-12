#!/bin/sh
set -eu
dir="$1"
for cert in "$dir"/*.crt "$dir"/*.cer "$dir"/*.pem; do
    if [ -f "$cert" ]; then
        keytool -importcert -noprompt -cacerts -storepass changeit \
            -alias "$(basename "$cert")" -file "$cert"
    fi
done
