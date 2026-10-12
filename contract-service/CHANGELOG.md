# История изменений contract-service

Здесь записаны заметные изменения сервиса по версиям, новые сверху. Формат — [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/),
номера версий — по [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html):

- **MAJOR** — ломается контракт синхронных вызовов `/internal/v1`, контракт очередей с сервисом Заявок или 1С:УСК или
  эксплуатация: удалена или переименована переменная окружения, появилась обязательная переменная без умолчания,
  миграция базы без обратной совместимости.
- **MINOR** — новая возможность без поломки, в том числе новая переменная с умолчанием и миграция, которая только добавляет.
- **PATCH** — исправление без новых возможностей.

Рубрики версии: Добавлено, Изменено, Устарело, Удалено, Исправлено, Безопасность и Перенос — что нужно сделать на контуре
при установке версии.

## [Не выпущено]

### Добавлено

- Каркас сервиса: приложение на Spring Boot 4.0.7 и Java 21, подключение к PostgreSQL с миграциями Liquibase (схема
  пока пустая), журнал в JSON (ECS), метрики Prometheus и health на порту управления, пробы `/livez` и `/readyz` на
  порту API, версия сервиса в `/actuator/info`.
- Образ (`Dockerfile`) и локальный контур с базой и двумя брокерами (`docker-compose.yml`).
- Подключения к двум брокерам RabbitMQ — межсервисному брокеру контура (сервис Заявок) и брокеру 1С:УСК: у каждого
  свои настройки (`projectx.brokers.internal`, `projectx.brokers.onec`), подключение с подтверждениями издателя и
  возвратами немаршрутизируемых сообщений, шаблон публикации (`mandatory`), фабрика контейнеров слушателей (ручное подтверждение, один потребитель, одно сообщение в работе,
  отказ запуска без очереди) и компонент здоровья `internalBroker` / `onecBroker` в `/actuator/health`. Недоступный
  брокер не роняет старт. Слушателей очередей и публикаций пока нет; очереди сервис не объявляет.
- Метрики подключений к брокерам `rabbitmq_*` с меткой `name` (`internalBroker`, `onecBroker`).

### Безопасность

- У паролей базы и брокеров нет умолчания: без `DB_PASSWORD`, `RABBITMQ_INTERNAL_PASSWORD` и `RABBITMQ_ONEC_PASSWORD`
  сервис не стартует, а не подключается с известным паролем.
- TLS к брокерам по умолчанию проверяет сертификат и имя хоста; выключается только явно, переменными.
- Health подключений не показывает учётные данные брокеров.
- Текст ошибки базы не несёт значений строки (`logServerErrorDetail=false` у пула и в умолчании `DB_URL`).
- Порт управления отделён от порта API.

### Перенос

- **Новый сервис:** своя база PostgreSQL `contract_service` с пользователем `contract_service`; миграции сервис
  применяет сам при старте.
- **Брокеры:** учётные записи сервиса на межсервисном брокере контура и на брокере 1С:УСК с правами на очереди из
  `application.yml`; сами очереди заводят сервис Заявок и 1С:УСК. Свойства `spring.rabbitmq.*` сервис не читает.
- **Переменные окружения.** Обязательные, без умолчания: `DB_PASSWORD`, `RABBITMQ_INTERNAL_PASSWORD`,
  `RABBITMQ_ONEC_PASSWORD`. Остальные необязательны:

  | Переменная | Умолчание |
  |---|---|
  | `DB_URL` | `jdbc:postgresql://localhost:5432/contract_service?logServerErrorDetail=false` |
  | `DB_USERNAME` | `contract_service` |
  | `DB_CONNECTION_TIMEOUT_MS` | `2000` |
  | `DB_SOCKET_TIMEOUT_SECONDS` | `60` |
  | `DB_LOCK_TIMEOUT` | `5s` |
  | `DB_STATEMENT_TIMEOUT` | `30s` |
  | `DB_IDLE_IN_TRANSACTION_TIMEOUT` | `1min` |
  | `MANAGEMENT_PORT` | `8081` |
  | `RABBITMQ_INTERNAL_HOST` / `RABBITMQ_ONEC_HOST` | `localhost` |
  | `RABBITMQ_INTERNAL_PORT` / `RABBITMQ_ONEC_PORT` | `5672` / `5673` |
  | `RABBITMQ_INTERNAL_VHOST` / `RABBITMQ_ONEC_VHOST` | `/` |
  | `RABBITMQ_INTERNAL_USERNAME` / `RABBITMQ_ONEC_USERNAME` | `contract_service` |
  | `RABBITMQ_INTERNAL_SSL_ENABLED` / `RABBITMQ_ONEC_SSL_ENABLED` | `false` |
  | `RABBITMQ_INTERNAL_SSL_VALIDATE_CERTIFICATE` / `RABBITMQ_ONEC_SSL_VALIDATE_CERTIFICATE` | `true` |
  | `RABBITMQ_INTERNAL_SSL_VERIFY_HOSTNAME` / `RABBITMQ_ONEC_SSL_VERIFY_HOSTNAME` | `true` |
  | `RABBITMQ_INTERNAL_LISTENER_AUTO_STARTUP` / `RABBITMQ_ONEC_LISTENER_AUTO_STARTUP` | `true` |
  | `INTERNAL_QUEUE_TASK_CHANGES` | `rvl_requests_to_rvl_contracts_task-changes` |
  | `INTERNAL_QUEUE_STATUS_CHANGES` | `rvl_contracts_to_rvl_requests_status-changes` |
  | `ONEC_QUEUE_KM_CONTRACT_SUBMISSIONS` | `rvl_contracts_to_1c-usk_km-contract-changes` |
  | `ONEC_QUEUE_KM_CONTRACT_REGISTRATIONS` | `1c-usk_to_rvl_contracts_km-contract-changes` |
  | `ONEC_QUEUE_LM_POLICY_REQUESTS` | `rvl_contracts_to_1c-usk_lm-policy-changes` |
  | `ONEC_QUEUE_LM_POLICY_ISSUES` | `1c-usk_to_rvl_contracts_lm-policy-changes` |
