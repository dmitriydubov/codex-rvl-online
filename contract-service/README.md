# contract-service — сервис Договоров РВЛ ОНЛАЙН

## Стек

Java 21, Spring Boot 4.0.7, Hibernate 7.2, Liquibase 5.0, PostgreSQL 16, RabbitMQ 4.1. Порты: `8080` — API и пробы
`/livez`, `/readyz`; `8081` — актуатор (`health`, `info`, `prometheus`).

## О сервисе

По задаче сервиса Заявок решает, что выпускается каждому участнику поездки: договор КМ на сумму доплаты (сразу готов к
акцепту) или полис ЛМ родственнику без доплаты (его выпускает 1С:УСК). Сервису Заявок отвечает состоянием выпуска по
заявке, справочники читает у сервиса справочных данных.

## Таймауты

| Что | Значение | Переменная |
|---|---|---|
| ожидание соединения пула базы | 2 с | `DB_CONNECTION_TIMEOUT_MS` |
| блокировка, запрос, простой транзакции | 5 с, 30 с, 1 мин | `DB_LOCK_TIMEOUT`, `DB_STATEMENT_TIMEOUT`, `DB_IDLE_IN_TRANSACTION_TIMEOUT` |
| чтение сокета базы | 60 с | `DB_SOCKET_TIMEOUT_SECONDS` |
| справочник: соединение, ответ | 2 с, 7 с | `REFERENCE_CONNECT_TIMEOUT`, `REFERENCE_READ_TIMEOUT` |
| подтверждение брокера | 10 с | `OUTBOX_CONFIRM_TIMEOUT` |
| соединение с брокером | 10 с | нет |

## Если сосед недоступен

- Справочный сервис: чтение повторяется (`REFERENCE_MAX_RETRIES`), потом работа ждёт следующего прохода, растёт
  `contracts_reference_unavailable_total`.
- Брокер: сервис стартует, в `/actuator/health` — DOWN у `internalBroker` или `onecBroker`, ответы ждут в журнале
  исходящих.
- База: сообщение возвращается в очередь через `INBOX_REQUEUE_DELAY`, разбор повторяется через 1, 5, 15 мин.
- Без очередей соседей: `RABBITMQ_INTERNAL_LISTENER_AUTO_STARTUP=false`, `RABBITMQ_ONEC_LISTENER_AUTO_STARTUP=false`.

## Сообщения

| Очередь | Куда | Переменная |
|---|---|---|
| `rvl_requests_to_rvl_contracts_task-changes` | из сервиса Заявок | `INTERNAL_QUEUE_TASK_CHANGES` |
| `rvl_contracts_to_rvl_requests_status-changes` | в сервис Заявок | `INTERNAL_QUEUE_STATUS_CHANGES` |
| `rvl_contracts_to_1c-usk_lm-policy-changes` | в 1С:УСК | `ONEC_QUEUE_LM_POLICY_REQUESTS` |
| `1c-usk_to_rvl_contracts_lm-policy-changes` | из 1С:УСК | `ONEC_QUEUE_LM_POLICY_ISSUES` |

Очереди договора КМ в 1С:УСК уже есть в настройках, обмена по ним пока нет. Синхронных вызовов пока нет.

## Диагностика по базе

Журналы `inbox_messages` (`NEW`, `PROCESSED`, `SKIPPED`, `FAILED`) и `outbox_messages` (`NEW`, `PUBLISHED`,
`FAILED`); записи `contracts` и `lm_policies`. Запросы только читают и персональных данных не выбирают.

```sql
select event_type, attempts, error_message, received_at from inbox_messages
 where status = 'FAILED' order by received_at desc limit 20;

select event_type, attempts, next_attempt_at, created_at from outbox_messages
 where status = 'NEW' order by created_at limit 20;

select number, status::text, error_code from contracts where individual_request_id = '<GUID заявки>'
union all
select issued_policy_number, status::text, error_code from lm_policies where individual_request_id = '<GUID заявки>';
```

## Документация

- [docs/operations.md](docs/operations.md) — сборка, запуск, все настройки, метрики и оповещения.
- [docs/design.md](docs/design.md) — почему так устроено.
- [CHANGELOG.md](CHANGELOG.md) — версии и что нужно для установки каждой.
