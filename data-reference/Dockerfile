# Образ сервиса data-reference для Kubernetes.
#
# Два этапа. Первый даёт jar, второй, на JRE, раскладывает его по слоям Spring Boot:
# зависимости меняются редко и лежат в нижнем слое, код сервиса — в верхнем, так что
# пересборка после правки кода перекладывает килобайты, а не десятки мегабайт библиотек.
#
# Откуда берётся jar, выбирает JAR_SOURCE:
#   build — собрать обёрткой Gradle внутри образа JDK. Так собирает CI: образ
#           воспроизводим из одного репозитория. Контейнеру нужна сеть до Nexus
#           и до дистрибутива Gradle. Вне контура — PUBLIC_REPOSITORIES=true: зависимости
#           тогда берутся из Maven Central (переключатель — в settings.gradle; переменные
#           окружения облака и GitHub Actions внутрь docker build не попадают).
#   host  — взять готовый из build/libs, собранный на машине командой ./gradlew bootJar.
#           Для локальной проверки образа там, где у контейнера нет выхода наружу:
#           образу нужен только JRE.
#
# Директивы «# syntax=» нет намеренно: она заставляет BuildKit скачать образ фронтенда
# с Docker Hub, а всё, что здесь используется, умеет встроенный фронтенд Docker.
#
# Базовые образы и дистрибутив Gradle в сети без выхода наружу берутся из зеркала Nexus
# аргументами сборки; compose читает их из .env, см. .env.example:
#   docker build --build-arg JDK_IMAGE=<зеркало>/eclipse-temurin:21-jdk \
#                --build-arg JRE_IMAGE=<зеркало>/eclipse-temurin:21-jre \
#                --build-arg GRADLE_DISTRIBUTION_URL=<зеркало>/gradle-8.14.3-bin.zip .
# Локально из готового jar: ./gradlew bootJar && docker build --build-arg JAR_SOURCE=host .
#
# Корпоративный центр сертификации. Nexus контура подписан им, базовые образы о нём
# не знают, и JVM внутри контейнера отвергает TLS. Сертификаты центра (корневой
# и промежуточный, по одному на файл .crt/.cer/.pem) кладутся в certs/ и импортируются
# в хранилище JVM на обоих этапах: на сборке — ради Nexus, на запуске — ради TLS
# к брокеру 1С:УСК с включённой проверкой сертификата. Каталог подключается как
# монтирование контекста, в слои образа файлы не попадают. Пустой certs/ — шаг пуст.
#
# Тесты на этапе сборки не запускаются намеренно: им нужны Docker для Testcontainers
# и внешние базы, а сборка образа — не место для них. Их прогоняет CI до сборки образа.

ARG JDK_IMAGE=eclipse-temurin:21-jdk
ARG JRE_IMAGE=eclipse-temurin:21-jre
ARG JAR_SOURCE=build

# ---------- 1a. Jar из исходников ----------
FROM ${JDK_IMAGE} AS build
WORKDIR /workspace

# Репозитории зависимостей: false — Nexus контура, true — Maven Central.
ARG PUBLIC_REPOSITORIES=false

RUN --mount=type=bind,source=certs,target=/certs sh /certs/import-ca.sh /certs

# Сначала только описание сборки: пока не меняются build.gradle и settings.gradle,
# слой с разрешёнными зависимостями переиспользуется, и правка кода их не перекачивает.
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle

# Дистрибутив Gradle обёртка качает с services.gradle.org. Пусто — адрес из
# gradle-wrapper.properties как есть; задано — подменяется на зеркало.
ARG GRADLE_DISTRIBUTION_URL
RUN if [ -n "${GRADLE_DISTRIBUTION_URL}" ]; then \
      sed -i "s#^distributionUrl=.*#distributionUrl=${GRADLE_DISTRIBUTION_URL}#" \
          gradle/wrapper/gradle-wrapper.properties; \
    fi

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain --quiet -PpublicRepositories=${PUBLIC_REPOSITORIES} dependencies

# Jar берётся по точному имени: <rootProject.name>-<version из gradle.properties>.jar,
# как его называет bootJar. Маска data-reference-*.jar при нескольких совпадениях молча
# взяла бы не тот файл, а нет файла с точным именем — сборка падает на cp.
COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain -PpublicRepositories=${PUBLIC_REPOSITORIES} bootJar -x test \
    && cp "build/libs/data-reference-$(sed -n 's/^version=//p' gradle.properties).jar" application.jar

# ---------- 1b. Jar, собранный на машине ----------
# В build/libs на машине копятся jar-ы прежних версий, поэтому и здесь — точное имя
# по текущей версии, а не маска.
FROM ${JRE_IMAGE} AS host
WORKDIR /workspace
COPY gradle.properties ./
COPY build/libs build/libs
RUN cp "build/libs/data-reference-$(sed -n 's/^version=//p' gradle.properties).jar" application.jar

# ---------- 2. Слои Spring Boot ----------
# Имя jar-а зафиксировано выше: в слое application он лежит под тем же именем,
# и точка входа не зависит от версии в gradle.properties.
FROM ${JAR_SOURCE} AS layers
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------- 3. Образ для запуска ----------
FROM ${JRE_IMAGE}

RUN --mount=type=bind,source=certs,target=/certs sh /certs/import-ca.sh /certs

# Пользователь без прав и с фиксированным числовым id: runAsNonRoot в Kubernetes
# проверяет именно число, а не имя.
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=layers --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=layers --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=app:app /workspace/extracted/application/ ./

USER 10001:10001
EXPOSE 8080

# Память JVM — от лимита контейнера, а не от памяти узла; при нехватке памяти процесс
# завершается, и Kubernetes перезапускает под, вместо того чтобы он висел полуживым.
# Переменную читает только запускаемая java, и Kubernetes может её переопределить.
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Всё остальное — адрес базы, брокер 1С:УСК, TLS, RABBITMQ_LISTENER_AUTO_STARTUP=true —
# приходит переменными окружения из манифестов и секретов, см. application.yml.
ENTRYPOINT ["java", "-jar", "application.jar"]
