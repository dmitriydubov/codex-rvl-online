# Инструкция по настройке проекта rvl-online (метод gauntlet)

Протокол `scan`, 2026-09-30, база `main` = `795e5ec`. Сессия шла в окружении `gauntlet-admin`
(`env_013PC74pJafEbrLaF8A6CVYY`), поэтому в ней `guard: off`: так и задумано для этого окружения.

Как собрано. 11 читателей первой фазы: сборка, гейты, код, тесты, хуки и CI, сеть, конфигурация и секреты, ПДн и гигиена,
документы REST, документы 1С, новые документы. 8 скептиков второй фазы пытались опровергнуть каждую ключевую находку; большинство
проверок они воспроизвели зондами на Testcontainers. Критик проверил план. Фактически прогнаны:
- гейты в областях `changed` и `all`, включая PIT по всему модулю;
- сборка в режиме контура (без `gauntlet/` и приёмки, Nexus заменён на Maven Central);
- `selftest.py`;
- достижимость около 135 адресов;
- сверка с документацией облака.

Файлы протокола:
- эта инструкция;
- `docs/gauntlet-setup-rvl-online-nexus.txt` — 312 модулей сборки для проверки в Nexus контура;
- `docs/gauntlet-plan-rvl-online.md` — план серии задач, ревизия 1.

Каталог `docs/gauntlet-*` в рабочий контур не переносится, как и `gauntlet/`.

## Коротко

- **Установка работает.**
  - `./gradlew gauntletFast` на `main` зелёный: 0 файлов в области, 155 unit-тестов и 6 тестов archTest.
  - `selftest.py`: 517 passed, 0 failed.
  - CI на PR #1: success за 3 мин 05 с.
  - Сборка без набора (режим контура): зелёная, 155 тестов.
- **Облако мешает работе в трёх местах.** Все три закрывает раздел 1: переменная окружения плюс setup script.
  1. Docker Hub отвечает 429 на анонимный pull. В этой сессии было 10 отказов, и Testcontainers-тесты, а с ними Stop-хук,
     краснеют из-за окружения.
  2. В VM нет `poppler-utils`, поэтому инструмент Read не открывает PDF. ТЗ — главный источник предметной области, а specifier и
     reviewer читают их именно через Read.
  3. Кэш Gradle и образы каждый раз холодные, а Maven Central на холодном кэше отвечает 429.
- **Защиты `main` нет.** На тарифе GitHub Free она для приватного репозитория недоступна. Коммит `795e5ec` ушёл в `main` мимо
  CI, потому что workflow запускается только на `pull_request`.
- **Старый код против гейтов.** Со `scope=all` 49 из 93 продакшен-файлов красные: при первом касании их придётся резать.
  - Checkstyle — 16 нарушений, PMD — 21, дублей CPD — 52.
  - PIT: test strength 82 % при пороге 100 %. После Ш3 (PR #5, тег `no-mutate`) — 81 %, но 604 из 666 мутантов без покрытия:
    покрытие строк PIT ≥ 80 % только у 5 из 67 классов с мутантами (у трёх из них есть выжившие), касание остальных
    62 классов требует unit-тестов (раздел 6).
  - Четыре конфликта гейтов с кодом решает владелец, и решить их нужно до рефакторинга (раздел 7; из его решений п. 3,
    `no-mutate`, после Ш3 выполнен — PR #5).
- **Подтверждённые дефекты** (с воспроизведением):
  - тело с `\u0000`, BOM или в UTF-16 бесконечно крутится в очереди;
  - повтор после временного отказа затирает более новые данные;
  - не-GET и `/mkb10?query&codes` отвечают 500;
  - ПДн уходят в журнал через Hibernate и в `last_error`;
  - срок хранения стирает тело только у потока полисов;
  - у PostgreSQL нет таймаутов, а три планировщика делят один поток;
  - слушатели 1С по умолчанию выключены, проверка TLS брокера тоже.

  Подробности и шаги исправления — в плане.
- **Секретов в дереве и в истории нет, ротация не нужна.** Но в `project_files/` лежат пример ПДн, похожих на реальные, и
  внутренние адреса компании (раздел 8).

---

## 1. Облачное окружение Claude Code

Окружений два:
- **Default** (`env_011KhGJ5hPyZ2jaWNeQYEh4A`) — для задач;
- **gauntlet-admin** (`env_013PC74pJafEbrLaF8A6CVYY`) — для правок набора (раздел 2).

**Где правится.** Окружение правится на claude.ai/code:
1. Нажмите значок облака с именем окружения над полем ввода (в открытой сессии — меню окружения в заголовке).
2. Наведите курсор на окружение и нажмите шестерёнку (или Edit).

В диалоге четыре поля: Name, Network access, Environment variables, Setup script. Раздел API credentials есть только на
тарифах Pro и Max и только у уже созданного окружения. Отдельной страницы настроек нет. Документация:
https://code.claude.com/docs/en/cloud-environments. Режим сессий — Accept edits.

### 1.1. Network access (оба окружения одинаково)

Уровень **Custom** и обязательно галка **«Also include default list of common package managers»**. Без галки пропадают Maven
Central, портал плагинов Gradle, `services.gradle.org`, Docker Hub, `*.gcr.io` (в том числе `mirror.gcr.io`),
`raw.githubusercontent.com` и GitHub.

В Allowed domains — по одному на строку домены документации из «Источников документации» `gauntlet/CLAUDE.md`. Их нет в
стандартном списке:

```
docs.oracle.com
docs.spring.io
docs.hibernate.org
docs.jboss.org
jakarta.ee
docs.liquibase.com
www.postgresql.org
jdbc.postgresql.org
www.rabbitmq.com
rabbitmq.github.io
javadoc.io
jspecify.dev
tomcat.apache.org
www.slf4j.org
logback.qos.ch
docs.micrometer.io
docs.junit.org
assertj.github.io
site.mockito.org
java.testcontainers.org
cucumber.io
docs.gradle.org
pitest.org
gradle-pitest-plugin.solidsoft.info
www.archunit.org
checkstyle.org
docs.pmd-code.org
www.jacoco.org
```

- В `gauntlet-admin` все эти домены отвечают 200, а `example.com` — 403. Значит, там уже Custom с этим списком.
- Список окружения **Default** из сессии не виден. Сессия установки, работавшая в Default, записала «закрыт» для
  `docs.junit.org`, `site.mockito.org` и `rabbitmq.github.io` (`gauntlet/CLAUDE.md`, README, раздел 6). Сравните Allowed
  domains двух окружений и выровняйте их. Решение 3 PR #1 — открыть `docs.junit.org`.
- **Не добавлять:**
  - `config.liquibase.com` — это аналитика Liquibase, её нужно выключить в конфигурации (раздел 6);
  - `downloads.gradle.org` — контрольную сумму обёртки владелец берёт на gradle.org сам;
  - `api.foojay.io`, `scans.gradle.com`.
- **Allowlist не поможет:**
  - `www.rabbitmq.com/docs/*` отвечает 403 от Cloudflare (`cf-mitigated: challenge`). Запасной путь —
    `raw.githubusercontent.com/rabbitmq/rabbitmq-website/main/versioned_docs/version-4.1/<страница>.md` или
    `<страница>/index.md`.
  - Вики Spring Boot (руководство по миграции на 4.0) из сессии не открыть: её открывает человек.
- Изменение списка доменов пересобирает кэш setup script.

### 1.2. Environment variables (формат `.env`)

Значения копируются в сессию при её старте, поэтому правка действует только на новые сессии. Их видят все пользователи
окружения, так что секретов здесь нет.

| Переменная | Default | gauntlet-admin | Зачем |
|---|---|---|---|
| `TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX=mirror.gcr.io/` | да | да | Образы Testcontainers пойдут с `mirror.gcr.io` вместо Docker Hub (там 429). Слеш в конце обязателен. Testcontainers 2.0.5 читает свойство `hub.image.name.prefix` из этой переменной и применяет префикс только к образам Docker Hub (сверено: `docs/features/image_name_substitution.md` по тегу 2.0.5, `javap` `PrefixingImageNameSubstitutor`). На зеркале есть `postgres:16.10-alpine`, `rabbitmq:4.1-alpine` и `testcontainers/ryuk:0.14.0` и по короткому пути, и по пути с `library/`; digest совпадает с Docker Hub. Зонд на префиксе прошёл. Код не меняется, и правило набора «реестр в коде не хардкодится» не нарушается |
| `GAUNTLET_GUARD=off` | **нет** | да (уже задана) | Выключает guard целиком (раздел 2) |
| `GAUNTLET_STOP_HOOK` | нет | нет | Stop-хук нужен и в админ-окружении |

**Не задавать ни в одном окружении:**
- `TEST_POSTGRES_URL`, `TEST_POSTGRES_USERNAME`, `TEST_POSTGRES_PASSWORD`, `TEST_RABBITMQ_URL`. С ними Testcontainers не
  поднимается, а `DatabaseCleaner` делает `TRUNCATE` всех таблиц указанной базы.
- `DB_*`, `RABBITMQ_*`, `ONEC_QUEUE_*` и учётные данные брокера 1С. Тестам они не нужны, а тесты приёма публикуют и читают
  очереди с именами контура (`TestBroker.java`), то есть на настоящем брокере забрали бы настоящие сообщения.
- `RABBITMQ_SSL_ENABLED`: `bootRun` и приёмка пошли бы по TLS.

`BASH_MAX_TIMEOUT_MS` можно задать, если нужны команды дольше 10 минут. Полный `mutationGate -Pgauntlet.scope=all` до Ш3
шёл 18–20 минут, но в работе он не нужен: область `changed` занимала минуты. После Ш3 (PR #5, тег `no-mutate`) pitest со
`scope=all` идёт 15 с, область `changed` — секунды (проба Ш3 на двух классах: 8 с против 72 с без тега).

### 1.3. Setup script (оба окружения)

Факты:
- скрипт запускается от root на Ubuntu 24.04 до старта Claude Code и обязан завершиться с кодом 0;
- если он укладывается примерно в 5 минут, снимок файловой системы (пакеты, Docker-образы, файлы) переиспользуется около 7 дней
  и пересобирается при смене скрипта или списка доменов;
- при resume скрипт не запускается;
- процессы в снимок не попадают, поэтому Docker-демон по-прежнему поднимает SessionStart-хук.

Сейчас прогрева нет: кэш Gradle появился в 09:55 внутри сессии, стартовавшей в 09:48, а образы тянулись уже в сессии.

```bash
#!/bin/bash
# rvl-online: setup script облачного окружения (Default и gauntlet-admin). Должен закончиться кодом 0 и уложиться в ~5 минут.
set -u
export DEBIAN_FRONTEND=noninteractive

# 1. poppler-utils: без pdftoppm инструмент Read не открывает PDF из project_files/ (ТЗ — источник предметной области).
#    apt-get update в образе VM падает на сторонних PPA (403 от прокси), основной архив Ubuntu при этом обновляется —
#    поэтому установка не зависит от кода возврата update.
( apt-get update -qq || true; apt-get install -y -qq poppler-utils ) > /tmp/setup-apt.log 2>&1 &
apt_pid=$!

# 2. Образы Testcontainers под теми именами, которые спросит Testcontainers при
#    TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX=mirror.gcr.io/ (иначе он скачает их заново).
(
  started=0
  if ! docker info > /dev/null 2>&1; then
    setsid nohup dockerd --host=unix:///var/run/docker.sock > /tmp/dockerd-setup.log 2>&1 &
    started=1
    for _ in $(seq 1 30); do docker info > /dev/null 2>&1 && break; sleep 1; done
  fi
  for image in postgres:16.10-alpine rabbitmq:4.1-alpine testcontainers/ryuk:0.14.0; do
    docker pull -q "mirror.gcr.io/${image}" || true
  done
  # 3. Образы docker compose (стенд Ш2a и профиль app) — под исходными именами: на compose и Dockerfile
  #    префикс Testcontainers не действует. JAR_SOURCE=host, поэтому eclipse-temurin:21-jdk в облаке не нужен.
  for image in postgres:16.10-alpine eclipse-temurin:21-jre; do
    docker pull -q "mirror.gcr.io/library/${image}" && docker tag "mirror.gcr.io/library/${image}" "${image}" || true
  done
  if [ "$started" = 1 ] && [ -f /var/run/docker.pid ]; then
    kill "$(cat /var/run/docker.pid)" 2> /dev/null || true
    for _ in $(seq 1 20); do [ -f /var/run/docker.pid ] || break; sleep 1; done
  fi
) > /tmp/setup-docker.log 2>&1 &
docker_pid=$!

wait "$apt_pid" "$docker_pid" || true
exit 0
```

**Не проверено в этой сессии.** Сам setup script виден только в диалоге окружения, а запуск `dockerd` внутри setup script
документацией не описан. Вторая новая сессия после сохранения скрипта должна показать:
- `which pdftoppm` → путь;
- в `docker images` образы `mirror.gcr.io/postgres`, `mirror.gcr.io/rabbitmq` и `mirror.gcr.io/testcontainers/ryuk`, а также
  `postgres:16.10-alpine` и `eclipse-temurin:21-jre` под исходными именами (для docker compose);
- строку статуса `docker: up`.

**Не включено, пока не проверено.**
- **Прогрев Gradle.** Документация не говорит, лежит ли клон репозитория на диске во время setup script. Кроме того, запросы
  setup script идут без `JAVA_TOOL_OPTIONS` сессии, а через них JVM получает прокси. Попробовать отдельной правкой:
  `cd /home/user/rvl-online/data-reference 2>/dev/null && ./gradlew --no-daemon dependencies > /tmp/setup-gradle.log 2>&1 || true`.
- **`gh`.** Он бесполезен: GitHub-прокси сессии отвечает на GraphQL 403, а `gh pr create` использует GraphQL (раздел 3).

### 1.4. Docker и ресурсы VM

- **Docker.**
  - `dockerd` поднимает SessionStart-хук в каждой сессии и после resume: `setsid nohup dockerd`, лог в `/tmp/dockerd.log`,
    ожидание до 30 с. `GAUNTLET_DOCKER=off` запуск отключает.
  - `service docker start` не работает: `/etc/init.d/docker` поднимает `ulimit -Hn 524288` при жёстком лимите 20000.
  - Ручной запуск: `setsid nohup dockerd --host=unix:///var/run/docker.sock > /tmp/dockerd.log 2>&1 &`, затем ждать
    `docker info`.
- **Ресурсы.**
  - VM: 4 vCPU, 15 GiB без swap, около 28 ГБ свободно на одном диске (`/`, `/tmp`, `/var/lib/docker`), `nofile` 20000.
    Документация даёт ориентир 4 vCPU / 16 ГБ / 30 ГБ.
  - Команда Bash ждёт 2 минуты, по запросу до 10, затем уходит в фон.
  - Таймауты хуков: SessionStart 180 с, Stop 1800 с, Stop-хук hardener'а 3600 с.
- **Длительность прогонов** на этой VM:

  | Прогон | Время |
  |---|---|
  | `gauntletFast`, холодный кэш | 142 с |
  | `gauntletFast --rerun-tasks`, `scope=all` | 57 с |
  | `test` | ~72 с |
  | `mutationGate -Pgauntlet.scope=all` (4 миниона; до Ш3 у каждого свой контейнер PostgreSQL) | 18–20 мин; после Ш3 (PR #5, тег `no-mutate`) pitest — 15 с, 0 стартов контейнеров |
  | Сборка в режиме контура | 85 с тёплая, ~113 с холодная |

- **Параллельные прогоны.** Два прогона гейтов в одном каталоге сборки не запускать: отчёты побьются. Параллельные
  Testcontainers-прогоны в нескольких копиях дерева на 4 vCPU конкурируют за CPU и память.

---

## 2. Админ-окружение `gauntlet-admin`

- **Отличия от Default.**
  - Задана `GAUNTLET_GUARD=off`: `guard.py` сразу разрешает всё. Не действуют защита набора и `CLAUDE.md`, ограничения ролей,
    заморозка спецификации и запреты `amend`, `reset`, `push --force` и push в `main`.
  - Остаются `permissions.deny` (`gh pr merge`, `git rebase`, `filter-*`) и ограничение GitHub-прокси: push только в текущую
    ветку сессии.
  - `GAUNTLET_STOP_HOOK` не задан, поэтому Stop-хук работает. Но правки только в `gauntlet/` он не проверяет: список
    затронутых модулей пуст (`stop_gate.py:102-104`).
- **Порядок работы.**
  - Только правки набора (`gauntlet/`, `.claude/`, `CLAUDE.md`, workflow) и утверждённой спецификации, по решению владельца.
  - После каждой правки вручную: `python3 gauntlet/hooks/selftest.py` (ожидается 517/0 или больше) и
    `cd data-reference && ./gradlew gauntlet --continue`. Затем PR.
- **Сеть, переменная зеркала и setup script** — те же, что в Default (раздел 1).
- **Сессии задач.** Не открывать сессии задач в `gauntlet-admin`. Координатор создаёт их с явным ID окружения Default: сессия,
  созданная другой сессией без явного окружения, наследует окружение создавшей.

---

## 3. GitHub

- **Приложение Claude.** Установлено: у сессии есть push (`can_push=true`). Токены не нужны: `GH_TOKEN` и `GITHUB_TOKEN` в
  сессии — заглушка, которую подменяет прокси.
- **Workflow `gauntlet`.** Активен, секретов не использует.
  - Сейчас:
    - запуск на `pull_request` и `workflow_dispatch`;
    - полная история (`fetch-depth: 0`), JDK Temurin 21, все модули из `module=`, таймаут 90 мин;
    - артефакт `gauntlet-reports` хранится 90 дней;
    - прогон на PR #1 — success за 3 мин 05 с.
  - Замечания, правка — в админ-окружении:
    1. **Нет триггера `push: branches: [main]`.** Прямой push `795e5ec` в `main` CI не проверил. Кэш Gradle `setup-gradle@v4`
       пишет только на основной ветке, поэтому сейчас каждый PR качает зависимости заново. До правки можно один раз запустить
       `workflow_dispatch` на `main`.
    2. **Нет блока `permissions:`.** Рекомендуется `contents: read`. Actions закреплены мажорными тегами (`@v4`), а не SHA.
    3. **Нет job сборки в режиме контура** (`-PpublicRepositories=false` с Maven Central вместо Nexus). Её можно добавить
       информационной проверкой.
    4. **Ночной прогон `scope=all`** до адаптации кода всегда будет красным (раздел 7). Если его добавлять, то только
       информационным.
- **Защита `main`.**
  - Репозиторий приватный, тариф Free. Rulesets и защита веток для приватных репозиториев на Free недоступны: API отвечает 403
    «Upgrade to GitHub Pro».
  - Варианты:
    1. **GitHub Pro** (рекомендуется): ruleset на `main` — только через PR, обязательная проверка `gauntlet`, запрет force push.
       Тогда же CODEOWNERS на `gauntlet/`, `.claude/`, `CLAUDE.md`, `*/CLAUDE.md`, `.github/`.
    2. **Без Pro:** триггер `push` на `main` и правило «в `main` только через PR, красный PR не мержить» в
       `gauntlet/README.md`.
  - Публичный репозиторий не рассматривать: в `project_files/` ПДн и внутренние адреса (раздел 8).
- **Что guard не защищает.** Решать в админ-окружении, с кейсами в `selftest.py`.
  - `contract-service/CLAUDE.md` (сейчас это правила data-reference), будущий `data-reference/CLAUDE.md` и любые
    `.github/workflows/*.yml`, кроме `gauntlet.yml`, агент может править через Edit и Bash. Новый workflow из ветки агента
    исполнится в Actions до ревью человека.
  - Запись в GitHub через MCP-инструменты (`merge_pull_request`, `push_files`, `create_or_update_file`, `delete_file`,
    `enable_pr_auto_merge`) и через `curl` к `api.github.com` хук не проверяет. Запрет `gh pr merge` их не покрывает.
- **Как открывать PR.** `gh` в VM нет. Даже если его поставить, `gh pr create` не сработает: GitHub-прокси отвечает на GraphQL
  403 «GitHub GraphQL is not available from Claude Code sessions; use the REST API».
  - В облачной сессии PR открывается MCP-инструментом `create_pull_request` или кнопкой Create PR.
  - Шаг 8 `gauntlet/CLAUDE.md` и разрешения `gh pr …` в `.claude/settings.json` стоит поправить (владелец).
  - Учесть: `guard.py` запрещает любой `gh api` с подстрокой «merge» в аргументах, в том числе в тексте тела PR.

---

## 4. Закладки с промптами

`claude.ai/code` принимает параметры `repositories`, `environment` (имя или ID окружения, предвыбор), `prompt` (или `q`) и
`prompt_url`. Для задач указывайте `environment` Default: так задача случайно не стартует в `gauntlet-admin`. Промпт
открывается в поле ввода, его можно дописать перед отправкой.

| Закладка | Окружение | Текст промпта |
|---|---|---|
| Новая задача | Default | Новая задача в data-reference от свежего main. Работай по CLAUDE.md и gauntlet/CLAUDE.md буквально. Первым сообщением приведи строку статуса SessionStart. Задача: … |
| Шаг плана | Default | Шаг Ш<N> плана docs/gauntlet-plan-rvl-online.md: <название>. Задача для Jira согласована, заново не пиши. Работай по gauntlet/CLAUDE.md буквально; первым сообщением приведи строку статуса SessionStart. |
| Разбор инцидента | Default | Разбор инцидента в data-reference: только чтение, ничего не коммить и не пушить. Симптом: … |
| Правка набора | gauntlet-admin | Правка набора gauntlet по решению владельца (админ-окружение). После правок: python3 gauntlet/hooks/selftest.py и cd data-reference && ./gradlew gauntlet --continue, затем PR. Что меняем: … |

Готовые адреса:

```
https://claude.ai/code?repositories=dmitriydubov/rvl-online&environment=env_011KhGJ5hPyZ2jaWNeQYEh4A&prompt=%D0%9D%D0%BE%D0%B2%D0%B0%D1%8F%20%D0%B7%D0%B0%D0%B4%D0%B0%D1%87%D0%B0%20%D0%B2%20data-reference%20%D0%BE%D1%82%20%D1%81%D0%B2%D0%B5%D0%B6%D0%B5%D0%B3%D0%BE%20main.%20%D0%A0%D0%B0%D0%B1%D0%BE%D1%82%D0%B0%D0%B9%20%D0%BF%D0%BE%20CLAUDE.md%20%D0%B8%20gauntlet/CLAUDE.md%20%D0%B1%D1%83%D0%BA%D0%B2%D0%B0%D0%BB%D1%8C%D0%BD%D0%BE.%20%D0%9F%D0%B5%D1%80%D0%B2%D1%8B%D0%BC%20%D1%81%D0%BE%D0%BE%D0%B1%D1%89%D0%B5%D0%BD%D0%B8%D0%B5%D0%BC%20%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D0%B4%D0%B8%20%D1%81%D1%82%D1%80%D0%BE%D0%BA%D1%83%20%D1%81%D1%82%D0%B0%D1%82%D1%83%D1%81%D0%B0%20SessionStart.%20%D0%97%D0%B0%D0%B4%D0%B0%D1%87%D0%B0%3A%20

https://claude.ai/code?repositories=dmitriydubov/rvl-online&environment=env_011KhGJ5hPyZ2jaWNeQYEh4A&prompt=%D0%A8%D0%B0%D0%B3%20%D0%A8%3CN%3E%20%D0%BF%D0%BB%D0%B0%D0%BD%D0%B0%20docs/gauntlet-plan-rvl-online.md%3A%20%3C%D0%BD%D0%B0%D0%B7%D0%B2%D0%B0%D0%BD%D0%B8%D0%B5%3E.%20%D0%97%D0%B0%D0%B4%D0%B0%D1%87%D0%B0%20%D0%B4%D0%BB%D1%8F%20Jira%20%D1%81%D0%BE%D0%B3%D0%BB%D0%B0%D1%81%D0%BE%D0%B2%D0%B0%D0%BD%D0%B0%2C%20%D0%B7%D0%B0%D0%BD%D0%BE%D0%B2%D0%BE%20%D0%BD%D0%B5%20%D0%BF%D0%B8%D1%88%D0%B8.%20%D0%A0%D0%B0%D0%B1%D0%BE%D1%82%D0%B0%D0%B9%20%D0%BF%D0%BE%20gauntlet/CLAUDE.md%20%D0%B1%D1%83%D0%BA%D0%B2%D0%B0%D0%BB%D1%8C%D0%BD%D0%BE%3B%20%D0%BF%D0%B5%D1%80%D0%B2%D1%8B%D0%BC%20%D1%81%D0%BE%D0%BE%D0%B1%D1%89%D0%B5%D0%BD%D0%B8%D0%B5%D0%BC%20%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D0%B4%D0%B8%20%D1%81%D1%82%D1%80%D0%BE%D0%BA%D1%83%20%D1%81%D1%82%D0%B0%D1%82%D1%83%D1%81%D0%B0%20SessionStart.

https://claude.ai/code?repositories=dmitriydubov/rvl-online&environment=env_011KhGJ5hPyZ2jaWNeQYEh4A&prompt=%D0%A0%D0%B0%D0%B7%D0%B1%D0%BE%D1%80%20%D0%B8%D0%BD%D1%86%D0%B8%D0%B4%D0%B5%D0%BD%D1%82%D0%B0%20%D0%B2%20data-reference%3A%20%D1%82%D0%BE%D0%BB%D1%8C%D0%BA%D0%BE%20%D1%87%D1%82%D0%B5%D0%BD%D0%B8%D0%B5%2C%20%D0%BD%D0%B8%D1%87%D0%B5%D0%B3%D0%BE%20%D0%BD%D0%B5%20%D0%BA%D0%BE%D0%BC%D0%BC%D0%B8%D1%82%D1%8C%20%D0%B8%20%D0%BD%D0%B5%20%D0%BF%D1%83%D1%88%D0%B8%D1%82%D1%8C.%20%D0%A1%D0%B8%D0%BC%D0%BF%D1%82%D0%BE%D0%BC%3A%20

https://claude.ai/code?repositories=dmitriydubov/rvl-online&environment=env_013PC74pJafEbrLaF8A6CVYY&prompt=%D0%9F%D1%80%D0%B0%D0%B2%D0%BA%D0%B0%20%D0%BD%D0%B0%D0%B1%D0%BE%D1%80%D0%B0%20gauntlet%20%D0%BF%D0%BE%20%D1%80%D0%B5%D1%88%D0%B5%D0%BD%D0%B8%D1%8E%20%D0%B2%D0%BB%D0%B0%D0%B4%D0%B5%D0%BB%D1%8C%D1%86%D0%B0%20%28%D0%B0%D0%B4%D0%BC%D0%B8%D0%BD-%D0%BE%D0%BA%D1%80%D1%83%D0%B6%D0%B5%D0%BD%D0%B8%D0%B5%29.%20%D0%9F%D0%BE%D1%81%D0%BB%D0%B5%20%D0%BF%D1%80%D0%B0%D0%B2%D0%BE%D0%BA%3A%20python3%20gauntlet/hooks/selftest.py%20%D0%B8%20cd%20data-reference%20%26%26%20./gradlew%20gauntlet%20--continue%2C%20%D0%B7%D0%B0%D1%82%D0%B5%D0%BC%20PR.%20%D0%A7%D1%82%D0%BE%20%D0%BC%D0%B5%D0%BD%D1%8F%D0%B5%D0%BC%3A%20
```

Протоколы `coordinate` и `contract <ревизия прода>` запускаются с прикреплённой инструкцией метода (`coordinate` ещё и с
планом). Файл через адрес не прикрепить, поэтому для них закладка — только окружение и репозиторий, а слово и файлы добавляются
в сессии.

---

## 5. Проверка установки

Проверка выполняется в новой сессии от `main` в окружении **Default**.

1. **Строка статуса.** Первым сообщением попросите: «процитируй строку статуса SessionStart и выполни
   `printenv GAUNTLET_GUARD`».
   - Ожидается `[gauntlet] active - scope: changed vs origin/main; guard: on; stop hook: on; docker: up` и пустой `printenv`.
   - Если вместо этого WARNING о merge-base, строка выходит без полей guard, stop hook и docker (`session_start.sh:54-58`).
     Это известный недочёт хука, а не выключенная защита: проверьте `printenv`.
   - В диалоге окружения Default не должно быть строк `GAUNTLET_*`: имя окружения ничего не гарантирует.
2. **Guard.** Попросите дописать строку в `gauntlet/README.md`. Ожидается отказ
   «[gauntlet guard] … is part of the gauntlet and is human-owned».
3. **Selftest.** `python3 gauntlet/hooks/selftest.py` → 517 passed, 0 failed.
4. **Гейты.** В `data-reference`:
   - `./gradlew gauntletInfo`: `module : data-reference`, overlay `config/data-reference/gauntlet.properties`,
     `basePackage : ru.projectx.datareference`, glue `ru.projectx.datareference.acceptance`, cucumber 8.0.1;
   - `./gradlew gauntletFast --continue`: BUILD SUCCESSFUL, «changed vs origin/main … 0 file(s)», 155 тестов в `test`
     (на ветке Ш3, PR #5, — 167 в 25 классах, 0 skipped: тег `no-mutate` из `test` не исключает), 6 в `archTest`, приёмка SKIPPED;
   - `./gradlew mutationGate`: «no changed main classes - mutation testing skipped»;
   - `./gradlew buildEnvironment` показывает `info.solidsoft.gradle.pitest:gradle-pitest-plugin:1.19.0`: набор подключён.

   Со `scope=all` гейты ожидаемо красные — это эталон долга (раздел 7), а не поломка установки.
5. **Setup script и зеркало.** `which pdftoppm` → путь; в `docker images` образы `mirror.gcr.io/*`; в `/tmp/dockerd.log` нет
   429 после старта тестов.
6. **Сеть.** Выполните
   `curl -sS -o /dev/null -L -w '%{http_code} %{url_effective}\n'` по адресам:
   - `https://docs.spring.io/spring-boot/4.0/`;
   - `https://docs.spring.io/spring-framework/reference/7.0/core/resilience.html`;
   - `https://docs.hibernate.org/orm/7.2/userguide/html_single/Hibernate_User_Guide.html`;
   - `https://docs.junit.org/6.0.3/overview.html`;
   - `https://javadoc.io/doc/org.mockito/mockito-core/5.20.0`;
   - `https://raw.githubusercontent.com/rabbitmq/rabbitmq-website/main/versioned_docs/version-4.1/confirms.md`.

   Ожидается 200. `curl -sS "$HTTPS_PROXY/__agentproxy/status"`: в `recentRelayFailures` нет нужных доменов (отказы
   `config.liquibase.com` — до выключения аналитики, раздел 6).
7. **Приёмка.** При нуле фич `acceptanceTest` SKIPPED, `gherkinMutation` — «no mutants - OK». Это работу приёмки **не
   доказывает**: сквозной путь проверит только первая утверждённая фича (план, этап 1 — спецификация контракта для Договоров).
8. **Hardener.** После него в первой поведенческой задаче должен появиться `data-reference/build/gauntlet/mutationGate-hook.log`.
   По логу облачной сессии рабочая область не считается доверенной («workspace not yet trusted»), а в такой сессии
   frontmatter-хуки сабагентов, по документации, пропускаются. Если лог не появится, нужен SubagentStop-хук в
   `.claude/settings.json` (правка владельца).

---

## 6. Известные ловушки

**Окружение**
- **Docker Hub 429** на анонимный pull: Testcontainers-тесты падают, Stop-хук краснеет из-за окружения. Лечится зеркалом и
  setup script (раздел 1). Признак в журнале — `toomanyrequests` в `/tmp/dockerd.log`.
- **Maven Central 429** на пачку новых артефактов, ещё на записи configuration cache: повторить через 30–60 с.
- **Liquibase 5.0.3** при каждом старте синхронно запрашивает `https://config.liquibase.com/analytics.yaml` (таймауты по
  1500 мс): в журнале прокси 20 отказов за прогон. Свойство `spring.liquibase.analytics-enabled` в Boot 4.0.7 есть, но не
  задано. В облаке это шум, в контуре — попытка выхода наружу. Выключается в `application.yml` (план, Ш1) или переменной
  `SPRING_LIQUIBASE_ANALYTICS_ENABLED=false`.
- **Контейнеры Docker в облачной VM не выходят в сеть** (bridge). Поэтому `docker build` с `JAR_SOURCE=build` в облаке
  падает. Образ собирать `./gradlew clean bootJar && docker build --build-arg JAR_SOURCE=host`, базовые образы брать через
  `--build-arg JDK_IMAGE=mirror.gcr.io/library/eclipse-temurin:21-jdk` и `JRE_IMAGE=…:21-jre`.
- **Stop-хук платформы** (`~/.claude/stop-hook-git-check.sh`, не gauntlet):
  - один раз блокирует остановку при незакоммиченных изменениях. На остановке для утверждения спецификации черновик фич не
    коммитить, остановиться повторно;
  - при неподписанном коммите может посоветовать `amend` или `rebase`, но их запрещает guard: историю не переписывать, вторая
    остановка проходит.
- **После простоя VM освобождается.** Новая VM приходит без фоновых процессов, `dockerd` поднимет хук. Сохранится ли
  незакоммиченный черновик спецификации, не проверено: specifier должен выводить полный текст сценариев в чат.
- **Хуки из `.claude/settings.json`** грузятся только в сессии с одним репозиторием.
- **Сайты без версии показывают чужую версию:**
  - корень `docs.junit.org` — 6.1.3;
  - `checkstyle.org` — 14.3.0;
  - JaCoCo trunk — 0.8.16-snapshot;
  - `rabbitmq.github.io/…/api/current/` — клиент 5.28.0.

  Сверять версию по самой странице.
- **`raw.githubusercontent.com` не отдаёт каталоги** (404), только файлы: `filelist.sgml`, `nav.adoc`, `mkdocs.yml`,
  `000_Index.adoc`, `checks.xml`. Неверные адреса в «Источниках документации»:
  - `docs.gradle.org/8.14.3/userguide/` → 404 (правильно `…/userguide/userguide.html`);
  - `www.archunit.org/userguide/` → 404 (правильно `…/userguide/html/000_Index.html`);
  - у Tomcat 11.0.22 нет javadoc-jar, есть sources-jar;
  - JUnit в приёмке — 6.1.3 (Cucumber 8.0.1), в тестах — 6.0.3.

  Правит владелец (план, О1).

**Гейты**
- **Область гейтов — изменённый файл целиком.**
  - Checkstyle, PMD и CPD проверяют только `src/main/java`.
  - CPD валит дубль, если хотя бы одно его место в изменённом файле: парный неизменённый файл тоже придётся трогать.
  - Порог покрытия PIT (80 %) считается суммарно по мутируемым классам, и проверяется до test strength.
- **PIT:**
  - До Ш3 он гонял контекстные тестовые классы на Testcontainers (19 на момент scan, тегов `no-mutate` не было). Отсюда долгие
    прогоны, TIMED_OUT и ложные выжившие: 9 мутантов «`stream()` → null» выживали, потому что контексты создаются до первого
    мутанта.
  - После Ш3 (PR #5) `@Tag("no-mutate")` стоит на составных аннотациях `@IntegrationTest` и `@AmqpIntegrationTest`
    (`src/test/java/…/support/`). 20 контекстных классов (с `ConsumerStandTest` из Ш2a) идут только в `test`: PIT исполняет
    5 unit-классов, контейнеры не стартуют, pitest со `scope=all` — 15 с.
  - Мутант, которого убивали только контекстные тесты, теперь NO_COVERAGE или SURVIVED: проба Ш3 на `OneCValues` — строки 51,
    70, 86, 87, 97 → nocov, 92 → surv. 9 ложных выживших «`stream()` → null» ушли в NO_COVERAGE.
  - RUN_ERROR на record-классах (падение JVM и `UnsupportedOperationException` при `redefineClasses`) засчитывается как убитый
    мутант и может оставить в `data-reference/` файлы `hs_err_pid*.log`. С Ш2a они в `.gitignore` (корень, `hs_err_pid*.log`),
    и `gauntletScope` их не видит: в Ш3 подложенный файл не попал в `git ls-files --others --exclude-standard`.
    После Ш3 (PR #5) в прогоне `scope=all` с тегом `hs_err_pid*.log` не появился, но RUN_ERROR остались:
    3 мутанта `InboundRetryProperties:36` (record) засчитаны убитыми.
  - Перед контрольным прогоном удалять `build/pitHistory.txt`: включён инкрементальный анализ.
- **Приёмка при нуле фич SKIPPED.** Если фичи есть, но исполнено 0 сценариев, `acceptanceCheck` зелёный. После удаления фич
  он читает устаревшие результаты.
- **Под строгой политикой каждый касаемый класс требует unit-тестов.** Тесты с Testcontainers помечены `no-mutate` (решение 8
  PR #1, Р3 а; Ш3, PR #5). На scan unit-покрытие (JaCoCo) — 10,3 % строк: у 9 из 77 файлов ≥ 80 %. После Ш3 PIT со
  `scope=all`: покрытие строк 9 %, ≥ 80 % только у `InboundRetryProperties`, `OneCStream`, `OneCEnvelopeParser`, `OneCValues` и
  `ReferenceImportService`, у остальных 62 классов с мутантами ниже (у 58 — 0 %). Касание такого класса без unit-тестов может
  уронить `mutationGate` по покрытию: порог суммарный (см. выше).

**Код и конфигурация data-reference**
- **Лимит PMD TooManyMethods (12).** `ReferenceController` (15 методов) и `OneCQueueListener` (15) краснеют при любой правке.
  У `PriceListTransferAssembler` 12 — на пределе, у `ApiExceptionHandler` 9.
- **Два бина `ObjectMapper`.** snake_case наружу даёт только `@Primary` `jsonMapper` Boot. Бин из `JacksonConfig` никуда не
  внедряется. Настраивать REST-JSON через свойства или customizer Boot.
- **`TestRestTemplate` в тестовых jar Boot 4.0.7 нет** — есть `RestTestClient` из spring-test.
  `spring-boot-starter-webmvc-test` в Nexus контура нет: срез `@WebMvcTest` недоступен, unit-тесты веб-слоя —
  `MockMvcBuilders.standaloneSetup`.
- **`missing-queues-fatal=true`.** Неверное имя любой из 11 очередей роняет старт. Имена сверять со списком брокера, а не с ТЗ:
  у ТЗ-2 `application.yml` (дефис) и `contract-service/CLAUDE.md` (подчёркивание) расходятся.
- **Слушатели 1С по умолчанию выключены** (`RABBITMQ_LISTENER_AUTO_STARTUP=false`). Сервис здоров по пробам, но 1С не читает,
  и в журнале об этом ни строки.
- **Проверка TLS-сертификата и имени хоста брокера по умолчанию выключена** (в Boot 4.0.7 включена).
  - `RABBITMQ_SSL_ENABLED=true` без `RABBITMQ_PORT=5671` даёт TLS на порт 5672.
  - При `validate-certificate=false` флаг `verify-hostname` в spring-rabbit 4.0.4 ничего не делает.
- **У PostgreSQL нет таймаутов.** `socketTimeout` 0, `statement_timeout` и `lock_timeout` 0, транзакционного таймаута нет.
  Три `@Scheduled` при виртуальных потоках выполняются одним потоком `SimpleAsyncTaskScheduler`: зависший запрос останавливает
  импорт, стирание ПДн и алерт одновременно (воспроизведено).
- **Приём при сбое базы.**
  - Слушатель любой сбой записи считает недоступностью базы: 5 с паузы, затем nack с возвратом. Тело, которое Jackson
    принимает, а `jsonb` нет (`\u0000`, UTF-8 BOM, UTF-16), крутится бесконечно.
  - На quorum-очереди RabbitMQ ≥ 4.0 (delivery-limit по умолчанию 20) сообщение будет потеряно.
  - При prefetch 20 возврат переставляет сообщения: порядок записи [2, 3, 4, 5, 1].
- **Ручная переобработка сырья SQL-ом** — только короткими транзакциями и строго по `received_at`. Тело полиса стирается через
  30 суток, после этого строку не переобработать.
- **`TEST_POSTGRES_URL` на нетестовую базу** — полная очистка её таблиц.
- **Пропускная способность и мониторинг.** Чистка ПДн — не больше 500 тел в час. Мониторинга FAILED, SKIPPED и зависших
  передач ТЗ-9 нет, только WARN.

**Сборка и образ**
- **Gradle 8.14.3 запускается на JVM 8–24.** Тулчейн Java 21 без плагина-резолвера: на машине сборки нужен JDK 21.
- **Dockerfile.**
  - Требует BuildKit (`RUN --mount`).
  - В режиме `JAR_SOURCE=host` маска `data-reference-*.jar` при нескольких совпадениях молча берёт последний по алфавиту
    (воспроизведено на 0.1.0/0.9.0/0.10.0). Перед сборкой — `clean bootJar`.
- **Плавающие теги** `eclipse-temurin:21-jre` (сейчас JDK 21.0.12.1 на Ubuntu 26.04.1) и `rabbitmq:4.1-alpine` (4.1.8,
  пересобран 2026-09-29). Прокси Docker в Nexus может отдать под тем же тегом другое.
- **`spring-boot-starter-web` 4.0.7 помечен устаревшим** в пользу `starter-webmvc`, но в Nexus контура есть только он. Не менять.

---

## 7. План адаптации старого кода

Набор включён со `scope=changed`: история не разбирается, но **первое касание файла ставит его под все гейты целиком**. Полный
прогон со `scope=all` на `795e5ec` — эталон долга:

| Гейт | Итог |
|---|---|
| integrity, archTest, CRAP | зелёные (max CRAP 6.0) |
| Checkstyle | 16 нарушений в 12 файлах: CC 4 у 9 методов, у `ContragentMapper#applySanatorium` CC 5 и 28 строк, ParameterNumber 5 у трёх, ClassFanOutComplexity 22 у `LpuCatalogService`, LambdaBodyLength у `PolicyMapper` |
| PMD | 21 нарушение в 17 файлах: TooManyMethods у 5 классов, ConstructorCallsOverridableMethod у 9 сущностей, ImmutableField на трёх коллекциях, AbstractClassWithoutAbstractMethod у `OneCEntity`, ClassWithOnlyPrivateConstructorsShouldBeFinal у трёх исключений |
| CPD | 52 дубля в 29 файлах; 35 из них — цепочки аксессоров и присваиваний в сущностях; все 52 держатся на `dry.ignoreIdentifiers=true` |
| PIT | 666 мутантов: убито 467, выжило 107 (в 34 классах), без покрытия 80, RUN_ERROR 10, TIMED_OUT 2; test strength 82 % (порог 100), покрытие строк 93 %; ниже 80 % у 6 классов (`AppealTypeMapper` 20 %, `Jackson3JsonFormatMapper`, `StuckTransferAlert`, `AppealType`, `ReferenceNotFoundException`, `OneCQueueListener`). После Ш3 (PR #5, тег `no-mutate`): убито 50 (в том числе 3 RUN_ERROR у `InboundRetryProperties:36`), выжило 12 (`OneCEnvelopeParser` @41,46, `OneCQueueListener` @135,146,181, `OneCValues` @77,92, `ReferenceImportService` @72,97,106,109,114), без покрытия 604; test strength 81 %; покрытие строк 9 % (146 из 1551), ≥ 80 % только у 5 из 67 классов с мутантами; pitest 15 с, 0 стартов контейнеров |

**49 файлов придётся резать при касании:**

| Пакет | Файлы |
|---|---|
| `config` | `Jackson3JsonFormatMapper` |
| `controller` | `ReferenceController` |
| `dto` | `LpuServicesResponse`, `Mkb10Response`, `PolicyResponse`, `PolicySearchResponse` |
| `entity` | 15 сущностей, кроме `InboundStatus`, `InboxEntry`, `OneCStream` |
| `exception` | 3 исключения REST |
| `mapper` | все 10 мапперов |
| `service` | `InboundMessageService`, `LpuCatalogService`, `Mkb10QueryService`, `PolicySearchService`, `PriceContractResolver`, `ReferenceQueryService`, `RvlAppealTypes` |
| `service.integration` | `OneCEnvelopeParser`, `OneCQueueListener` |
| `service.onec` | `OneCIngestService`, `OneCValues`, `PriceListTransferAssembler`, `ReferenceImportService` |
| `service.schedulers` | `PersonalDataRetention`, `StuckTransferAlert` |

**44 файла зелёные:** `DataReferenceApplication`, остальные `config`, `ApiExceptionHandler`, `RequestIdFilter`, шесть DTO, три
перечисления и сущности inbox, три исключения 1С, все `repository`, `InboxService`, `PriceContract`, `ReferenceCalendar`,
`ImportResult`, `StreamMapper`, `ReferenceImportScheduler`. Это итог на `795e5ec`, где PIT исполнял и контекстные тесты. После Ш3
(PR #5, тег `no-mutate`) покрытие строк PIT ≥ 80 % из этих файлов только у `InboundRetryProperties` и `OneCStream`; у остальных
файлов с мутантами (`ImportResult` — 50 %, прочие — 0 %) первое касание требует unit-тестов (в плане — «зел.†»).

**Решения владельца до рефакторинга.** Это правки набора, делаются в админ-окружении; подробные варианты — в плане, Р1–Р5.
1. **CPD на аксессорах сущностей.** Варианты: `--exclude` каталога `entity` в задаче `cpdReport`; новая опция `DryGate`;
   `dry.minimumTokens=100` (останется 4 дубля, но пройдут реальные дубли на 60–99 токенов).
2. **PMD ImmutableField на персистентных коллекциях.** Требование JPA 3.2 §2.1 — поля без `final`. Нужен узкий
   `violationSuppressXPath` для `@OneToMany`, `@ManyToMany`, `@ElementCollection`. ConstructorCallsOverridableMethod снимается
   кодом (статическая фабрика), исключение не нужно.
3. **Решение 8 PR #1 (`no-mutate`) с новыми данными** — решено 2026-09-30 (Р3 а), выполнено Ш3 (PR #5) тегом в тестах
   `data-reference`, не правкой набора. Unit-покрытие без контекстных тестов — 10,3 %. Варианты:
   - (а) тег сразу на составных аннотациях — каждый касаемый класс потребует unit-тестов;
   - (б) оставить контекстные тесты в PIT — медленно, ложные выжившие;
   - (в) тег на классы тестов по кластерам, по мере появления unit-тестов.
4. **RUN_ERROR** показывать отдельной строкой «не проверено» и не засчитывать как убитый.
5. **Приёмка.** `acceptanceCheck` должен падать при 0 исполненных сценариев и не читать устаревшие результаты.

**Порядок работ задаёт владелец** (решения 2026-09-30, план):
1. data-reference до финального рабочего решения, первым — контракт для сервиса Договоров;
2. затем contract-service;
3. оплата последней.

Поэтому рефакторинг идёт не сплошной серией, а **по касанию**: перед шагом, который трогает красный файл, этот файл приводится
к лимитам. Рефакторинг, не нужный ни одному шагу, уходит в бэклог качества.

Порядок кластеров, если резать их подряд:
1. гигиена;
2. мелкие изолированные: исключения, Jackson, разделение `ReferenceController`;
3. сервисы с CC 4;
4. `OneCValues` и `OneCEnvelopeParser`;
5. планировщики;
6. `OneCQueueListener`;
7. `PriceListTransferAssembler`;
8. кластер мапперов;
9. разрыв цикла `mapper ↔ service`;
10. кластеры сущностей;
11. пары DTO.

Поведенческие шаги идут после рефакторинга тех файлов, которые трогают. Первый из них — исполняемая спецификация действующего
контракта трёх GET для Договоров вместе с каркасом приёмки (план, Ш25a): код сервиса он не меняет. Полный граф — в плане.

**Архитектура.** Против направления четыре ребра, все объявлены в `architecture.properties`, `archTest` зелёный.
- `mapper → service`: 29 импортов в 10 файлах — `StreamMapper`, `ImportResult`, `OneCValues`;
- `service → mapper`: `PriceListTransferAssembler → PriceListMapper`; вместе с первым ребром это цикл;
- `dto → repository`: `Mkb10Response` строится из `Mkb10Row`;
- `exception → dto`: исключения несут `ErrorResponse.Detail`.

Снятие цикла — перенос трёх классов из `service.onec` в `mapper`, затем `fail.on.cycles=true` (решение 1 PR #1).

Куда класть новые классы:
- `allowed.config` пуст, то есть `config` не может зависеть ни от одного пакета модуля. Там лежат только фабрики бинов из
  классов JDK, Spring и библиотек.
- `forbidden.config` запрещает Spring AMQP и клиент RabbitMQ. Фабрики контейнеров слушателей и программная регистрация точек
  приёма — в `service.integration`.

**Кандидаты в `exclude.classes`.** Новых нет: `*Repository*` и `PolicySpecifications` уже исключены, остальные тонкие адаптеры
покрыты тестами. Record-классы без логики — только поштучно и по решению владельца (п. 4). После Ш3 (PR #5) PIT контекстные
тесты не исполняет: тонкий адаптер, до которого дотягиваются только они, для PIT без покрытия — при касании unit-тест либо
`exclude.classes` по решению владельца (правило 8 набора).

---

## 8. Операционные задачи вне кода

- **Секреты.** Настоящих секретов в дереве и в истории (7 коммитов) нет, ротация не нужна.
  - Пароли в `application.yml`, `docker-compose.yml` и тестах — плейсхолдеры локального контура.
  - `storepass` в `certs/import-ca.sh` — общеизвестный пароль JDK.
  - Проверить прежний репозиторий, откуда пришёл код (коммиты вроде `9e86cc9` здесь не видны): не коммитился ли там пароль
    брокера 1С.
- **ПДн и внутренние адреса во внешнем контуре.**
  - Пример сообщения в ТЗ-01 (стр. 2) и его копия среди `KDMS-*` содержат e-mail на публичном домене, дату рождения, номер
    полиса и GUID из живой 1С — похоже на реальные данные. Тестовые фикстуры скопировали из ТЗ-1 и ТЗ-2 номер полиса и
    реквизиты договора.
  - Во внешнем контуре лежат внутренние хосты, IP и домен компании: PDF `project_files/` (в том числе `ordering/`) и
    `contract-service/CLAUDE.md` (хост и логин тестового брокера).
  - Решения человека:
    1. запросить у 1С синтетический пример ТЗ-01 и заменить оба PDF;
    2. фикстуры — на синтетику (план, Ш2);
    3. допустимы ли внутренние адреса в GitHub — решить с ИБ;
    4. выключить форки (`allow_forking` сейчас true).

    Удаление из HEAD историю не чистит, а переписывать историю агентам запрещено: это решение и работа владельца.
- **Запросы к 1С:УСК.**
  - Тип очередей (classic или quorum), delivery-limit, DLX, версия RabbitMQ. Если очереди quorum — поставить политику
    `delivery-limit=-1` или DLX с разбором до запуска приёма.
  - Бывают ли в телах BOM и `\u0000`.
  - Единица `creation-timestamp`.
  - ТЗ-8: полный ли набор контактов в сообщении.
  - ТЗ-9: смысл «финализировалась последней».
  - Округление цен с тремя знаками.
  - GUID видов обращения РВЛ (OQ-48).
  - Порядок первичной выгрузки.
  - Верное имя очереди ТЗ-2.
- **Мониторинг до метрик.** Метрик в сервисе нет, открыт только `/actuator/health`. До шага метрик (план, Ш24) нужны:
  - алерты по журналу: ERROR «потеряно» (нечитаемый конверт), WARN «не соответствует контракту», WARN «передачи прайсов не
    собрались», повторяющийся WARN «сырьё … не сохранено»;
  - периодический SQL-отчёт по `inbound_message`: FAILED, SKIPPED, BUFFERED старше часа, RETRY, давность последнего PROCESSED
    как признак зависшего планировщика.

  Исчерпание лестницы повторов в журнал не пишется: только статус FAILED.
- **Эксплуатация контура.**
  - Одна реплика и выкатка без перекрытия (Recreate): выборка сырья без блокировки, порядок держится на одном потребителе.
  - Сетевая политика: к `/internal/v1` — только сервисы Заявок и Договоров. Межсервисной аутентификации в системе не будет
    (решение владельца 2026-09-30), а ручки отдают ПДн: ФИО, дату рождения, e-mail. Поэтому сетевая политика — единственная
    защита, её нужно проверить на контуре. К `/actuator/health` — только kubelet.
  - Маскирование query для `/internal/v1/policies*` в ingress и mesh: ФИО едет в строке GET. Access-лог Tomcat без `%r` и
    `%q`.
  - `terminationGracePeriodSeconds` больше 30 с: 30 с на фазу завершения плюс пауза слушателя.
  - Регламент первичной загрузки: слушатели запущены до выгрузки из 1С, 1С выгружает потоки по порядку ТЗ-7/4 → 3 → 8 → 5 →
    10 → 9 → 6 → 2 → 1 и ждёт нашего разбора. Иначе дети массово уходят в SKIPPED, а автоматического доразбора нет.
  - Сверить `appeal_types` контура с наименованиями видов обращения РВЛ в конфигурации: иначе R-01 и R-03 пустые, признак —
    WARN на старте.
- **ИБ.**
  - Межсервисной аутентификации не будет (ОВ-10 = ОВС-01 закрыт решением владельца). Исключение — возможно, внешний сервис
    оплаты, но это не наша система.
  - Открыты ПДн в R-05 и события Ordering в cvm (ОВД-05).
- **GitHub.** Тариф Pro и ruleset (раздел 3). Выровнять Network access и переменные двух окружений (раздел 1).

---

## 9. Перенос в рабочий контур

**Что переносится и что нет.**
- **Не переносятся:** `gauntlet/`, `.claude/`, корневой `CLAUDE.md`, `.github/`, `data-reference/src/acceptanceTest/`,
  `project_files/`, `contract-service/project_files/`, `docs/gauntlet-*`, а также 5 файлов `.DS_Store` (после их удаления в
  плане, Ш1, — в список удалённых).
- **Проверено.** Сборка модуля без них зелёная. Проверка шла в копии без этих каталогов, без `CLAUDE_CODE_REMOTE`, с Maven
  Central вместо Nexus: `./gradlew build -PpublicRepositories=false`, 155 тестов, 0 падений, 0 пропусков. Портал плагинов,
  git-зависимости и прямые URL сборке не нужны. PIT, Cucumber, ArchUnit, JaCoCo, Checkstyle и PMD в контуре не скачиваются.
- **Правила data-reference.** В контуре единственный источник контракта 1С — правила из `contract-service/CLAUDE.md` (их
  переносят в `data-reference/CLAUDE.md`, решение 2 PR #1). Нужно решить, переносится ли этот файл: сейчас «Рабочий контур» в
  `gauntlet/CLAUDE.md` запрещает только корневой.

**Проверить в Nexus до переноса.**
1. 312 модулей из `docs/gauntlet-setup-rvl-online-nexus.txt`: 180 с jar, 132 только POM (родительские POM, маркер плагина Spring
   Boot 4.0.7, 43 BOM из импорта `spring-boot-dependencies`). Версии из BOM совпадают с таблицей «Источников документации»;
   исключение — JUnit 6.1.3 в приёмке, но приёмка в контур не идёт.
2. Дистрибутив `gradle-8.14.3-bin.zip`: зеркало для `distributionUrl` и аргумента `GRADLE_DISTRIBUTION_URL`.
3. Образы:
   - `eclipse-temurin:21-jdk` и `21-jre` (Dockerfile);
   - `postgres:16.10-alpine`, `rabbitmq:4.1-alpine`, `testcontainers/ryuk:0.14.0` (тесты через
     `TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX`);
   - `rabbitmq:4.1-management-alpine` (только локальный compose).
4. Будущие зависимости плана — отдельно, до начала шага:
   - `io.micrometer:micrometer-registry-prometheus` (версия из BOM Boot 4.0.7, Micrometer 1.16.6);
   - провайдер кэша;
   - для contract-service — PDF-библиотека, а на этапе оплаты — клиент токенов для Ordering, если он понадобится.

**Агент сборки контура.** Нужен JDK 21: Gradle 8.14.3 не запускается на JDK 25, скачать тулчейн сам не сможет. BuildKit
обязателен для Dockerfile. Тестам нужен Docker с префиксом реестра контура либо выделенные тестовые база и брокер через
`TEST_POSTGRES_URL` и `TEST_RABBITMQ_URL`: `DatabaseCleaner` очищает все таблицы. Решение 7 PR #1: CI контура пока не
уведомляем.

**Переменные окружения сервиса.**

| Переменная | Умолчание в коде | В контуре |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/data_reference` | обязательна |
| `DB_USERNAME`, `DB_PASSWORD` | плейсхолдеры локального контура | обязательны, пароль — секрет развёртывания |
| `RABBITMQ_HOST` | `localhost` | обязательна |
| `RABBITMQ_PORT` | `5672` | `5671` при TLS: иначе TLS уйдёт на 5672 |
| `RABBITMQ_VHOST` | `/` | по брокеру 1С |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | плейсхолдеры | обязательны, пароль — секрет |
| `RABBITMQ_SSL_ENABLED` | `false` | `true` |
| `RABBITMQ_SSL_VALIDATE_CERTIFICATE`, `RABBITMQ_SSL_VERIFY_HOSTNAME` | `false` | `true`; доверие сертификату брокера — через `certs/` |
| `RABBITMQ_LISTENER_AUTO_STARTUP` | `false` | **обязательно `true`**, иначе сервис 1С не читает |
| `ONEC_QUEUE_*` (11 шт.) | имена тестового брокера `1c-usk_to_rvl-online_…` | сверить со списком брокера контура; неверное имя роняет старт |
| `SPRING_LIQUIBASE_ANALYTICS_ENABLED` | не задано | `false` до переноса Ш1 |
| `JDK_JAVA_OPTIONS` | `-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError` (образ) | по необходимости |

Аргументы сборки образа: `JDK_IMAGE`, `JRE_IMAGE`, `GRADLE_DISTRIBUTION_URL`, `PUBLIC_REPOSITORIES` (по умолчанию `false` =
Nexus), `JAR_SOURCE`.

В документе 1С переменные брокера названы `RABBITMQ_1C_*`, а сервис читает `RABBITMQ_*`: в манифестах — имена из
`application.yml`.

Значения `projectx.onec.contract.rvl-activity-id` (GUID вида деятельности РВЛ) и `dms-product-type` зашиты в `application.yml`
значениями тестового контура, без переменной. Проверить, что это значения рабочего контура. Переопределяются ослабленным
связыванием: `PROJECTX_ONEC_CONTRACT_RVLACTIVITYID` и т. п.

**База.** Миграции идут при старте под пользователем приложения. Ему нужны DDL на схему и CREATE на базу (`pg_trgm`, changeSet
011), либо `pg_trgm` заранее ставит администратор БД.

**Как переносить.** PR за PR в порядке мержа. Каждый PR плана даёт в отчёте «Перенос в контур» новые зависимости с
координатами, удалённые и переименованные файлы, новые переменные и изменённые умолчания. Удалённые файлы в контуре удалять
вручную, иначе останутся дубли классов и бинов. Первый такой случай — перенос `StreamMapper`, `ImportResult`, `OneCValues` в
пакет `mapper`.
