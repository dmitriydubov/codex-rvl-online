# Подключение модуля к gauntlet

Набор в этом репозитории уже установлен (протокол `start`, 2026-09-30; охраняемые модули — `data-reference` и,
с 2026-10-12, `contract-service`). Эта инструкция — для следующих модулей со своей Gradle-сборкой. Она правит
`gauntlet/`, поэтому выполняется **в админ-окружении** (`GAUNTLET_GUARD=off`, см. `README.md`, раздел 5) по
решению владельца, либо человек вносит правки на GitHub. Цель: после неё `./gradlew gauntlet` в новом модуле
зелёный на его текущем коде, в `data-reference` ничего не изменилось, PR открыт. Бизнес-код и существующие
тесты не менять: всё, что найдено про старый код, — в отчёт.

## 0. Какие сборки охраняет gauntlet
- Модуль — каталог с собственными `settings.gradle`, `build.gradle` и `gradlew`. Корневую сборку **не
  создавай**. Если у модуля нет своего `gradlew`, скопируй `gradlew`, `gradlew.bat` и `gradle/wrapper/`
  из `data-reference/`.
- Добавь модуль в `gauntlet/config/gauntlet.properties`: `module=data-reference,contract-service`. Хуки
  запускают Gradle в каждом изменённом модуле, CI — во всех; `src/` и `build/` — каждого модуля.
- Общие пороги — в `gauntlet/config/`; своё у модуля — в `gauntlet/config/<module>/`: `gauntlet.properties`
  там дополняет общий файл, `architecture.properties` (и при нужде `checkstyle.xml`, allowlist'ы) заменяет
  общий. Проверка: `cd <module> && ./gradlew gauntletInfo`.
- Модуль, перечисленный в `module`, но без оверлея, падает в `archTest` на шаблонном `base.package` — так
  и задумано: сначала политика модуля.

## 1. Разведка
Определи и запиши для отчёта:
- версию Spring Boot и DSL сборки, версии из `./gradlew dependencies`;
- корневой пакет сервиса (`@SpringBootApplication`) и его прямые подпакеты;
- какие подпакеты от каких зависят сейчас (импорты между ними, циклы);
- как интеграционные тесты поднимают контекст (Testcontainers? какие контейнеры?); пропускаются ли они
  без Docker (`@EnabledIf`, `disabledWithoutDocker`) — integrity это запрещает;
- правила модуля (его `CLAUDE.md`) и источники предметной области (`project_files/`).

## 2. `gauntlet/config/<module>/gauntlet.properties`
- `basePackage` — корневой пакет модуля; `acceptance.glue` — `<basePackage>.acceptance`.
- `exclude.classes` — ключ заменяет общий целиком: повтори маски по умолчанию (`*Application,*Config,
  *Configuration,*Repository*`) и добавь тонкие IO-адаптеры, до которых дотягиваются только интеграционные
  тесты. Классы с логикой не исключать; проверь, что маски не задевают такие классы (`*Properties` с
  методами, мапперы с правилами разбора). Комментарий перечисляет ровно то, что исключено. Маски — решение
  владельца.
- `module`, `baseRef`, `cucumber.version` и пороги — только в общем файле. Cucumber 8.0.1 подходит для
  Spring Boot 4.x: Gradle выравнивает JUnit в classpath приёмки до версии, нужной Cucumber.

## 3. `gauntlet/config/<module>/architecture.properties`
- `base.package` — корневой пакет модуля.
- `allowed.<подпакет>=…` для **каждого** прямого подпакета — так, как зависимости устроены сейчас (гейт
  зелёный на текущем коде; незаявленный подпакет — падение с перечислением).
- `forbidden.*` — только правила, которые выполняются уже сейчас.
- `fail.on.cycles=true`, если циклов нет ни между компонентами, ни между подпакетами; есть — `false` с комментарием, какой цикл, и задача на его разрыв.
- В отчёт: рёбра против направления, циклы, снятые `forbidden`. Ужесточать — решение владельца.

## 4. Сборка модуля (рабочий контур без набора)
Скопируй схему `data-reference`:
- `settings.gradle` — переключатель репозиториев: свойство `publicRepositories` (по умолчанию Nexus контура);
  в облаке его выставляют хуки набора, в GitHub Actions — `env` в `gauntlet.yml`; закомментированный вариант «только Nexus»; флаг
  `gradle.ext.gauntletEnabled` — набор подключается, только когда `../gauntlet/gauntlet.gradle` рядом и
  репозитории публичные.
- `build.gradle` — плагин PIT в `buildscript {}` (только при `gradle.ext.gauntletEnabled`, с портала
  плагинов: в Nexus контура его нет) и последним блоком
  `if (gradle.ext.gauntletEnabled) { apply plugin: 'info.solidsoft.pitest'; apply from: '../gauntlet/gauntlet.gradle' }`.
  В `acceptanceTestImplementation` ничего не добавляй: набор кладёт туда Cucumber, только если
  конфигурация пуста, а зависимости тестов приёмка наследует от `testImplementation`.
- Проверка режима контура: копия модуля без `gauntlet/` и `src/acceptanceTest`, без `CLAUDE_CODE_REMOTE`,
  адрес Nexus подменён на `https://repo.maven.apache.org/maven2/` как на его прокси, чистый
  `GRADLE_USER_HOME` → `./gradlew build` зелёный и не ходит на портал плагинов.

## 5. Приёмочные тесты
- `src/acceptanceTest/java/<пакет из acceptance.glue>/CucumberSpringConfiguration.java` — по образцу
  `gauntlet/templates/…` и `data-reference`: свои Testcontainers (классы из `src/test` приёмке не видны),
  расписания отодвинуты, AMQP выключен, если сценарии его не используют.
- Каталог `src/acceptanceTest/resources/features/` создай пустым (`.gitkeep`). Пример фичи из
  `templates/` не копируй. Коммит с `.gitkeep` замораживает фичи в ветке подключения — это безвредно
  и снимается после мержа.

## 6. Первый прогон
Docker: `nohup dockerd --host=unix:///var/run/docker.sock > /tmp/dockerd.log 2>&1 &`, дождись `docker info`.
`cd <module> && ./gradlew gauntlet --continue`. Изменённых main-классов в ветке нет, поэтому Checkstyle,
CRAP, DRY, PIT и Gherkin-мутация проверят пустое множество; реально прогоняются `integrityCheck`, `test` и
`archTest`.
- `integrityCheck` нашёл пропуски тестов (`@Disabled`, `@EnabledIf`, `assume*`, `disabledWithoutDocker`) →
  по решению владельца либо переделать (без Docker тест падает, а не пропускается — так сделано в
  `data-reference`), либо записать в `gauntlet/config/<module>/integrity-allowlist.txt` с причиной
  `pre-existing at install`. В отчёт — каждое.
- `archTest` перечислил незаявленные подпакеты или рёбра → шаг 3.
- `test` красный по существу — в отчёт, не чини. Maven Central ответил 429 — повтори через 20–60 секунд.
- `gauntletScope` не находит merge-base → `git fetch origin +refs/heads/main:refs/remotes/origin/main`
  (после `git fetch --unshallow origin` в shallow-клоне).
Повторяй до зелёного. Затем `./gradlew gauntlet --continue` в `data-reference` — тоже зелёный.

## 7. Хуки
Уже установлены: `.claude/settings.json`, `.claude/agents/`, `.github/workflows/gauntlet.yml` и
`@gauntlet/CLAUDE.md` в корневом `CLAUDE.md`. `install.sh` не запускай — его источники (`gauntlet/claude`,
`gauntlet/ci`) удалены при установке. Проверь `python3 gauntlet/hooks/selftest.py` — `0 failed`.
Допиши модуль в таблицу «Репозиторий» в `gauntlet/CLAUDE.md` и в корневой `CLAUDE.md`, добавь строки
его стека в «Источники документации», если стек отличается.

## 8. Коммит, push, PR
Коммит `chore: guard <module> with the gauntlet`, push своей ветки, PR с отчётом:
- модуль, версии (Boot, Cucumber, JUnit), `basePackage`/`glue`;
- `exclude.classes` и почему;
- архитектура: компоненты, рёбра против направления, циклы, снятые `forbidden`;
- integrity: что переделано или внесено в allowlist и почему;
- перенос в контур: изменённые файлы сборки модуля, новые зависимости и плагины с координатами;
- что не удалось прогнать и почему.

Человек прочитает отчёт и смержит PR — это и есть утверждение подключения.
