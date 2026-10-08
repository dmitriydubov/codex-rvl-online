# Образ сервиса для Kubernetes. Jar раскладывается по слоям Spring Boot, поэтому после правки кода меняется только
# верхний слой. Строки «# syntax=» нет нарочно: с ней BuildKit скачивал бы фронтенд с Docker Hub.
# Способы сборки, зеркала образов и сертификаты описаны в README модуля.

# Точная версия JDK, чтобы через месяц тот же Dockerfile собрал тот же Java.
ARG JDK_IMAGE=eclipse-temurin:21.0.12.1_1-jdk
ARG JRE_IMAGE=eclipse-temurin:21.0.12.1_1-jre
# build — собрать jar внутри образа, host — взять собранный на машине, когда у сборки нет сети.
ARG JAR_SOURCE=build

# ---------- 1a. Jar из исходников ----------
FROM ${JDK_IMAGE} AS build
WORKDIR /workspace

# false — зависимости из Nexus контура, true — из Maven Central. Аргумент нужен, потому что переменные окружения,
# по которым сборка сама выбирает репозитории, внутрь docker build не попадают.
ARG PUBLIC_REPOSITORIES=false

# Корпоративный центр сертификации из certs/: без него сборка не доверяет Nexus. В слои образа он не попадает.
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

# Тесты здесь не запускаются: им нужен Docker, их прогоняет CI до сборки образа.
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

# Тот же центр сертификации: без него сервис не доверяет брокеру 1С:УСК.
RUN --mount=type=bind,source=certs,target=/certs sh /certs/import-ca.sh /certs

# Числовой id пользователя: runAsNonRoot в Kubernetes проверяет число, а не имя.
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=layers --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=layers --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=app:app /workspace/extracted/application/ ./

USER 10001:10001
# 8080 — REST и пробы /livez и /readyz, 8081 — актуатор (health, info, prometheus).
EXPOSE 8080 8081

# Память JVM считается от лимита контейнера. При нехватке памяти процесс завершается,
# и Kubernetes перезапускает под, а не оставляет его полуживым.
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Адреса и пароли базы и брокера приходят переменными окружения (см. application.yml).
# Умолчаний у паролей нет: пароль из репозитория подошёл бы к любой базе с таким же паролем.
# Слушатели очередей 1С включены, пока их не выключат переменной RABBITMQ_LISTENER_AUTO_STARTUP.
ENTRYPOINT ["java", "-jar", "application.jar"]
