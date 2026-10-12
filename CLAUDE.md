# rvl-online

Сервисы контура РВЛ ОНЛАЙН, за которые отвечает владелец репозитория. У каждого сервиса своя
Gradle-сборка в своём каталоге; корневой сборки нет.

- `data-reference/` — сервис справочных данных: справочники и полисы из 1С:УСК (RabbitMQ 1С),
  REST-чтение справочников для сервисов Заявок и Договоров. Java 21, Spring Boot 4.0, PostgreSQL.
  Охраняется gauntlet (`module=` в `gauntlet/config/gauntlet.properties`). Его правила и
  предметная область — в `data-reference/CLAUDE.md`: прочитай перед задачей в `data-reference`.
- `contract-service/` — сервис Договоров, он же бэкенд ЛК застрахованного: договоры КМ и полисы ЛМ по
  задачам сервиса Заявок, обмен с 1С:УСК, печатные формы, акцепт. Java 21, Spring Boot 4.0, PostgreSQL.
  Охраняется gauntlet с 2026-10-12. Его правила — в `contract-service/CLAUDE.md`: прочитай перед задачей
  в `contract-service`; документы — в `project_files/contract-service/`.
- `project_files/` — ТЗ, схемы и структуры данных контура: источник бизнес-правил. Разложен по сервисам:
  `common/`, `data-reference/`, `contract-service/` (см. `project_files/README.md`).

Сервис Заявок (он же сервис HR), BFF и фронтенд — у других разработчиков, в своих репозиториях.

Всё это — внешний контур (облако Claude Code и GitHub). В рабочий контур компании сервисы переносятся
вручную, без `gauntlet/`, `.claude/`, файлов `CLAUDE.md` (этого и модулей), `.github/`, каталогов приёмки
и `project_files/` (подробно — `gauntlet/CLAUDE.md`, «Рабочий контур»).

@gauntlet/CLAUDE.md
