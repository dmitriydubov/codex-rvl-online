# data-reference

## Стек

Java 21, Spring Boot 4.0.7, PostgreSQL 16, Liquibase, RabbitMQ 4.1 (брокер 1С:УСК), Caffeine. Порт `8080` — REST и
пробы `/livez`, `/readyz`; `8081` — `/actuator/health`, `/actuator/prometheus`. История версий — [CHANGELOG.md](CHANGELOG.md).

## О сервисе

Справочники и полисы РВЛ ОНЛАЙН: принимает их из 1С:УСК, хранит и отдаёт сервисам Заявок и Договоров по REST, только
на чтение. В 1С ничего не пишет. Реплика — одна: со второй разбор сообщений 1С пойдёт дважды и не по порядку.

## Таймауты

| Что | Значение | Переменная |
|---|---|---|
| Чтение по REST | 3 с | `API_READ_TIMEOUT_SECONDS` |
| Ожидание соединения с базой | 2 с | `DB_REST_CONNECTION_TIMEOUT_MS`, `DB_CONNECTION_TIMEOUT_MS` |
| Ответ зависшей базы: REST; приём и разбор | 5 с; 60 с | `DB_REST_SOCKET_TIMEOUT_SECONDS`; `DB_SOCKET_TIMEOUT_SECONDS` |
| Оператор; блокировка; простой транзакции | 30 с; 5 с; 1 мин | `DB_STATEMENT_TIMEOUT`; `DB_LOCK_TIMEOUT`; `DB_IDLE_IN_TRANSACTION_TIMEOUT` |
| Соединение с брокером | 10 с | — |
| Ожидание родителя | 24 ч | `ONEC_PARENT_WAIT_TIMEOUT` |
| Повторы разбора при сбое | 30 с, 5 мин, 30 мин | — |
| Кэш справочников | 10 мин | `REFERENCE_CACHE_TTL` |

## Без базы и без брокера

- Нет базы: что есть в кэше, отдаётся; остальное и полисы — 503 через 2 с, у зависшей базы или занятого пула — до 5 с.
  Приём из 1С на паузе, сообщения ждут в очередях; вернётся сам. Без базы сервис не стартует: миграции идут при запуске.
- Нет брокера: сервис стартует, REST отвечает, приём ждёт брокера. Без очередей 1С — `RABBITMQ_LISTENER_AUTO_STARTUP=false`.
- `/livez`, `/readyz` от базы и брокера не зависят. Что лежит, видно в `/actuator/health`: `db`, `rabbit`, `onecIngest`.

## Сообщения и REST

Из 1С — 11 очередей (`ONEC_QUEUE_*`), у каждой один потребитель. Сообщение подтверждается после записи в базу; без
пригодного `message-id` — подтверждается и теряется. Разбор — по времени приёма, действует последнее. Не наше получает
`SKIPPED` с причиной: санаторий без РВЛ и его договоры, адрес, услуги, прайсы, договор страхования не ДМС, программа
не РВЛ; страхователь сохраняется, но тоже `SKIPPED`. Ребёнок ждёт родителя. Сервис сообщений не отправляет. Наружу —
`GET /internal/v1/…`: ЛПУ, услуги и цены, МКБ-10, полисы, договоры страхования, программы.

## Диагностика по базе

Сырьё — `inbound_message`, тела, которые база не приняла, — `quarantine`. Статусы: `NEW` → `PROCESSED`; `SKIPPED` —
пропущено; `BUFFERED` — часть передачи прайса; `AWAITING_PARENT` — ждёт родителя; `RETRY` — временный сбой; `FAILED` —
нужен человек, см. `last_error`.

```sql
-- не разобрано, по потокам
select stream, status, count(*), min(received_at)
  from inbound_message
 where status in ('NEW', 'RETRY', 'BUFFERED', 'AWAITING_PARENT', 'FAILED')
 group by stream, status order by stream, status;

-- причины отказов
select stream, last_error, count(*), max(received_at)
  from inbound_message
 where status = 'FAILED'
 group by stream, last_error order by 3 desc limit 20;

-- кого ждут сообщения
select stream, awaited_parent_kind, awaited_parent_id, count(*), min(parent_wait_since)
  from inbound_message
 where status = 'AWAITING_PARENT'
 group by 1, 2, 3 order by 5 limit 20;
```

## Подробнее

- [docs/operations.md](docs/operations.md) — настройки, выкатка, метрики, сбои, ручные операции, REST, очереди,
  сборка, стенд.
- [docs/design.md](docs/design.md) — почему так устроено.
- [docs/load-testing.md](docs/load-testing.md) — результаты нагрузочного тестирования.
