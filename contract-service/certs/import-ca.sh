#!/bin/sh
# Добавляет сертификаты из каталога в хранилище доверенных центров JVM (cacerts): Nexus контура и его
# сервисы подписаны корпоративным центром, которого нет в базовых образах. Пустой каталог ничего не меняет,
# а сертификат, который keytool не разобрал, роняет сборку.
set -eu
dir="$1"
for cert in "$dir"/*.crt "$dir"/*.cer "$dir"/*.pem; do
    if [ -f "$cert" ]; then
        keytool -importcert -noprompt -cacerts -storepass changeit \
            -alias "$(basename "$cert")" -file "$cert"
    fi
done
