# data-reference

Сервис справочных данных РВЛ ОНЛАЙН. Принимает из 1С:УСК справочники и полисы через RabbitMQ, хранит их в
PostgreSQL и отдаёт сервисам Заявок и Договоров по REST `/internal/v1`. В 1С ничего не пишет.

## Стек

Java 21, Spring Boot 4.0.7, PostgreSQL 16, Liquibase, RabbitMQ 4.1 (брокер 1С:УСК), Caffeine. Порт `8080` — REST и
пробы `/livez`, `/readyz`; `8081` — `/actuator/health`, `/actuator/prometheus`. Версии — [CHANGELOG.md](CHANGELOG.md).

## Таймауты

| Что | Значение | Переменная |
|---|---|---|
| Чтение по REST целиком | 3 с | `API_READ_TIMEOUT_SECONDS` |
| Ожидание соединения с базой | 2 с | `DB_REST_CONNECTION_TIMEOUT_MS`, `DB_CONNECTION_TIMEOUT_MS` |
| Запрос к базе; блокировка | 30 с; 5 с | `DB_STATEMENT_TIMEOUT`; `DB_LOCK_TIMEOUT` |
| Ожидание родителя сообщением | 24 ч | `ONEC_PARENT_WAIT_TIMEOUT` |
| Повторы при сбое | 30 с, 5 мин, 30 мин | — |
| Тревога о зависшей передаче прайса | 1 ч | — |
| Кэш справочников | 10 мин | `REFERENCE_CACHE_TTL` |
| ПДн полиса после окончания или архива | 30 сут | `POLICY_PERSONAL_DATA_GRACE` |

## Приём из 1С

11 очередей (`ONEC_QUEUE_*`), у каждой один потребитель; сообщения разбираются по времени приёма, действует
последнее. Полисы, МКБ-10 и виды обращения сохраняются все, из остального — только «наше»: санатории с видом
деятельности РВЛ, их договоры, адреса, услуги и прайсы, договоры ДМС, программы РВЛ; прочее — `SKIPPED`. Ребёнок, пришедший раньше родителя (договор раньше санатория, прайс раньше
услуги), ждёт его до 24 ч.

## Статусы сообщений

Сырьё — таблица `inbound_message`. `NEW` → `PROCESSED`; `SKIPPED` — не наше; `BUFFERED` — часть передачи прайса;
`AWAITING_PARENT` — ждёт родителя; `RETRY` — временный сбой; `FAILED` — нужен человек, причина в `last_error`.
Ручная переобработка — [docs/design.md](docs/design.md), «Разбор сырья, повторы и ручная переобработка».

## Диагностика по БД

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

- [docs/operations.md](docs/operations.md) — ручки REST, очереди, сборка, образ, локальный контур и стенд.
- [docs/design.md](docs/design.md) — почему так устроено.
- `src/main/resources/application.yml`, `.env.example` — все настройки с умолчаниями.
