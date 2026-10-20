# data-reference

## Стек

Java 21, Spring Boot 4.0.7, PostgreSQL 16, Liquibase, RabbitMQ 4.1 (брокер 1С:УСК), Caffeine. Порт `8080` — REST и
пробы, `8081` — `/actuator/health`, `/actuator/prometheus`. Версии — [CHANGELOG.md](CHANGELOG.md).

## О сервисе

Справочники и полисы РВЛ ОНЛАЙН: принимает из 1С:УСК, хранит и отдаёт сервисам Заявок и Договоров по REST; в 1С
не пишет. Реплик — до трёх: REST — на всех, очереди 1С, разбор и расписания — на одном ведущем (`leader_lock`); кэш
сбрасывается на всех, часы — по NTP.

## Таймауты

| Что | Значение | Переменная |
|---|---|---|
| Чтение по REST | 3 с | `API_READ_TIMEOUT_SECONDS` |
| Ожидание соединения | 2 с | `DB_REST_CONNECTION_TIMEOUT_MS`, `DB_CONNECTION_TIMEOUT_MS` |
| Ответ зависшей базы: REST; приём и разбор | 5 с; 60 с | `DB_REST_SOCKET_TIMEOUT_SECONDS`; `DB_SOCKET_TIMEOUT_SECONDS` |
| Оператор; блокировка; простой транзакции | 30 с; 5 с; 1 мин | `DB_STATEMENT_TIMEOUT`; `DB_LOCK_TIMEOUT`; `DB_IDLE_IN_TRANSACTION_TIMEOUT` |
| Соединение с брокером | 10 с | — |
| Роль после гибели ведущего | 30 с | `LEADER_LOCK_TTL` |
| Ожидание родителя | 24 ч | `ONEC_PARENT_WAIT_TIMEOUT` |
| Повторы разбора при сбое | 30 с, 5 мин, 30 мин | — |
| Кэш справочников | 10 мин | `REFERENCE_CACHE_TTL` |
| Переподключение к сбросу кэша | 1–10 с | `REFERENCE_CACHE_RECONNECT_DELAY`, `REFERENCE_CACHE_RECONNECT_MAX_DELAY` |

## Без базы и без брокера

- Нет базы: кэш отдаётся; остальное и полисы — 503 через 2 с, у зависшей базы или занятого пула — до 5 с.
  Ведущего нет, сообщения ждут в очередях; приём вернётся сам, кэш сбросится целиком. Без базы сервис не стартует.
- Нет брокера: сервис стартует и отвечает, приём ждёт. Без очередей 1С — `RABBITMQ_LISTENER_AUTO_STARTUP=false`.
- `/livez`, `/readyz` от базы и брокера не зависят. Что лежит — в `/actuator/health`: `db`, `rabbit`, `onecIngest`
  (`role`: `leader`/`standby`). У ведущего `onec_leader` = 1; метрики сырья — только у него.

## Сообщения и REST

Из 1С — 11 очередей (`ONEC_QUEUE_*`), по одному потребителю на ведущем. Сообщение подтверждается после записи в
базу; без пригодного `message-id` — подтверждается и теряется. Разбор — по времени приёма по часам базы, действует
последнее. Не наше — `SKIPPED` с причиной: санаторий без РВЛ со всем своим, договор не ДМС, программа не
РВЛ, страхователь (сохраняется). Наружу — `GET /internal/v1/…`: ЛПУ, услуги и цены, МКБ-10, полисы, договоры страхования, программы.

## Диагностика по базе

Сырьё — `inbound_message`, непринятые базой тела — `quarantine`. Статусы: `NEW` → `PROCESSED`, `SKIPPED`; `BUFFERED` —
часть передачи прайса; `AWAITING_PARENT` — ждёт родителя; `RETRY` — временный сбой; `FAILED` — нужен человек, см.
`last_error`.

```sql
-- не разобрано
select stream, status, count(*), min(received_at) from inbound_message
 where status not in ('PROCESSED', 'SKIPPED') group by 1, 2 order by 1, 2;

-- отказы
select stream, last_error, count(*), max(received_at) from inbound_message
 where status = 'FAILED' group by 1, 2 order by 3 desc limit 20;

-- кого ждут
select stream, awaited_parent_kind, awaited_parent_id, count(*), min(parent_wait_since) from inbound_message
 where status = 'AWAITING_PARENT' group by 1, 2, 3 order by 5 limit 20;
```

## Подробнее

- [operations.md](docs/operations.md) — настройки, выкатка, метрики, сбои.
- [design.md](docs/design.md) — почему так устроено; [code-style.md](docs/code-style.md) — как пишем код.
- [load-testing.md](docs/load-testing.md) — результаты нагрузочного тестирования.
