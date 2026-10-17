# data-reference: эксплуатация

## О сервисе

**РВЛ ОНЛАЙН** — контур сервисов, через которые застрахованные по ДМС получают восстановительное лечение в
санаториях (ЛПУ): сервис Заявок ведёт заявки на лечение и их согласование, сервис Договоров — договоры, полисы
и личный кабинет застрахованного. Справочники и полисы для них приходят из учётной системы страховщика 1С:УСК.
Аббревиатура «РВЛ» означает ещё и вид деятельности «восстановительное лечение» в 1С:УСК: по нему санатории
отделяются от прочих контрагентов.

**data-reference** принимает из 1С:УСК справочники и полисы через RabbitMQ, хранит их в PostgreSQL и отдаёт
сервисам Заявок и Договоров по REST. Сам он в 1С ничего не пишет и наружу, за пределы контура, не публикуется.

Версия — в `gradle.properties` (`version=`), история изменений — в [CHANGELOG.md](../CHANGELOG.md).

## Стек

Java 21, Spring Boot 4.0.7 (Spring Framework 7.0.8, Spring AMQP 4.0.4, Spring Data JPA 4.0.6), Hibernate ORM 7.2.19,
Liquibase 5.0.3, PostgreSQL 16 (драйвер 42.7.11), RabbitMQ 4.1 (клиент 5.27.1), Jackson 3.1.4, Caffeine 3.2.4,
Micrometer 1.16.6 с реестром Prometheus, встроенный Tomcat 11.0.22, сборка Gradle 8.14.3. Версии библиотек задаёт
BOM Spring Boot.

## REST `/internal/v1`

Только `GET`, ответы в JSON с полями в snake_case. Ручки доступны только сервисам Заявок и Договоров: своей
авторизации у них нет, защищает сетевая политика.

| Метод | Путь | Что отдаёт |
|---|---|---|
| GET | `/internal/v1/lpu?contract_external_id=` | каталог ЛПУ, доступных застрахованным договора страхования |
| GET | `/internal/v1/lpu/{id}` | карточку ЛПУ с прайсовым договором |
| GET | `/internal/v1/lpu/{id}/services?need_prices=` | услуги прайсового договора по видам обращения РВЛ, с ценами или без |
| GET | `/internal/v1/services/{id}/price?date_from=&date_to=` | стоимость услуги за период проживания |
| GET | `/internal/v1/mkb10?query=&limit=` или `?codes=` | подсказку по МКБ-10 или перечень кодов разом |
| GET | `/internal/v1/policies/search?contract_external_id=&query=&limit=` | подсказку по части ФИО или номера среди действующих полисов договора |
| GET | `/internal/v1/policies/{id}`, `/internal/v1/policies?external_id=` или `?number=&contract_external_id=` | полис целиком, с персональными данными |
| GET | `/internal/v1/policies/{id}/contract-view`, `/internal/v1/policies/contract-view?external_id=` | полис для сервиса Договоров, без персональных данных |
| GET | `/internal/v1/insurance-contracts/{id}`, `/internal/v1/insurance-contracts?external_id=` | договор страхования со страхователем |
| GET | `/internal/v1/programs/{id}` | страховую программу с перечнем услуг |

Подробное описание полей и ошибок — в документе «ТЗ API Data-reference». Примеры запросов к стенду с
синтетическими данными — [src/test/resources/stand/README.md](../src/test/resources/stand/README.md).

## Очереди 1С:УСК

11 очередей, 10 потоков данных: прейскуранты приходят двумя очередями. Очереди заводит 1С:УСК, сервис их не создаёт.
Умолчания имён — имена тестового брокера; на контуре их сверяют со списком брокера.

| Переменная | Что приходит |
|---|---|
| `ONEC_QUEUE_POLICIES` | полисы застрахованных с программами и условиями обслуживания (персональные данные) |
| `ONEC_QUEUE_INSURANCE_CONTRACTS` | договоры страхования; сохраняются только ДМС |
| `ONEC_QUEUE_CONTRAGENTS` | контрагенты-юрлица: ЛПУ и страхователи |
| `ONEC_QUEUE_MKB10` | справочник МКБ-10 |
| `ONEC_QUEUE_LPU_CONTRACTS` | договоры с ЛПУ |
| `ONEC_QUEUE_PROGRAMS` | страховые программы и учреждения, доступные по ним |
| `ONEC_QUEUE_APPEAL_TYPES` | виды обращения за медицинской помощью |
| `ONEC_QUEUE_LPU_CONTACTS` | адрес и регион ЛПУ |
| `ONEC_QUEUE_PRICE_LISTS` | прейскуранты ЛПУ, пакетами строк |
| `ONEC_QUEUE_PRICE_LISTS_FINAL` | финальное сообщение передачи прейскуранта со счётчиками пакетов |
| `ONEC_QUEUE_CONTRAGENT_SERVICES` | услуги контрагентов по договорам |

## Порты

- `8080` — REST `/internal/v1` и пробы `/livez`, `/readyz`.
- `8081` (`MANAGEMENT_PORT`) — `/actuator/health`, `/actuator/info` (версия сервиса), `/actuator/prometheus`.

## Переменные окружения

Обязательны только пароли. Остальные умолчания ведут на локальный контур из `docker-compose.yml` или заданы под рабочий
режим. Таймауты PostgreSQL пишутся с единицей (`ms`, `s`, `min`): число без неё PostgreSQL читает как миллисекунды.

**База**

| Переменная | Умолчание | Что задаёт |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/data_reference?logServerErrorDetail=false` | адрес базы для пула и миграций; параметры драйвера (`sslmode` и подобные) — только здесь; `logServerErrorDetail=false` сохранять |
| `DB_USERNAME` | `data_reference` | пользователь базы; нужны права DDL на схему и CREATE на базу для расширения `pg_trgm` |
| `DB_PASSWORD` | нет, обязательна | пароль базы |
| `DB_CONNECTION_TIMEOUT_MS` | `2000` | ожидание соединения в пуле приёма и разбора, мс |
| `DB_SOCKET_TIMEOUT_SECONDS` | `60` | сетевой тайм-аут драйвера в пуле приёма и разбора, с; больше `DB_STATEMENT_TIMEOUT` |
| `DB_LOCK_TIMEOUT` | `5s` | ожидание одной блокировки |
| `DB_STATEMENT_TIMEOUT` | `30s` | один оператор |
| `DB_IDLE_IN_TRANSACTION_TIMEOUT` | `1min` | открытая транзакция без запросов |
| `DB_REST_POOL_SIZE` | `5` | соединений в пуле REST-чтений; у пула приёма и разбора их 10 |
| `DB_REST_CONNECTION_TIMEOUT_MS` | `2000` | ожидание соединения в пуле REST-чтений, мс, не меньше 250 |
| `DB_REST_SOCKET_TIMEOUT_SECONDS` | `5` | сетевой тайм-аут драйвера в пуле REST-чтений, с; не меньше лимита чтения вместе с ожиданием отмены |
| `DB_REST_CANCEL_SIGNAL_TIMEOUT_SECONDS` | `2` | сколько драйвер ждёт отправки отмены запроса в пуле REST-чтений, с |
| `API_READ_TIMEOUT_SECONDS` | `3` | лимит REST-чтения на каждый оператор, с; меньше `DB_LOCK_TIMEOUT` |

**Брокер 1С:УСК**

| Переменная | Умолчание | Что задаёт |
|---|---|---|
| `RABBITMQ_HOST` | `localhost` | хост брокера; с TLS он должен быть в сертификате брокера |
| `RABBITMQ_PORT` | `5672` | порт: 5672 без TLS, 5671 с TLS |
| `RABBITMQ_VHOST` | `/` | vhost |
| `RABBITMQ_USERNAME` | `data_reference` | логин брокера; на брокере 1С:УСК — `rvl-online` |
| `RABBITMQ_PASSWORD` | нет, обязательна | пароль брокера |
| `RABBITMQ_SSL_ENABLED` | `false` | TLS к брокеру |
| `RABBITMQ_SSL_VALIDATE_CERTIFICATE` | `true` | проверка сертификата брокера; на тестовом контуре с самоподписанным сертификатом — `false` |
| `RABBITMQ_SSL_VERIFY_HOSTNAME` | `true` | проверка имени хоста в сертификате; без проверки сертификата не действует |
| `RABBITMQ_LISTENER_AUTO_STARTUP` | `true` | слушатели очередей 1С; `false` — там, где очередей 1С нет |
| `ONEC_QUEUE_*` | имена тестового брокера | имена 11 очередей — раздел «Очереди 1С:УСК» |

**Сроки, кэш и прочее**

| Переменная | Умолчание | Что задаёт |
|---|---|---|
| `ONEC_PARENT_WAIT_TIMEOUT` | `24h` | сколько сообщение ждёт родителя; меньше срока хранения тел (30 суток), иначе сервис не стартует |
| `POLICY_PERSONAL_DATA_GRACE` | `30d` | через сколько после окончания полиса или перевода в архив стираются ФИО, дата рождения и почта; не меньше суток |
| `REFERENCE_CACHE_TTL` | `10m` | срок жизни ответа в кэше |
| `REFERENCE_CACHE_MAXIMUM_SIZE` | `1000` | предел ответов в каждом кэше |
| `MANAGEMENT_PORT` | `8081` | порт актуатора |
| `PROJECTX_ONEC_CONTRACT_DMSPRODUCTTYPE` | `ЛичноеСтрахование,Личное страхование` | типы продукта ДМС, значения через запятую; заменяет список целиком |
| `PROJECTX_ONEC_CONTRACT_INSURERROLE` | `Страхователь` | роль страхователя в договоре страхования; пустое значение — сервис не стартует |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | `ecs` | формат журнала; пустое значение — обычный текст для локальной работы |

Переменные `docker-compose.yml` и аргументы сборки образа — в разделе «Сборка и локальный запуск» и в `.env.example`.

## Прочие настройки

У этих настроек нет своей переменной. Любую настройку Spring Boot можно задать переменной окружения: точки заменяются на
`_`, дефисы убираются, буквы прописные (`projectx.import.poll-interval` → `PROJECTX_IMPORT_POLLINTERVAL`), список —
значениями через запятую.

| Настройка | Умолчание | Что задаёт |
|---|---|---|
| `projectx.inbound.retry.backoff` | `30s`, `5m`, `30m` | паузы повторов разбора после временного отказа; после последней — `FAILED` |
| `projectx.import.initial-delay` | `30s` | первый проход разбора после старта |
| `projectx.import.poll-interval` | `10s` | пауза между проходами разбора |
| `projectx.import.batch-size` | `100` | сообщений за проход разбора |
| `projectx.import.payload-ttl` | `30d` | срок хранения тел полисов, договоров страхования и пропущенных сообщений |
| `projectx.import.quarantine-ttl` | `30d` | срок хранения тел в карантине |
| `projectx.import.retention-poll-interval` | `1h` | пауза между проходами стирания по сроку |
| `projectx.import.retention-batch-size` | `500` | строк за пачку стирания |
| `projectx.import.stuck-transfer-threshold` | `1h` | с какого возраста передача прайса считается зависшей |
| `projectx.import.stuck-transfer-poll-interval` | `15m` | пауза между проверками зависших передач |
| `projectx.import.parent-wait-report-limit`, `stuck-transfer-report-limit` | `20` | сколько GUID называет одна запись журнала |
| `projectx.onec.ingest.storage-check-interval` | `10s` | проверка базы, пока приём на паузе |
| `projectx.onec.ingest.duplicate-delivery-backoff` | `5s` | пауза перед возвратом в очередь двух столкнувшихся доставок одного сообщения |
| `projectx.onec.contract.rvl-activity-id` | `f3876c53-c7d8-4620-b364-9538164ba5d4` | GUID вида деятельности РВЛ в 1С:УСК; неверный GUID оставит справочник санаториев пустым |
| `projectx.onec.contract.rvl-contract-property` | не задано | имя признака договора на РВЛ; пока не задано, договоры ЛПУ этим признаком не отбираются |
| `projectx.metrics.backlog-refresh-interval` | `30s` | обновление снимка метрик разбора |
| `projectx.api.zone` | `Europe/Moscow` | по какому дню считается «действует на сегодня» |
| `projectx.api.rvl-appeal-types` | `РВЛ и СКЛ`, `РВЛ в России` | виды обращения, по которым отбираются позиции размещения |
| `projectx.api.search.*` | `default-limit` 10, `max-limit` 50, длина запроса 2–100 | поиск полисов |
| `projectx.api.mkb10.*` | `default-limit` 20, `max-limit` 100, длина запроса 2–100, `max-codes` 100 | подсказка и перечень МКБ-10 |
| `projectx.api.price.max-nights` | `366` | самый длинный период расчёта стоимости, ночей |
| `spring.rabbitmq.connection-timeout` | `10s` | соединение с брокером |
| `spring.lifecycle.timeout-per-shutdown-phase` | `30s` | сколько плавная остановка ждёт начатые запросы |
| `spring.task.scheduling.pool.size` | `5` | потоков у задач по расписанию |

Значения полей 1С:УСК, одинаковые на всех контурах (вид записи адреса и порядок видов адреса, имя свойства с почтой
застрахованного), заданы умолчаниями в коде: `OneCContractProperties`, `PolicyContractProperties`.

## Метрики и когда тревожиться

Метрики — `/actuator/prometheus` на порту актуатора. Пороги ниже — предложение, на контуре их настраивают.

| Метрика | Что показывает | Когда тревожиться |
|---|---|---|
| `onec_ingest_deliveries_total{stream,queue,outcome}` | доставки из очередей по исходу: `stored`, `duplicate`, `quarantined`, `unidentified`, `returned`, `storage_outage` | любой рост `unidentified` (сообщение без пригодного `message-id` потеряно) или `quarantined` (тело в карантине) |
| `onec_ingest_paused` | 1 — приём на паузе: база не принимает записи | держится 1: база недоступна |
| `onec_ingest_pauses_total` | сколько раз приём вставал на паузу | частый рост: база работает с перебоями |
| `onec_inbound_messages{stream,status}` | строки сырья в `NEW`, `RETRY`, `BUFFERED`, `AWAITING_PARENT`, `FAILED` | рост `FAILED` |
| `onec_inbound_pending_oldest_age_seconds{stream}` | сколько ждёт разбора самое давнее сообщение потока (`NEW` или `RETRY`) | растёт и не падает: разбор потока стоит |
| `onec_inbound_transitions_total{stream,status}` | переходы статусов при разборе | — |
| `onec_inbound_parent_wait_expired_total{stream}` | сообщения, отклонённые потому, что родитель не пришёл за срок | `increase(onec_inbound_parent_wait_expired_total[1h]) > 0` |
| `onec_price_transfers_stuck` | передачи прайса, ждущие своих частей дольше часа | больше 0 |
| `onec_inbound_backlog_staleness_seconds` | давность снимка, по которому считаются `onec_inbound_messages` и возраст ожидания | намного больше 30 с: снимок не обновляется, значения выше устарели |
| `onec_retention_overdue_payloads`, `onec_retention_overdue_quarantine_bodies` | тела с персональными данными и тела карантина старше срока хранения, ещё не стёртые | больше 0 дольше часа |
| `policies_retention_overdue_personal_data` | полисы, чьи ФИО, дата рождения и почта должны быть стёрты, но ещё нет | больше 0 дольше часа |
| `hikaricp_*{pool}` | пулы соединений: `application` (приём и разбор) и `rest-read` (REST-чтения) | `increase(hikaricp_connections_timeout_total{pool="application"}[5m]) > 0` |
| `cache_gets_total`, `cache_puts_total`, `cache_evictions_total`, `cache_size` с меткой `cache` | кэш ответов | — |

Доля ответов 503 на `/internal/v1/` выше 5 % за 10 минут — повод смотреть базу; считается по счётчику запросов сервера
с метками статуса и шаблона пути.

`/actuator/health` показывает компоненты `db` и `onecIngest`. `onecIngest` в `DOWN` с деталями `sqlstate`, `failure`,
`since`, `queues` — приём на паузе из-за базы; с деталью `queuesWithoutConsumer` вне паузы — у этих очередей нет
потребителя: окно запуска, потерянная на брокере очередь или потребитель, не поднявшийся после паузы.

## Сбои и ручные операции

- **REST отвечает 503.** База недоступна или чтение не уложилось в лимит. В журнале — тип отказа и SQLSTATE; нехватку
  соединений видно по `hikaricp_*{pool="rest-read"}`.
- **Приём на паузе.** Ничего делать не нужно: сообщения ждут в очередях, приём вернётся сам, когда проверка базы
  пройдёт. Если очередь осталась без потребителя (`queuesWithoutConsumer`) и после возврата базы и брокера, сервис его
  сам не поднимет — перезапустите сервис.
- **`FAILED` в сырье.** Причина — в `last_error`, без значений полей. Сначала устраняют причину (данные в 1С:УСК,
  настройка, исправленный разбор), затем возвращают строку в `NEW`.
- **Ручной возврат в `NEW`** (`FAILED` и `SKIPPED`). Строка встаёт в поток по старому моменту приёма и затрёт всё, что
  пришло после неё. Поэтому:
  - возвращают последнее сообщение по объекту или весь хвост потока, начиная с него, по порядку `received_at`;
  - только пока тело не стёрто по сроку хранения, иначе разбор отклонит его с причиной «тело сообщения стёрто по сроку
    хранения»;
  - сообщение, не дождавшееся родителя, возвращают, когда родитель уже пришёл: после возврата ожидание начинается
    заново, на полный срок;
  - сообщение в `AWAITING_PARENT` возвращать не нужно: его разберут, когда придёт родитель.
- **Родитель, который не придёт нашими потоками.** Его признают не нашим строкой в `foreign_reference` (GUID, вид
  записи, момент); его дети тогда пропускаются, а не ждут.
- **Карантин** (`quarantine`). Тела, которые база не приняла, лежат там байт в байт, причина — SQLSTATE. Сами они не
  переобрабатываются, их разбирает человек. Тело стирается через 30 суток, запись остаётся.
- **Зависшая передача прайса.** Части лежат в `BUFFERED`, прежний прайс услуги не тронут; раз в 15 минут журнал
  называет передачи старше часа. Передачу, которая уже не может стать последней, сервис закрывает пропуском сам;
  остальные разбирают вручную.
- **Справочник санаториев пуст при непустых страхователях** (`lpu` пуста, `insurers` нет) — неверный
  `projectx.onec.contract.rvl-activity-id`.
- **Договоры страхования пропускаются с причиной «договор не ДМС: тип продукта …»** — 1С:УСК пишет тип продукта
  иначе, чем в `PROJECTX_ONEC_CONTRACT_DMSPRODUCTTYPE`.

Почему процедуры такие — в [design.md](design.md), разделы «Разбор сырья, повторы и ручная переобработка» и «Прайсы».

## Сборка и локальный запуск

Нужны JDK 21 и Docker: тесты поднимают PostgreSQL и RabbitMQ в контейнерах.

```shell
./gradlew build          # сборка и тесты
./gradlew bootJar        # только jar: build/libs/data-reference-<версия>.jar
```

Вне рабочего контура сборке нужны публичные репозитории: `./gradlew -PpublicRepositories=true …` или переменная
`ORG_GRADLE_PROJECT_publicRepositories=true`. По умолчанию сборка берёт зависимости только из Nexus контура.

Локально база и брокер поднимаются из `docker-compose.yml`. В локальном брокере нет очередей 1С, поэтому слушатели
выключаются; пароли — локального контура:

```shell
docker compose up -d
DB_PASSWORD=data_reference RABBITMQ_PASSWORD=data_reference RABBITMQ_LISTENER_AUTO_STARTUP=false \
LOGGING_STRUCTURED_FORMAT_CONSOLE= ./gradlew bootRun
curl http://localhost:8081/actuator/info
```

Пустая `LOGGING_STRUCTURED_FORMAT_CONSOLE` переключает журнал с JSON на обычный текст. Сервис в контейнере —
`docker compose --profile app up -d --build`. Образ и локальный контур описаны ниже.

Переменные окружения и настройки — разделы выше; почему значения такие — в [design.md](design.md).

### Образ

Образ собирается из `Dockerfile` модуля (нужен BuildKit). Откуда взять jar, задаёт аргумент `JAR_SOURCE`:

- `build` — собрать внутри образа, так собирает CI. Нужна сеть до Nexus и дистрибутива Gradle. Вне контура задайте
  `PUBLIC_REPOSITORIES=true`, чтобы зависимости брались из Maven Central.
- `host` — взять собранный на машине, когда у сборки нет сети:
  `./gradlew bootJar && docker build --build-arg JAR_SOURCE=host .`

В сети без выхода наружу базовые образы и Gradle берутся из зеркала Nexus (compose читает эти аргументы из `.env`,
шаблон — `.env.example`):

```shell
docker build --build-arg JDK_IMAGE=<зеркало>/eclipse-temurin:21.0.12.1_1-jdk \
             --build-arg JRE_IMAGE=<зеркало>/eclipse-temurin:21.0.12.1_1-jre \
             --build-arg GRADLE_DISTRIBUTION_URL=<зеркало>/gradle-8.14.3-bin.zip .
```

Сертификаты корпоративного центра сертификации кладутся в `certs/`: без них JVM не доверяет Nexus при сборке и брокеру
1С:УСК при работе. В слои образа они не попадают. Тесты при сборке образа не запускаются: им нужен Docker, их
прогоняет CI до сборки образа.

### Локальный контур и стенд (`docker-compose.yml`)

```shell
docker compose up -d                  # база и брокер
docker compose --profile app up -d    # и сам сервис из Dockerfile
```

Пароли локального контура известны всем, поэтому порты по умолчанию открыты только на 127.0.0.1. Чтобы к сервису и
базе ходили с других машин (например, тестировщик из Postman), задайте в `.env` `SERVICE_BIND_ADDRESS=0.0.0.0` и/или
`POSTGRES_BIND_ADDRESS=0.0.0.0`, смените пароль базы и откройте порты в брандмауэре. Брокер и актуатор остаются на
127.0.0.1. Переменные сервиса и аргументы сборки берутся из `.env` (шаблон `.env.example`), без него действуют умолчания
`application.yml` и `Dockerfile`.

Локальный брокер — не брокер 1С:УСК: очередей 1С в нём нет, поэтому слушатели 1С выключены. Чтобы проверить приём,
задайте `RABBITMQ_LISTENER_AUTO_STARTUP=true` в `.env` и заведите очереди в панели брокера (`http://localhost:15672`).

Стенд для разработчиков сервиса Договоров (профиль `stand`) — база, сервис и закладка синтетических данных 1С:
контрагентов, договоров страхования, программ и полисов. Брокер стенду не нужен. Файлы и примеры запросов —
[src/test/resources/stand/](../src/test/resources/stand/README.md).

```shell
./gradlew clean bootJar               # только при JAR_SOURCE=host
docker compose up --build stand-seed
```

- Запускайте именно `stand-seed`, а не `--profile stand`: профиль целиком поднял бы и брокер.
- Стенд готов, когда закладка напечатала «сырьё 1С заложено» и пример 09 из README стенда отвечает 200: health
  отвечает UP раньше, чем данные разобраны. Статус SKIPPED у строки страхователя в `inbound_message` ожидаем:
  страхователь — не санаторий.
- Сервис — `http://localhost:8080`; профили `app` и `stand` вместе не поднимать, порт у них один. Клиенту из другого
  контейнера нужна сеть `data-reference_default` (адрес `http://data-reference-stand:8080`) или `network_mode: host`.
- Сброс — `docker compose --profile stand down -v`. Он удаляет и базу, общую с профилем `app`, и данные брокера; только
  базу удалит `docker volume rm data-reference_postgres-data` после `down` без `-v`.
- Если Docker Hub отвечает 429 (Too Many Requests): в `.env` задайте `JAR_SOURCE=host` и
  `JRE_IMAGE=mirror.gcr.io/library/eclipse-temurin:21.0.12.1_1-jre`, а образ базы возьмите из зеркала:
  `docker pull mirror.gcr.io/library/postgres:16.10-alpine && docker tag mirror.gcr.io/library/postgres:16.10-alpine postgres:16.10-alpine`.
  В рабочем контуре все образы берутся из Nexus.

## Документация

- [design.md](design.md) — почему так устроено: причины решений, которые не видны из кода.
- [CHANGELOG.md](../CHANGELOG.md) — версии, что нужно для установки каждой.
- `src/main/resources/application.yml` — настройки; умолчания значений обмена с 1С:УСК, которые одинаковы на всех
  контурах, заданы в `OneCContractProperties`; причины значений — в [design.md](design.md).
- `.env.example` — шаблон переменных для `docker-compose.yml`.
- [src/test/resources/stand/README.md](../src/test/resources/stand/README.md) — стенд с синтетическими данными для
  разработчиков соседних сервисов.
- «ТЗ API Data-reference» — контракт REST; технические задания 1С:УСК — контракт очередей.
