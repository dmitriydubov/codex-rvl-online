# contract-service — сервис Договоров РВЛ ОНЛАЙН

## Стек

Java 21, Spring Boot 4.0.7, Hibernate 7.2, Liquibase 5.0, PostgreSQL 16, RabbitMQ 4.1, S3 (MinIO). Порты:
`8080` — REST `/internal/v1`, пробы `/livez`, `/readyz`; `8081` — актуатор.

## О сервисе

По задаче сервиса Заявок выпускает участнику договор КМ на доплату или полис ЛМ родственнику без доплаты через
1С:УСК, принимает акцепт оферты и справки 070/у (S3), отвечает Заявкам. Экземпляров может быть несколько;
очереди и фоновые проходы ведёт один — ведущий.

## Таймауты

| Что | Значение | Переменная |
|---|---|---|
| база: соединение, сокет | 2 с, 60 с | `DB_CONNECTION_TIMEOUT_MS`, `DB_SOCKET_TIMEOUT_SECONDS` |
| блокировка, запрос, простой | 5 с, 30 с, 1 мин | `DB_LOCK_TIMEOUT`, `DB_STATEMENT_TIMEOUT`, `DB_IDLE_IN_TRANSACTION_TIMEOUT` |
| справочник: соединение, ответ | 2 с, 7 с | `REFERENCE_CONNECT_TIMEOUT`, `REFERENCE_READ_TIMEOUT` |
| S3: соединение, вызов | 2 с, 20 с | `S3_CONNECT_TIMEOUT`, `S3_CALL_TIMEOUT` |
| брокер: подтверждение | 10 с | `OUTBOX_CONFIRM_TIMEOUT` |
| переход роли ведущего | 30 с | `LEADER_LOCK_TTL` |
| остановка | 30 с | нет |

## Если сосед недоступен

- Справочник: повторы `REFERENCE_MAX_RETRIES`, задача ждёт; ответ 1С:УСК — лестница `INBOX_RETRY_BACKOFF`, затем
  FAILED.
- База: приём на паузе до ответа базы (`INBOX_STORAGE_CHECK_INTERVAL`), сообщения ждут в очередях; разбор — по
  лестнице; занятая запись — повтор, акцепт — 503.
- Брокер: health DOWN у `internalBroker`/`onecBroker`, исходящие ждут; нет очереди 1С:УСК — ERROR `…_UNDELIVERED`.
- S3: справка — 503 `STORAGE_UNAVAILABLE`, передача КМ ждёт.
- 1С:УСК молчит: запись в SUBMITTED; дольше `…_AWAITING_THRESHOLD` — `contracts_*_awaiting_overdue`.
- Без соседей: `RABBITMQ_*_LISTENER_AUTO_STARTUP` = `false`.

## Сообщения

| Очередь | Куда | Переменная |
|---|---|---|
| `rvl_requests_to_rvl_contracts_task-changes` | из Заявок | `INTERNAL_QUEUE_TASK_CHANGES` |
| `rvl_contracts_to_rvl_requests_status-changes` | в Заявки | `INTERNAL_QUEUE_STATUS_CHANGES` |
| `rvl_contracts_to_1c-usk_lm-policy-changes` | в 1С:УСК | `ONEC_QUEUE_LM_POLICY_REQUESTS` |
| `1c-usk_to_rvl_contracts_lm-policy-changes` | из 1С:УСК | `ONEC_QUEUE_LM_POLICY_ISSUES` |
| `rvl_contracts_to_1c-usk_km-contract-changes` | в 1С:УСК | `ONEC_QUEUE_KM_CONTRACT_SUBMISSIONS` |
| `1c-usk_to_rvl_contracts_km-contract-changes` | из 1С:УСК | `ONEC_QUEUE_KM_CONTRACT_REGISTRATIONS` |

REST от Заявок (`/internal/v1`): справка — `POST /requests/{заявка}/participants/{участник}/attachments`, пакет —
`GET /requests/{заявка}/package`, акцепт — `POST /contracts/{договор}/acceptance`. Справочники — `REFERENCE_URL`.

## Диагностика по базе

`inbox_messages`: NEW, PROCESSED, SKIPPED, FAILED; `inbox_quarantine` — карантин; `outbox_messages`: NEW, PUBLISHED,
FAILED; `contracts`, `lm_policies`: NEW … ISSUED, ERROR, CANCELLED; `leader_lock` — ведущий.

```sql
select message_id, event_type, attempts, error_message from inbox_messages where status = 'FAILED' limit 20;
select message_id, source_queue, reason, received_at from inbox_quarantine order by received_at desc limit 20;
select 'KM', insured_participant_id, status::text, error_code from contracts where individual_request_id = '<заявка>'
union all
select 'LM', participant_id, status::text, error_code from lm_policies where individual_request_id = '<заявка>';
```

## Документация

[Эксплуатация](docs/operations.md), [почему так](docs/design.md), [как пишем код](docs/code-style.md),
[версии](CHANGELOG.md).
