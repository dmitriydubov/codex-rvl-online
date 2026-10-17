# contract-service — сервис Договоров РВЛ ОНЛАЙН

## Стек

Java 21, Spring Boot 4.0.7, Hibernate 7.2, Liquibase 5.0, PostgreSQL 16, RabbitMQ 4.1. Порты: `8080` — пробы
`/livez`, `/readyz`; `8081` — актуатор.

## О сервисе

По задаче сервиса Заявок решает, что выпускается участнику поездки: договор КМ на сумму доплаты (сразу готов к акцепту)
или полис ЛМ родственнику без доплаты (выпускает 1С:УСК). Отвечает сервису Заявок состоянием выпуска по заявке.

## Таймауты

| Что | Значение | Переменная |
|---|---|---|
| соединение пула базы: ожидание, проверка | 2 с, 1 с | `DB_CONNECTION_TIMEOUT_MS`, нет |
| блокировка, запрос, простой транзакции | 5 с, 30 с, 1 мин | `DB_LOCK_TIMEOUT`, `DB_STATEMENT_TIMEOUT`, `DB_IDLE_IN_TRANSACTION_TIMEOUT` |
| чтение сокета базы | 60 с | `DB_SOCKET_TIMEOUT_SECONDS` |
| справочник: соединение, ответ | 2 с, 7 с | `REFERENCE_CONNECT_TIMEOUT`, `REFERENCE_READ_TIMEOUT` |
| брокер: соединение, подтверждение | 10 с, 10 с | нет, `OUTBOX_CONFIRM_TIMEOUT` |
| остановка | 30 с | нет |

## Если сосед недоступен

- Справочный сервис: чтение повторяется (`REFERENCE_MAX_RETRIES`), растёт `contracts_reference_unavailable_total`.
  Запрос полиса ждёт следующего прохода, результат 1С:УСК — лестницы `INBOX_RETRY_BACKOFF` (1, 5, 15 мин), потом FAILED.
- База: приём возвращает сообщение в очередь через `INBOX_REQUEUE_DELAY`, разбор ждёт; временный отказ при разборе —
  по лестнице, потом FAILED.
- Брокер: сервис стартует, в `/actuator/health` — DOWN у `internalBroker` или `onecBroker`, исходящие ждут в журнале.
- 1С:УСК молчит: полис остаётся SUBMITTED без срока, его видно третьим запросом ниже.
- Без очередей соседей: `RABBITMQ_INTERNAL_LISTENER_AUTO_STARTUP=false`, `RABBITMQ_ONEC_LISTENER_AUTO_STARTUP=false`.

## Сообщения

| Очередь | Направление | Переменная |
|---|---|---|
| `rvl_requests_to_rvl_contracts_task-changes` | из сервиса Заявок | `INTERNAL_QUEUE_TASK_CHANGES` |
| `rvl_contracts_to_rvl_requests_status-changes` | в сервис Заявок | `INTERNAL_QUEUE_STATUS_CHANGES` |
| `rvl_contracts_to_1c-usk_lm-policy-changes` | в 1С:УСК | `ONEC_QUEUE_LM_POLICY_REQUESTS` |
| `1c-usk_to_rvl_contracts_lm-policy-changes` | из 1С:УСК | `ONEC_QUEUE_LM_POLICY_ISSUES` |

Очереди договора КМ в 1С:УСК есть в настройках, обмена по ним нет. Справочники — REST GET на `REFERENCE_URL`.

## Диагностика по базе

`inbox_messages`: NEW (`attempts` > 0 — ждёт повтора), PROCESSED, SKIPPED, FAILED; `outbox_messages`: NEW, PUBLISHED,
FAILED. `contracts`: NEW, DRAFT_READY … ISSUED, ERROR, CANCELLED; `lm_policies`: NEW, SUBMITTED, ISSUED, ERROR,
CANCELLED. Запросы только читают, без персональных данных.

```sql
select message_id, correlation_id, event_type, attempts, error_message, received_at from inbox_messages
 where status = 'FAILED' order by received_at desc limit 20;

select message_id, event_type, attempts, error_message, created_at from outbox_messages
 where status = 'NEW' order by created_at limit 20;

select 'KM', insured_participant_id, number, status::text, error_code from contracts
 where individual_request_id = '<GUID заявки>'
union all
select 'LM', participant_id, issued_policy_number, status::text, error_code from lm_policies
 where individual_request_id = '<GUID заявки>';
```

## Документация

- [docs/operations.md](docs/operations.md) — сборка, запуск, настройки, метрики, оповещения.
- [docs/design.md](docs/design.md) — почему так устроено.
- [CHANGELOG.md](CHANGELOG.md) — версии.
