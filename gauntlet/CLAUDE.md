# Gauntlet: как здесь ведётся разработка

Код пишет агент. Человек код не читает: он читает спецификацию (Gherkin), отчёт о гейтах и изменения
самого набора `gauntlet/`. Итоговое решение по каждой задаче принимает человек — merge PR в GitHub.
Работа считается сделанной только когда `./gradlew gauntlet` зелёный, ветка запушена и PR открыт.
Никогда не сообщай об успехе, не выполнив проверку.

## Репозиторий
Монорепозиторий сервисов, за которые отвечает владелец; у каждого сервиса своя Gradle-сборка
в своём каталоге, корневой сборки нет и не будет. Набор `gauntlet/` один на все модули и работает
только во внешнем контуре — в облаке Claude Code и в GitHub (см. «Правила владельца», «Рабочий контур»).

| Каталог | Что это | Под гейтами |
|---|---|---|
| `data-reference/` | сервис справочных данных: справочники и полисы из 1С:УСК (RabbitMQ), REST-чтение для соседних сервисов; пакет `ru.projectx.datareference` | да (`module=data-reference`) |
| `contract-service/` | сервис Договоров, он же бэкенд ЛК застрахованного; пока без кода — только `CLAUDE.md` и документы | нет: подключается, когда появится его сборка (`gauntlet/INSTALL.md`, решение владельца) |
| `project_files/`, `contract-service/project_files/` | ТЗ, схемы и структуры данных — источники предметной области (раздел «Источники документации») | — |

Сервис Заявок (он же сервис HR), BFF и фронтенд пишут другие разработчики в своих репозиториях.
Правила и предметная область `data-reference` (контракт 1С:УСК, пакеты, транзакции, ПДн, тесты) описаны
в `contract-service/CLAUDE.md` — прочитай его перед задачей в `data-reference`. Он главный по предметной
области и правилам кода. По процессу, веткам, проверкам перед коммитом, репозиториям сборки, тестовой
инфраструктуре и лимитам размера главный этот файл: там, где тот расходится (фиксированная ветка разработки,
`./gradlew build` как проверка, метод до 40 строк), действует gauntlet.

## Гейты

| Команда | Что проверяет |
|---|---|
| `./gradlew gauntletFast` | integrity (нет отключённых тестов и подавлений) → Checkstyle (размер, структура, CC ≤ 3) → PMD (антипаттерны) → unit-тесты → архитектура (ArchUnit) → приёмка (Cucumber, ни одного skipped) → CRAP ≤ 8 → дубли |
| `./gradlew mutationGate` | PIT по изменённым классам: каждый покрытый мутант убит (test strength 100 %), покрытие строк мутируемых классов ≥ 80 % |
| `./gradlew gherkinMutation` | каждое значение из Examples мутируется по очереди; какой-то сценарий обязан упасть |
| `./gradlew gauntlet` | всё вместе |

Область проверки — diff относительно базовой ветки плюс незакоммиченные файлы (`baseRef` в
`gauntlet/config/gauntlet.properties`). Охраняемые сборки перечислены в `module` (сейчас `data-reference`):
`./gradlew …` запускай из каталога модуля (`cd data-reference && ./gradlew …`), `src/` и `build/` — его,
фичи — в `data-reference/src/acceptanceTest/resources/features/`, настройки модуля — в
`gauntlet/config/data-reference/` (`./gradlew gauntletInfo` покажет, что подхватилось), а `gauntlet/`
остаётся в корне. Задача, затрагивающая два модуля, — одна спецификация (фичи в обоих) и один PR. Stop-хук сам
запускает `gauntletFast` в каждом модуле, где ход менял код, и возвращает тебе падения. `--continue`
показывает все красные гейты за один прогон. Отчёты: `<module>/build/reports/`.

**Среда облачной сессии.**
- Тесты с базой и брокером (`@IntegrationTest`, `@AmqpIntegrationTest`, приёмка) поднимают PostgreSQL и
  RabbitMQ через Testcontainers. Без Docker они **падают, а не пропускаются** — так задумано. В облачной
  VM демон поднимает SessionStart-хук (`dockerd` напрямую: `service docker start` там падает на ulimit),
  строка статуса показывает `docker: up`. Если там другое или контейнер сессии перезапускался:
  `nohup dockerd --host=unix:///var/run/docker.sock > /tmp/dockerd.log 2>&1 &` и дождись `docker info`.
  Красный Stop-хук из-за «Could not find a valid Docker environment» — это окружение, а не код.
- Maven Central через прокси иногда отвечает 429 (Too Many Requests) на пачку новых артефактов: повтори
  через 20–60 секунд, к коду это отношения не имеет.
- Набор подключается к сборке только при публичных репозиториях: в облаке их включает
  `CLAUDE_CODE_REMOTE=true`, в GitHub Actions — `GITHUB_ACTIONS=true`. В локальной сессии Claude Code задай
  `ORG_GRADLE_PROJECT_publicRepositories=true`, иначе сборка пойдёт в Nexus контура, а Stop-хук не найдёт
  задач набора.
- Не заканчивай ход, пока идёт фоновый прогон гейтов: Stop-хук запустит свой в тот же `build/` и будет ждать
  его конца (блокировка `.gradle/gauntlet-build.lock`, до 1500 с), а при более долгом прогоне упадёт по таймауту.

## Процесс для задачи, меняющей поведение сервиса

Триггер — сообщение пользователя: новая фича, багфикс, изменение контракта или сообщения. Для правок,
не меняющих поведение (рефакторинг, зависимости, логи, инфраструктура), шаги 2–4 пропускаются,
остальное так же.

1. **Контекст.** Прочитай `gauntlet/config/gauntlet.properties`, настройки модуля в
   `gauntlet/config/<module>/` (`gauntlet.properties`, `architecture.properties`), правила модуля
   (для `data-reference` — `contract-service/CLAUDE.md`) и существующие фичи по теме в
   `<module>/src/acceptanceTest/resources/features/`.
2. **Спецификация.** Делегируй сабагенту `specifier`: запрос дословно, релевантные фичи, ограничения из
   сообщения. Он вернёт изменённые файлы, по строке на сценарий и открытые вопросы. Спецификации пишутся
   на русском (`# language: ru`); в рабочий контур они не переносятся.
3. **Утверждение — остановись.** Покажи пользователю пути файлов, по одной строке на сценарий, вопросы и
   допущения specifier'а. Спроси, утверждает ли он спецификацию, и закончи ход. До ответа — никакого кода
   и никаких коммитов: незакоммиченные фичи — черновик, который человек ещё может править (Stop-хук их не
   гоняет). Stop-хук облачной платформы (`~/.claude/stop-hook-git-check.sh`, не gauntlet) один раз
   заблокирует остановку и попросит закоммитить и запушить изменения — черновик фич не коммить: ответь
   одной строкой, что ждёшь утверждения, и остановись повторно, вторая остановка проходит. Решения,
   нужные от человека, выводи нумерованным списком, чтобы он ответил «1) да 2) да …». Просят изменений —
   снова к `specifier`.
   Если в `gauntlet.properties` стоит `spec.approval=pr`, не останавливайся: человек прочитает Gherkin в PR.
4. **Заморозка.** После «да» закоммить только фичи отдельным коммитом `spec: <что описано>` и сразу
   запушить ветку (`git push -u origin HEAD`): с этого момента спецификация видна человеку на GitHub,
   а хук замораживает её для всех агентов — изменить её может только человек. Если по ходу реализации
   спецификация окажется противоречивой или невыполнимой — остановись и опиши проблему; не подгоняй
   ни код, ни тесты, ни спецификацию.
5. **Реализация (ты).** TDD маленькими шагами по правилам ниже; step-классы в `src/acceptanceTest/java`.
   `./gradlew gauntletFast --continue` до зелёного.
6. **Закалка.** Сабагент `hardener` (название фичи и список изменённых классов): убивает выживших мутантов
   и пустые step-определения, правит только тестовый код. Его предложения по продакшен-коду применяй
   сам, затем снова `gauntletFast`.
7. **Ревью.** Сабагент `reviewer` (базовая ветка, фичи, список изменённых файлов). Исправь все
   blocker/major, повтори прогон, верни результат ревьюеру. Не больше 2 раундов. Рекомендация
   противоречит утверждённой спецификации — вопрос человеку.
8. **Финал.** `./gradlew gauntlet --continue` зелёный → коммит → `git push` своей ветки → открой PR
   (`gh pr create`, в описании — отчёт ниже; если не получилось, скажи об этом: пользователь нажмёт
   Create PR сам). Тот же отчёт — в чат. **Никогда не мержи** и не проси мержить за тебя: merge — это
   утверждение результата человеком. Одна задача — одна ветка и одна сессия: следующую задачу человек
   начинает новой сессией от основной ветки.

### Отчёт (в PR и в чат)
- **Гейты**: каждый со статусом; max CRAP; мутанты — убито / выжило / без покрытия; Gherkin-мутанты.
- **Спецификация**: файлы и сценарии — то единственное, что человек читает.
- **Ревьюер**: вердикт, что исправлено.
- **Сверка по документации**: по строке на решение, которое опирается на поведение фреймворка, библиотеки
  или протокола: утверждение → источник (URL или jar и класс) → версия; отдельно — что проверено только
  по байткоду и почему.
- **Перенос в контур**: новые зависимости и плагины с координатами (group:artifact:version) для проверки
  в корпоративном реестре; удалённые и переименованные файлы и классы; новые переменные окружения и значения
  по умолчанию (см. «Правила владельца»).
- **Нужно решение человека** — нумерованным списком: эквивалентные мутанты, предлагаемые исключения,
  вопросы к спецификации.
- **Оценка**: трудозатраты задачи для Jira против факта, с причиной расхождения.
- **Остаточные риски**.

### Правила владельца
- **Задача для Jira** (тема, описание с критериями приёмки, трудозатраты) пишется в чат до начала работы.
  Если её уже написал координатор и она пришла в промпте, заново не пишется.
- **Решения человека — нумерованным списком** в любой остановке и в отчёте; ответ «1) да 2) нет …»
  записывается в описание PR.
- **Лимит токенов.** Если в сессию пришло предупреждение о 99 % пятичасового или недельного лимита либо
  человек написал «пауза», работа ставится на паузу, 100 % не ждать: закоммить и запушь свою ветку, напиши
  статус (что сделано, что красное, что дальше). Красный гейт на паузе не чинится: отвечай Stop-хуку одной
  строкой «пауза по лимиту, работа не сделана». Возобновляет человек или координатор сообщением в сессию.
- **Рабочий контур** — закрытая сеть компании, где живут сервисы. Там нет GitHub, интернета и Claude:
  облачные сессии Claude Code и GitHub — внешний контур. Зависимости и образы в контуре берутся только из
  корпоративного Nexus, с ним владелец работает сам, вручную; правки переносит тоже вручную, PR за PR
  в порядке мержа. Из сессии Nexus закрыт, и обращаться к нему не нужно: здесь — только публичные адреса
  (Maven Central, портал плагинов Gradle, Docker Hub, официальная документация).
  - Сборка сама выбирает репозитории (`settings.gradle` модуля): в облаке (`CLAUDE_CODE_REMOTE=true`) и
    в GitHub Actions — публичные, иначе — Nexus; набор gauntlet подключается, только когда рядом лежит
    `gauntlet/` и репозитории публичные. Переключатель и закомментированный вариант «только Nexus» не трогай.
  - В контур не переносятся `gauntlet/`, `.claude/`, корневой `CLAUDE.md`, `.github/`,
    `<module>/src/acceptanceTest/` и `project_files/`. Продакшен-код и unit-тесты от них не зависят,
    а сборка модуля без них зелёная.
  - Каждая новая зависимость и плагин — в отчёт с координатами `group:artifact:version`: владелец проверит
    их в Nexus до переноса. Известно, что Nexus контура отдаёт `spring-boot-starter-web`, но не
    `spring-boot-starter-webmvc` и `spring-boot-starter-webmvc-test` (см. `data-reference/build.gradle`);
    такие ограничения не обходить.
  - Удалённые и переименованные файлы — отдельным списком в отчёте: при ручном переносе старые файлы
    удаляются, иначе останутся дубли классов и бинов. Переносы и переименования — только когда они нужны
    задаче.
  - Новые переменные окружения и изменённые значения по умолчанию — в отчёт.
  - Реестр образов в коде не хардкодится: в облаке образы Testcontainers идут с Docker Hub, в контуре
    префикс реестра задаёт его CI (`TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX`).

## Жёсткие правила
1. **Не трогай gauntlet.** `gauntlet/`, `.claude/`, `CLAUDE.md` и `.github/workflows/gauntlet.yml`
   принадлежат человеку (хук блокирует правки). Если проверка выглядит неверной — остановись и объясни,
   не обходи её.
2. **Никогда не ослабляй проверку ради зелёного**: никаких `@Disabled`/`@Ignore`/assumptions, `@Generated`/
   `@DoNotMutate`, `CHECKSTYLE:OFF`/`NOPMD`, фильтров тестов, `enabled = false` или переконфигурации гейтов
   в build-скриптах, удалённых или ослабленных assert'ов. `integrityCheck` это ловит; человек видит каждую
   попытку в diff.
3. **Фичи — это спецификация** (`<module>/src/acceptanceTest/resources/features/`, на русском,
   `# language: ru`). Пишет их только сабагент `specifier`, утверждает только человек — в чате. Код должен удовлетворять спецификации, а не наоборот.
4. **TDD.** На каждое поведение: сфокусированный падающий unit-тест, который упал бы на правдоподобной
   неверной реализации → минимум кода → рефакторинг. Приёмочные тесты не заменяют unit-тесты.
5. **Маленькие функции.** Цикломатическая сложность ниже 4 (Checkstyle: CC ≤ 3, NPath ≤ 20); CRAP-гейт
   падает выше 8. Дели методы со смешанными обязанностями; извлечённый метод сам владеет своими входами —
   не передавай boolean, который вызывающий уже знал. Одна задача на класс и файл (Checkstyle: метод
   ≤ 25 строк кода без пустых и комментариев, ≤ 4 параметров, файл ≤ 300 строк, fan-out ≤ 20; PMD:
   ≤ 12 методов в классе, cognitive complexity < 10). Лимиты набора строже правил проекта и действуют
   вместо них. Область гейтов — изменённый файл целиком: большой файл сначала приводится к лимитам
   отдельной задачей без изменения поведения.
6. **Зависимости направлены к политике**, как объявлено в политике модуля
   `gauntlet/config/<module>/architecture.properties` (`./gradlew gauntletInfo` покажет, какой файл
   действует): без новых циклов и рёбер против направления, домен и прикладная логика свободны от
   транспорта и персистентности. Исключения, которые политика объявляет явно (у `data-reference` — цикл
   `mapper ↔ service`), — долг, а не образец: новый код их не расширяет. Адаптеры вызывают прикладную
   логику и никогда не дублируют её правило.
7. **Step-определения используют каждый параметр** и проверяют наблюдаемое поведение (HTTP-ответ,
   сохранённое состояние, отправленное сообщение). Пустой step проявится как выживший Gherkin-мутант.
8. Помечай `@Tag("no-mutate")` только медленные тесты с полным контекстом или Testcontainers: они
   идут в `test`, но PIT их пропускает, и код, покрытый только ими, считается непокрытым. Держи логику
   в обычных unit-тестируемых классах; тонкие IO-адаптеры, до которых дотягиваются только интеграционные
   тесты, человек исключает через `exclude.classes`.
9. Секретам, персональным и другим чувствительным данным не место в логах, тестовых фикстурах и текстах исключений.
10. **Git.** Коммиты говорят, что изменилось и зачем, и заканчиваются `By <role>.` (coder, specifier,
    hardener…). Только новые коммиты в своей ветке: никаких `--amend`, `rebase`, `revert`, `reset` на другую
    ревизию, `checkout -B`, `push --force`, push в базовую ветку, `--no-verify` (хук блокирует). Ошибку
    исправляй новым коммитом.
11. **SOLID — по каждому принципу, не «в целом».** SRP: у класса одна причина меняться — оркестрацию,
    маппинг и IO не смешивай в одном классе. OCP: новый случай — новый класс или стратегия, а не ещё одна
    ветка `if`/`switch` по типу. LSP: реализация не ужесточает предусловия и не бросает
    `UnsupportedOperationException` вместо обещанного поведения. ISP: интерфейсы узкие — клиент не зависит
    от методов, которые не вызывает. DIP: сервис зависит от абстракции (интерфейс, порт, `List`/`Map`),
    конкретный класс выбирает конфигурация или адаптер. Измеримую часть ловит `pmdGate` (god class,
    число методов, loose coupling), остальное — `reviewer`, который называет принцип и место.
12. **Сверяйся с официальной документацией, а не с памятью — строго и на каждом шаге.** Стек — Spring
    Boot 4.0 / Framework 7.0 / Java 21 / Hibernate 7.2 / Jackson 3: многое из «привычного» устарело. Правило действует для всех ролей:
    `specifier` сверяет семантику протоколов и фреймворка, на которую опираются сценарии (подтверждения
    брокера, повторная доставка, коды HTTP); кодер — каждый API, аннотацию, свойство, поведение потоков,
    транзакций и таймаутов до того, как написать код; `hardener` — тестовые библиотеки; `reviewer` —
    что сверка была и источник подходит к версии. Версии — из BOM проекта (`./gradlew dependencies`),
    документация — той же версии, не «latest». Источники — только из раздела «Источники документации»
    ниже. Никогда не выдумывай имя свойства, атрибут аннотации, метод или поведение: нет подтверждения
    ни в одном источнике — не используй, остановись и опиши. Офлайн-источники, которые есть всегда:
    имена, типы и значения по умолчанию свойств Spring Boot — `META-INF/spring-configuration-metadata.json`
    внутри jar в кэше Gradle (`~/.gradle/caches/modules-2/files-2.1/`), сигнатуры и поведение —
    `javap -p -c -cp <jar>`. Сайт недоступен — запасные пути раздела «Источники документации» по порядку:
    javadoc-jar и sources-jar нужной версии, исходники документации по тегу, jar в кэше Gradle; каждый случай
    — в отчёт.
13. **Антипаттерны запрещены**, не только те, что ловит `pmdGate`: god class и класс-«сервис» со всем
    подряд, логика в листенере/контроллере/планировщике вместо домена, магические строки и числа,
    boolean-параметр-переключатель, `null` вместо `Optional` или пустой коллекции, проглоченное
    исключение, `catch (Exception)` вне границы системы (листенер, планировщик, внешний клиент) или без
    решения (лог + reject/requeue/повтор/пробросить), мутабельное статическое состояние, самописный
    механизм там, где у Spring/JDK есть стандартный (retry, транзакции, валидация, конвертация, планирование, кэш).
14. **Оптимальный, безопасный и отказоустойчивый путь.** Перед реализацией назови 2–3 варианта и выбери
    с обоснованием (кратко — в отчёт). Для каждого нового пути продумай: идемпотентность обработчика
    (повторная доставка сообщения, повторный HTTP-запрос), границы транзакций (транзакция не пересекает
    вызов внешней системы), таймауты и ограниченные ретраи внешних вызовов, судьбу отказа (dead letter,
    outbox, компенсация), потокобезопасность, валидацию входа на границе, наблюдаемость (структурный лог
    без ПДн, метрика, correlation id). Кэширование закладывай сразу там, где данные читаются многократно
    и меняются редко (справочники, реестры подразделений, конфигурация внешних систем, результаты дорогих
    запросов): только стандартный Spring Cache (`@Cacheable`/`@CacheEvict`, провайдер из конфигурации),
    у каждого кэша явно заданы ключ, TTL, предел размера и инвалидация при изменении источника. Без кэша
    там, где нужна свежесть (статусы заявок, всё, что участвует в принятии решения). Самописные
    `static Map`-кэши запрещены.
15. **Читаемый код.** Имена из предметной области (заказ, счёт, клиент — не data/info/tmp);
    метод делает ровно то, что говорит имя, и держит один уровень абстракции; guard clause вместо
    вложенности; комментарий, объясняющий плохой код, — повод переписать код; Javadoc на русском для
    публичных классов и неочевидных методов; `var` — только когда тип виден из правой части. Класс должен
    читаться сверху вниз как рассказ.

## Источники документации
Единственные допустимые источники для правила 12. Версии — те, что разрешает сборка `data-reference`
(`./gradlew dependencies`, Spring Boot 4.0.7); при обновлении BOM сверяй по новой версии. `<версия>` —
версия артефакта из `./gradlew dependencies`. Адреса проверены запросом из облачной сессии 2026-09-30;
пометка «закрыт» — домен не пропускает сетевая политика окружения (или сайт отвечает 403), тогда сразу
запасной путь.

**Предметная область** (для `specifier`, кодера и `reviewer`; бизнес-правила берутся только отсюда, не выдумываются):

| Что | Источник |
|---|---|
| Правила и контракт `data-reference` | `contract-service/CLAUDE.md`: «Контракт 1С:УСК», «Соседи по контуру», «Структура пакетов», «Как пишем код» |
| Потоки 1С:УСК ТЗ-01…ТЗ-10, структура БД 1С | `project_files/1C_data/*.pdf`, `contract-service/project_files/1C_data/*.pdf` (ТЗ-5 v2.0 — договоры ЛПУ) |
| REST `data-reference` | `project_files/ТЗ API Data-reference.pdf` |
| Точки интеграции, схемы процесса | `project_files/Точки интеграции РВЛ и 1С_УСК.pdf`, `project_files/Точки интеграций сервис Договоров и Заявок.pdf`, `project_files/RVL on-line. Схемы процесса и флоу.pdf` |
| Структуры данных, ФТ, вопросы и ответы | `project_files/Структура БД v.0.2.pdf`, `project_files/Структура данных (Сервис договоров).pdf`, `project_files/Структура данных Сервис Заявок v.2.pdf`, `project_files/Сервис договоров_синхронные эндпоинты.pdf`, `project_files/ФТ к модулю РВЛ на базе платформы B2B-портала.pdf`, `project_files/Вопросы и ответы по РВЛ.pdf` |
| ЛК застрахованного (`contract-service`) | `contract-service/project_files/` (требования к лендингу и ЛК, примеры документов) |

PDF читаются инструментом Read (страницами). Документы противоречат друг другу или коду — остановись и
спроси владельца, не выбирай сам.

**Технологии** (версия из сборки → источник той же версии):

| Что | Официальный источник | Если сайт недоступен |
|---|---|---|
| Java SE 21 | API: docs.oracle.com/en/java/javase/21/docs/api/; JLS и JVMS: docs.oracle.com/javase/specs/ (se21) | `javap -p -c` на классах JDK |
| Spring Boot 4.0.7 | docs.spring.io/spring-boot/4.0/; API: docs.spring.io/spring-boot/4.0/api/java/; свойства: docs.spring.io/spring-boot/4.0/appendix/application-properties/; плагин Gradle: docs.spring.io/spring-boot/4.0/gradle-plugin/; миграция: вики github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide (из сессии закрыта, открывает человек) | `META-INF/spring-configuration-metadata.json` в jar, `javap` |
| Spring Framework 7.0.8 | docs.spring.io/spring-framework/reference/7.0/ (в т. ч. core/resilience.html — `@Retryable`, `RetryTemplate`, `@ConcurrencyLimit`, включаются `@EnableResilientMethods`; core/null-safety.html — JSpecify); Javadoc: docs.spring.io/spring-framework/docs/7.0.x/javadoc-api/; изменения: raw.githubusercontent.com/wiki/spring-projects/spring-framework/Spring-Framework-7.0-Release-Notes.md | javadoc-jar и sources-jar, `javap` |
| Spring AMQP 4.0.4 | docs.spring.io/spring-amqp/reference/4.0/; API: docs.spring.io/spring-amqp/docs/4.0.4/api/ | javadoc-jar, `javap` |
| Spring Data JPA 4.0.6 | docs.spring.io/spring-data/jpa/reference/4.0/; API: docs.spring.io/spring-data/jpa/reference/4.0/api/java/ | javadoc-jar, `javap` |
| Hibernate ORM 7.2.19 | docs.hibernate.org/orm/7.2/ (руководство: userguide/html_single/Hibernate_User_Guide.html) | javadoc-jar, `javap` |
| Hibernate Validator 9.0.1, Jakarta Validation 3.1 | docs.jboss.org/hibernate/validator/9.0/reference/en-US/html_single/; jakarta.ee/specifications/bean-validation/3.1/ | javadoc-jar |
| Jakarta Persistence 3.2 | jakarta.ee/specifications/persistence/3.2/ | javadoc-jar |
| Liquibase 5.0.3 | docs.liquibase.com | javadoc-jar и sources-jar `org.liquibase:liquibase-core` |
| PostgreSQL 16 (образ `postgres:16.10-alpine`), JDBC 42.7.11 | www.postgresql.org/docs/16/; jdbc.postgresql.org/documentation/ | исходник документации: raw.githubusercontent.com/postgres/postgres/REL_16_STABLE/doc/src/sgml/ |
| RabbitMQ 4.1 (образ `rabbitmq:4.1-alpine`): подтверждения, повторная доставка, DLX, аргументы очередей | www.rabbitmq.com/docs — **закрыт** (сайт отвечает 403 на страницы) | исходники той же документации: raw.githubusercontent.com/rabbitmq/rabbitmq-website/main/versioned_docs/version-4.1/<страница>.md (например, confirms.md) |
| RabbitMQ Java-клиент 5.27.1 | www.rabbitmq.com/client-libraries/java-api-guide; Javadoc rabbitmq.github.io/rabbitmq-java-client/ — **закрыт** | javadoc-jar `com.rabbitmq:amqp-client:5.27.1`; raw.githubusercontent.com/rabbitmq/rabbitmq-java-client/v5.27.1/ |
| Jackson 3.1 (`tools.jackson`) | javadoc.io/doc/tools.jackson.core/jackson-databind/<версия>; миграция: raw.githubusercontent.com/FasterXML/jackson/main/jackson3/MIGRATING_TO_JACKSON_3.md | javadoc-jar, `javap` |
| JSpecify 1.0 | jspecify.dev/docs/user-guide/ | javadoc-jar |
| Tomcat 11.0 (встроенный) | tomcat.apache.org/tomcat-11.0-doc/ | javadoc-jar |
| HikariCP 7.0.2 (пул соединений, версия из BOM Boot 4.0.7) | README по тегу: raw.githubusercontent.com/brettwooldridge/HikariCP/HikariCP-7.0.2/README.md (свойства пула, единицы и умолчания) | sources-jar и javadoc-jar `com.zaxxer:HikariCP:7.0.2` |
| SLF4J 2.0, Logback 1.5 | www.slf4j.org/manual.html, www.slf4j.org/api/; logback.qos.ch/manual/ | `javap` |
| Micrometer 1.16 | docs.micrometer.io/micrometer/reference/1.16/ | javadoc-jar, `javap` |
| JUnit 6.0.3 | docs.junit.org — **закрыт** | исходники руководства по тегу: raw.githubusercontent.com/junit-team/junit-framework/r6.0.3/documentation/modules/ROOT/pages/ (оглавление — …/modules/ROOT/nav.adoc); javadoc-jar |
| AssertJ 3.27 | assertj.github.io/doc/ | raw.githubusercontent.com/assertj/doc/main/src/docs/asciidoc/user-guide/ |
| Mockito 5.20 | Javadoc (он же руководство): javadoc.io/doc/org.mockito/mockito-core/5.20.0 (site.mockito.org закрыт) | javadoc-jar |
| Testcontainers 2.0.5 | java.testcontainers.org (модули: /modules/databases/postgres/, /modules/rabbitmq/) | raw.githubusercontent.com/testcontainers/testcontainers-java/2.0.5/docs/ |
| Awaitility 4.3 | руководство — вики: raw.githubusercontent.com/wiki/awaitility/awaitility/Usage.md | javadoc-jar |
| Cucumber-JVM 8.0.1 (приёмка) | cucumber.io/docs/ (Java: installation/java/); Gherkin на русском — cucumber.io/docs/gherkin/languages/ | javadoc-jar, raw.githubusercontent.com/cucumber/cucumber-jvm/v8.0.1/ |
| Gradle 8.14.3 | docs.gradle.org/8.14.3/userguide/; DSL: docs.gradle.org/8.14.3/dsl/ | нет |
| Docker Compose v5.1.1 (CLI) и спецификация compose-файла (`docker-compose.yml`) | docs.docker.com — **закрыт** | CLI — по тегу версии: raw.githubusercontent.com/docker/compose/v5.1.1/docs/reference/<команда>.md (compose.md, compose_up.md, compose_down.md …); файл — raw.githubusercontent.com/docker/docs/main/content/reference/compose-file/<раздел>.md (services.md, build.md, fragments.md) и raw.githubusercontent.com/compose-spec/compose-spec/main/<NN>-<раздел>.md; руководства — raw…/docker/docs/main/content/manuals/compose/…; у docker/docs и compose-spec тегов под версию нет — ветка main, поведение проверять `docker compose … config` |
| Dockerfile и BuildKit (`Dockerfile` модуля: `RUN --mount`, `FROM ${ARG}`) | docs.docker.com — **закрыт** | raw.githubusercontent.com/moby/buildkit/master/frontend/dockerfile/docs/reference.md; руководства — raw…/docker/docs/main/content/manuals/build/…; поведение проверять `docker build` локально |
| Гейты: PIT 1.22.1, gradle-pitest-plugin 1.19.0, ArchUnit 1.5.1, Checkstyle 12.3.1, PMD 7.27.0, JaCoCo 0.8.15 | pitest.org; gradle-pitest-plugin.solidsoft.info; www.archunit.org/userguide/; checkstyle.org; docs.pmd-code.org/pmd-doc-7.27.0/; www.jacoco.org/jacoco/trunk/doc/ | по тегу версии на raw.githubusercontent.com: hcoles/pitest/1.22.1/README.md; szpak/gradle-pitest-plugin/release/1.19.0/README.md; TNG/ArchUnit/v1.5.1/docs/userguide/; checkstyle/checkstyle/checkstyle-12.3.1/src/site/xdoc/checks/; pmd/pmd/pmd_releases/7.27.0/pmd-java/src/main/resources/category/java/; jacoco/jacoco/v0.8.15/org.jacoco.doc/docroot/doc/ |
| Хуки и сабагенты Claude Code (правки `.claude/settings.json`, `.claude/agents/`, `gauntlet/hooks/`) | code.claude.com/docs/en/hooks, code.claude.com/docs/en/sub-agents — без версии: сверять с версией Claude Code в сессии (`claude --version`, в облаке — строка статуса или метаданные сессии) | поведение проверять `python3 gauntlet/hooks/selftest.py` и журналом хука в `build/gauntlet/` |

- Адрес без версии показывает последнюю ветку, а не нашу: у docs.spring.io, docs.hibernate.org,
  docs.micrometer.io версия major.minor стоит в пути (`/spring-boot/4.0/`, `/orm/7.2/`, `/reference/1.16/`);
  проверь по метатегу `version` страницы. Сайты без версий (docs.liquibase.com, java.testcontainers.org,
  assertj.github.io, cucumber.io) — источник, только если страница не расходится с исходниками по тегу
  нужной версии; в отчёт тогда идёт ссылка на тег.
- Универсальная замена недоступного сайта: javadoc-jar и sources-jar нужной версии на repo.maven.apache.org
  (зеркало — javadoc.io), исходники документации по тегу версии на raw.githubusercontent.com; последний
  рубеж — jar в кэше Gradle (`~/.gradle/caches/modules-2/files-2.1/`).
- Не источник: блоги, Stack Overflow, статьи, ответы ИИ, память модели, код других проектов. Официальный
  источник противоречит «привычному» — прав источник.
- Домен недоступен из облачной сессии — его добавляет человек в Network access окружения; до этого — запасной
  путь и пометка в отчёте. Ответ прокси «CONNECT tunnel failed, response 403» бывает и у несуществующего домена:
  существование адреса сначала сверь с метаданными артефакта (`<url>` в POM).
- Веб-страницы github.com чужих репозиториев из сессии закрыты (HTTP 403 «GitHub access to this repository is
  not enabled for this session»); чужие репозитории ради документации не подключать. Файлы репозитория —
  `raw.githubusercontent.com/<owner>/<repo>/<тег>/…`, вики — `raw.githubusercontent.com/wiki/<owner>/<repo>/<Страница>.md`;
  страницу, которой нет и там (вики миграции Spring Boot), открывает человек.
- Nexus контура — не источник документации и из сессии не используется (см. «Рабочий контур»).

## Когда застрял
- Выживший мутант, который ты считаешь эквивалентным; `switch`, который должен остаться выше лимита
  CRAP; легитимная находка integrity — **остановись и опиши**. Человек может добавить исключение в
  `gauntlet/config/*-allowlist.txt`.
- Спецификация противоречит unit-тесту или коду — остановись и спроси; не правь ни одну сторону,
  чтобы «подружить» их.
- Гейт красный после 3 попыток — Stop-хук отпустит тебя с предупреждением. Работа при этом НЕ сделана:
  так и напиши в отчёте, с причинами.
