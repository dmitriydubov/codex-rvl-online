# data-reference

## Стек

Java 21, Spring Boot 4.0.7, PostgreSQL 16, Liquibase, RabbitMQ 4.1 (брокер 1С:УСК), Caffeine. Порт `8080` — REST и
пробы `/livez`, `/readyz`; `8081` — `/actuator/health`, `/actuator/prometheus`. Версии — [CHANGELOG.md](CHANGELOG.md).

## О сервисе

Справочники и полисы РВЛ ОНЛАЙН: принимает их из 1С:УСК через RabbitMQ, хранит в PostgreSQL и отдаёт сервисам Заявок и
Договоров по REST, только на чтение. В 1С ничего не пишет.

## Таймауты

| Что | Значение | Переменная |
|---|---|---|
| Запрос к базе при чтении по REST | 3 с | `API_READ_TIMEOUT_SECONDS` |
| Ожидание соединения с базой | 2 с | `DB_REST_CONNECTION_TIMEOUT_MS`, `DB_CONNECTION_TIMEOUT_MS` |
| Ответ зависшей базы: REST; приём и разбор | 5 с; 60 с | `DB_REST_SOCKET_TIMEOUT_SECONDS`; `DB_SOCKET_TIMEOUT_SECONDS` |
| Оператор; блокировка | 30 с; 5 с | `DB_STATEMENT_TIMEOUT`; `DB_LOCK_TIMEOUT` |
| Соединение с брокером | 10 с | — |
| Ожидание родителя сообщением | 24 ч | `ONEC_PARENT_WAIT_TIMEOUT` |
| Повторы разбора при сбое | 30 с, 5 мин, 30 мин | — |
| Кэш справочников | 10 мин | `REFERENCE_CACHE_TTL` |

## Без базы и без брокера

- База недоступна: REST отвечает 503 `SERVICE_UNAVAILABLE` примерно через 5 с; приём из 1С встаёт на паузу, сообщения ждут
  в очередях, и приём сам возобновляется, когда база снова принимает записи. Без базы сервис не стартует: миграции идут
  при запуске.
- Брокер недоступен или слушатели выключены (`RABBITMQ_LISTENER_AUTO_STARTUP=false`): REST отдаёт то, что уже в базе.
- Пробы `/livez`, `/readyz` от базы и брокера не зависят. Что лежит, видно в `/actuator/health`: `db` и `onecIngest`.

## Сообщения и REST

Из 1С — 11 очередей (`ONEC_QUEUE_*`), у каждой один потребитель. Сообщение подтверждается после записи в базу,
разбирается по времени приёма, действует последнее. Не наше получает `SKIPPED` с причиной: санаторий без вида
деятельности РВЛ с его договорами, адресом, услугами и прайсами, договор страхования не ДМС, программа не РВЛ;
страхователь сохраняется, но тоже `SKIPPED`. Ребёнок, пришедший раньше родителя, ждёт его до 24 ч.
Сам сервис сообщений не отправляет. Наружу — только `GET /internal/v1/…`: ЛПУ, услуги и цены, МКБ-10, полисы, договоры
страхования, программы.

## Диагностика по базе

Сырьё — `inbound_message`, тела, которые база не приняла, — `quarantine`. Статусы: `NEW` → `PROCESSED`; `SKIPPED` — не
наше; `BUFFERED` — часть передачи прайса; `AWAITING_PARENT` — ждёт родителя; `RETRY` — временный сбой; `FAILED` — нужен
человек, причина в `last_error`.

```sql
-- что не разобрано, по потокам
select stream, status, count(*), min(received_at)
  from inbound_message
 where status in ('NEW', 'RETRY', 'BUFFERED', 'AWAITING_PARENT', 'FAILED')
 group by stream, status order by stream, status;

-- причины отказов за сутки
select stream, last_error, count(*)
  from inbound_message
 where status = 'FAILED' and received_at > now() - interval '1 day'
 group by stream, last_error order by 3 desc;

-- кого ждут сообщения
select stream, awaited_parent_kind, awaited_parent_id, count(*), min(parent_wait_since)
  from inbound_message
 where status = 'AWAITING_PARENT'
 group by 1, 2, 3 order by 5 limit 20;
```

## Подробнее

- [docs/operations.md](docs/operations.md) — переменные и настройки, метрики, сбои и ручные операции, ручки REST,
  очереди, сборка и стенд.
- [docs/design.md](docs/design.md) — почему так устроено.
