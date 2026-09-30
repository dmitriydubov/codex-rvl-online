#!/bin/sh
# Одноразовая закладка сырья стенда (docker-compose.yml, профиль stand).
#
# Схему создаёт сам сервис — Liquibase на старте, — поэтому сначала ждём таблицу
# inbound_message, а не healthcheck образа сервиса: есть ли в образе JRE curl или wget,
# не проверялось, а таблица — ровно то условие, которое закладке нужно.
# Подключение — переменными libpq (PGHOST, PGUSER, ...) из docker-compose.yml.
set -eu

attempts=90
pause=2

attempt=0
until [ -n "$(psql -tAq -c "select to_regclass('public.inbound_message')")" ]; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge "$attempts" ]; then
        echo "стенд: таблицы inbound_message нет после $((attempts * pause)) с — сервис не накатил схему" >&2
        exit 1
    fi
    sleep "$pause"
done

psql -v ON_ERROR_STOP=1 -q -f /stand/seed.sql
echo "стенд: сырьё 1С заложено, разбор — планировщиком сервиса"
