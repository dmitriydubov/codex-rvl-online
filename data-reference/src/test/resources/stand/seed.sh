#!/bin/sh
# Одноразовая закладка сырья стенда (docker-compose.yml, профиль stand).
#
# Схему создаёт сам сервис — Liquibase на старте, — поэтому сначала ждём схему, а не
# healthcheck образа сервиса: есть ли в образе JRE curl или wget, не проверялось.
# Схема готова, когда есть таблица inbound_message и Liquibase снял свою блокировку
# (databasechangeloglock.locked = false): таблица появляется посреди обновления, и
# закладка, начатая раньше конца, могла бы столкнуться с миграцией этой же таблицы.
# Пока таблицы блокировки нет, запрос падает и печатает пусто — это тоже «ждать».
# Подключение — переменными libpq (PGHOST, PGUSER, ...) из docker-compose.yml.
set -eu

attempts=90
pause=2

schema_ready="select 'ready' where to_regclass('public.inbound_message') is not null
                 and not exists (select 1 from databasechangeloglock where locked)"

attempt=0
until [ -n "$(psql -tAq -c "$schema_ready" 2>/dev/null)" ]; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge "$attempts" ]; then
        # Последняя попытка — с выводом ошибок psql: причиной может быть подключение, а не схема.
        psql -tAq -c "$schema_ready" >&2 || true
        echo "стенд: схема не готова после $((attempts * pause)) с — сервис не накатил её до конца (журнал: docker compose logs data-reference-stand)" >&2
        exit 1
    fi
    sleep "$pause"
done

psql -v ON_ERROR_STOP=1 -q -f /stand/seed.sql
echo "стенд: сырьё 1С заложено, разбор — планировщиком сервиса"
