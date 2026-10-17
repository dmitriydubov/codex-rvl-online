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

Все переменные окружения с умолчаниями — в [CHANGELOG.md](../CHANGELOG.md) (раздел «Перенос»), в
`src/main/resources/application.yml` (умолчания значений обмена с 1С:УСК — в `OneCContractProperties`) и в шаблоне
`.env.example`; почему значения такие — в [design.md](design.md).

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
