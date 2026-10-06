# Образ сервиса data-reference для Kubernetes.
#
# Сначала получаем jar, затем раскладываем его по слоям Spring Boot: библиотеки лежат
# в нижнем слое, код сервиса — в верхнем, и после правки кода меняется только верхний.
#
# Откуда взять jar, задаёт JAR_SOURCE:
#   build — собрать внутри образа (так собирает CI). Нужна сеть до Nexus и дистрибутива Gradle;
#           вне контура задайте PUBLIC_REPOSITORIES=true, чтобы брать зависимости из Maven Central
#           (переменные, по которым сборка сама выбирает репозитории, внутрь docker build не попадают).
#   host  — взять собранный на машине: ./gradlew bootJar && docker build --build-arg JAR_SOURCE=host .
#           Подходит, когда у контейнера нет сети.
#
# Строки «# syntax=» нет нарочно: с ней BuildKit скачивал бы фронтенд с Docker Hub.
#
# В сети без выхода наружу базовые образы и Gradle берутся из зеркала Nexus (compose читает
# эти аргументы из .env, шаблон — .env.example):
#   docker build --build-arg JDK_IMAGE=<зеркало>/eclipse-temurin:21.0.12.1_1-jdk \
#                --build-arg JRE_IMAGE=<зеркало>/eclipse-temurin:21.0.12.1_1-jre \
#                --build-arg GRADLE_DISTRIBUTION_URL=<зеркало>/gradle-8.14.3-bin.zip .
#
# Сертификаты корпоративного центра сертификации кладутся в certs/: без них JVM не доверяет
# Nexus при сборке и брокеру 1С:УСК при работе. В слои образа они не попадают.
#
# Тесты здесь не запускаются: им нужен Docker, их прогоняет CI до сборки образа.

# Точная версия JDK, чтобы через месяц тот же Dockerfile собрал тот же Java.
ARG JDK_IMAGE=eclipse-temurin:21.0.12.1_1-jdk
ARG JRE_IMAGE=eclipse-temurin:21.0.12.1_1-jre
ARG JAR_SOURCE=build

# ---------- 1a. Jar из исходников ----------
FROM ${JDK_IMAGE} AS build
WORKDIR /workspace

# false — зависимости из Nexus контура, true — из Maven Central.
ARG PUBLIC_REPOSITORIES=false

RUN --mount=type=bind,source=certs,target=/certs sh /certs/import-ca.sh /certs

# Сначала только файлы сборки: пока они не менялись, скачанные зависимости берутся из кэша слоя.
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle

# Адрес дистрибутива Gradle можно подменить на зеркало. Контрольная сумма остаётся прежней,
# поэтому зеркало обязано отдать тот же архив.
ARG GRADLE_DISTRIBUTION_URL
RUN if [ -n "${GRADLE_DISTRIBUTION_URL}" ]; then \
      sed -i "s#^distributionUrl=.*#distributionUrl=${GRADLE_DISTRIBUTION_URL}#" \
          gradle/wrapper/gradle-wrapper.properties; \
    fi

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain --quiet -PpublicRepositories=${PUBLIC_REPOSITORIES} dependencies

# Jar берётся по точному имени с текущей версией: нет такого файла — сборка падает.
COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain -PpublicRepositories=${PUBLIC_REPOSITORIES} bootJar -x test \
    && cp "build/libs/data-reference-$(sed -n 's/^version=//p' gradle.properties).jar" application.jar

# ---------- 1b. Jar, собранный на машине ----------
# Точное имя, а не маска: в build/libs могут лежать jar-ы прежних версий.
FROM ${JRE_IMAGE} AS host
WORKDIR /workspace
COPY gradle.properties ./
COPY build/libs build/libs
RUN cp "build/libs/data-reference-$(sed -n 's/^version=//p' gradle.properties).jar" application.jar

# ---------- 2. Слои Spring Boot ----------
FROM ${JAR_SOURCE} AS layers
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------- 3. Образ для запуска ----------
FROM ${JRE_IMAGE}

RUN --mount=type=bind,source=certs,target=/certs sh /certs/import-ca.sh /certs

# Числовой id пользователя: runAsNonRoot в Kubernetes проверяет число, а не имя.
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=layers --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=layers --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=app:app /workspace/extracted/application/ ./

USER 10001:10001
# 8080 — REST и пробы /livez и /readyz, 8081 — актуатор (health, prometheus).
EXPOSE 8080 8081

# Память JVM считается от лимита контейнера. При нехватке памяти процесс завершается,
# и Kubernetes перезапускает под, а не оставляет его полуживым.
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Адреса и пароли базы и брокера приходят переменными окружения (см. application.yml).
# Умолчаний у паролей нет: пароль из репозитория подошёл бы к любой базе с таким же паролем.
# Слушатели очередей 1С включены, пока их не выключат переменной RABBITMQ_LISTENER_AUTO_STARTUP.
ENTRYPOINT ["java", "-jar", "application.jar"]
