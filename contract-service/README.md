# contract-service — сервис Договоров

## Стек

Java 21, Spring Boot 4.0.7, Liquibase 5.0, PostgreSQL 16, RabbitMQ 4.1, S3 (MinIO). `8080` — REST `/internal/v1`,
`/livez`, `/readyz`; `8081` — актуатор.

## О сервисе

По задаче Заявок выпускает через 1С:УСК договор КМ на доплату или полис ЛМ родственнику без доплаты,
принимает справки 070/у и акцепт оферты, создаёт и отменяет заказ на оплату в Ordering, отвечает Заявкам.
Фоновую работу ведёт ведущий (`leader_lock`).

## Таймауты

| Что | Значение | Переменная |
|---|---|---|
| база: соединение, сокет | 2 с, 60 с | `DB_CONNECTION_TIMEOUT_MS`, `DB_SOCKET_TIMEOUT_SECONDS` |
| блокировка, запрос, простой | 5 с, 30 с, 1 мин | `DB_LOCK_TIMEOUT`, `DB_STATEMENT_TIMEOUT`, `DB_IDLE_IN_TRANSACTION_TIMEOUT` |
| справочник: соединение, ответ | 2 с, 7 с | `REFERENCE_CONNECT_TIMEOUT`, `REFERENCE_READ_TIMEOUT` |
| Ordering: соединение, ответ | 2 с, 10 с | `ORDERING_CONNECT_TIMEOUT`, `ORDERING_READ_TIMEOUT` |
| сервер токенов: соединение, ответ | 2 с, 5 с | `ORDERING_TOKEN_CONNECT_TIMEOUT`, `ORDERING_TOKEN_READ_TIMEOUT` |
| S3: соединение, вызов | 2 с, 20 с | `S3_CONNECT_TIMEOUT`, `S3_CALL_TIMEOUT` |
| брокер: подтверждение | 10 с | `OUTBOX_CONFIRM_TIMEOUT` |
| смена ведущего | 30 с | `LEADER_LOCK_TTL` |
| остановка | 30 с | нет |

## Если сосед недоступен

- Справочник: повторы `REFERENCE_MAX_RETRIES`, задача ждёт; ответ 1С:УСК — лестница `INBOX_RETRY_BACKOFF`, затем
  FAILED.
- База: приём на паузе (`INBOX_STORAGE_CHECK_INTERVAL`), разбор — по лестнице.
- Брокер: DOWN у `internalBroker`/`onecBroker`/`marketingBroker` — ждут все исходящие; нет очереди 1С:УСК — ERROR
  `…_UNDELIVERED`.
- Ordering, сервер токенов: заказ ждёт, растёт `contracts_ordering_failures_total`, договор не меняется.
- S3: справка — 503 `STORAGE_UNAVAILABLE`, передача КМ ждёт.
- 1С:УСК молчит: запись в SUBMITTED; дольше `…_AWAITING_THRESHOLD` — `contracts_*_awaiting_overdue`.
- Без соседей: `RABBITMQ_*_LISTENER_AUTO_STARTUP` и обязательная `PAYMENT_ENABLED` = `false`. В этой версии оплату не
  включают: счёта нет, а договор, акцептованный при `false`, заказа не получит и после включения.

## Сообщения

Очереди: `rvl_requests_to_rvl_contracts_task-changes`, `rvl_contracts_to_rvl_requests_status-changes`,
`rvl_contracts_to_1c-usk_*`, `1c-usk_to_rvl_contracts_*` (`*` — `lm-policy-changes`, `km-contract-changes`). Брокер
маркетинга — только подтверждение документа в `MARKETING_DOCUMENT_FIXES_EXCHANGE`.

REST от Заявок: справка — `POST /requests/{заявка}/participants/{участник}/attachments`, пакет —
`GET /requests/{заявка}/package`, акцепт — `POST /contracts/{договор}/acceptance`.

## Диагностика по базе

`inbox_messages`: NEW, PROCESSED, SKIPPED, FAILED; `outbox_messages`: NEW, PUBLISHED, FAILED; `contracts`,
`lm_policies`: NEW … ISSUED, ERROR, CANCELLED; `payment_orders`: AWAITING_CREATION, CREATED, REJECTED,
CANCEL_PENDING, CANCELLED, CANCEL_REJECTED.

```sql
select message_id, event_type, error_message from inbox_messages where status = 'FAILED';
select message_id, source_queue, reason from inbox_quarantine;
select 'KM', insured_participant_id, status::text, error_code from contracts where individual_request_id = '<заявка>'
union all
select 'LM', participant_id, status::text, error_code from lm_policies where individual_request_id = '<заявка>';
```

## Документация

[Эксплуатация](docs/operations.md), [неполадки](docs/troubleshooting.md), [почему так](docs/design.md),
[как пишем код](docs/code-style.md), [версии](CHANGELOG.md).
