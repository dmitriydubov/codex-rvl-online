# contract-service — сервис Договоров РВЛ ОНЛАЙН

## Стек

Java 21, Spring Boot 4.0.7, Hibernate 7.2, Liquibase 5.0, PostgreSQL 16, RabbitMQ 4.1, S3 (MinIO). Порты:
`8080` — REST `/internal/v1`, пробы `/livez`, `/readyz`; `8081` — актуатор.

## О сервисе

По задаче сервиса Заявок выпускает участнику поездки договор КМ на доплату или полис ЛМ родственнику без доплаты
(через 1С:УСК), отвечает Заявкам о выпуске, хранит в S3 справки 070/у.

## Таймауты

| Что | Значение | Переменная |
|---|---|---|
| база: соединение, сокет | 2 с, 60 с | `DB_CONNECTION_TIMEOUT_MS`, `DB_SOCKET_TIMEOUT_SECONDS` |
| блокировка, запрос, простой | 5 с, 30 с, 1 мин | `DB_LOCK_TIMEOUT`, `DB_STATEMENT_TIMEOUT`, `DB_IDLE_IN_TRANSACTION_TIMEOUT` |
| справочник: соединение, ответ | 2 с, 7 с | `REFERENCE_CONNECT_TIMEOUT`, `REFERENCE_READ_TIMEOUT` |
| S3: соединение, обмен, вызов | 2 с, 10 с, 20 с | `S3_CONNECT_TIMEOUT`, `S3_READ_TIMEOUT`/`S3_WRITE_TIMEOUT`, `S3_CALL_TIMEOUT` |
| брокер: соединение, подтверждение | 10 с, 10 с | нет, `OUTBOX_CONFIRM_TIMEOUT` |
| остановка | 30 с | нет |

## Если сосед недоступен

- Справочник: повторы `REFERENCE_MAX_RETRIES`, `contracts_reference_unavailable_total`; запрос полиса ждёт прохода,
  ответ 1С:УСК — лестница `INBOX_RETRY_BACKOFF` (1, 5, 15 мин), потом FAILED и возврат вручную (`docs/operations.md`).
- База: приём возвращает сообщение в очередь через `INBOX_REQUEUE_DELAY`, разбор — по лестнице.
- Брокер: старт идёт, health — DOWN у `internalBroker` или `onecBroker`, исходящие ждут; нет очереди 1С:УСК — полис ЛМ
  в ERROR `LM_POLICY_REQUEST_UNDELIVERED`.
- S3: справка — 503 `STORAGE_UNAVAILABLE`, Заявки повторяют; `contracts_storage_unavailable_total`.
- 1С:УСК молчит: полис в SUBMITTED; дольше `LM_POLICY_REQUEST_AWAITING_THRESHOLD` (1 ч) —
  `contracts_lm_policies_awaiting_overdue`.
- Без соседей: `RABBITMQ_INTERNAL_LISTENER_AUTO_STARTUP`, `RABBITMQ_ONEC_LISTENER_AUTO_STARTUP` = `false`.

## Сообщения

| Очередь | Направление | Переменная |
|---|---|---|
| `rvl_requests_to_rvl_contracts_task-changes` | из Заявок | `INTERNAL_QUEUE_TASK_CHANGES` |
| `rvl_contracts_to_rvl_requests_status-changes` | в Заявки | `INTERNAL_QUEUE_STATUS_CHANGES` |
| `rvl_contracts_to_1c-usk_lm-policy-changes` | в 1С:УСК | `ONEC_QUEUE_LM_POLICY_REQUESTS` |
| `1c-usk_to_rvl_contracts_lm-policy-changes` | из 1С:УСК | `ONEC_QUEUE_LM_POLICY_ISSUES` |

Справки 070/у от Заявок — `POST /internal/v1/requests/{заявка}/participants/{участник}/attachments`; справочники —
GET на `REFERENCE_URL`.

## Диагностика по базе

`inbox_messages`: NEW (`attempts` > 0 — повтор), PROCESSED, SKIPPED, FAILED; `outbox_messages`: NEW, PUBLISHED,
FAILED; `contracts`: NEW … ISSUED, ERROR, CANCELLED; `lm_policies`: NEW, SUBMITTED, ISSUED, ERROR, CANCELLED;
`contract_documents`: формы 1С и справки. Запросы только читают.

```sql
select message_id, event_type, attempts, error_message from inbox_messages
 where status = 'FAILED' order by received_at desc limit 20;

select message_id, event_type, attempts, error_message from outbox_messages
 where status = 'NEW' order by created_at limit 20;

select 'KM', insured_participant_id, status::text, error_code from contracts where individual_request_id = '<заявка>'
union all
select 'LM', participant_id, status::text, error_code from lm_policies where individual_request_id = '<заявка>';
```

## Документация

[Эксплуатация](docs/operations.md), [почему так устроено](docs/design.md), [как пишем код](docs/code-style.md),
[версии](CHANGELOG.md).
