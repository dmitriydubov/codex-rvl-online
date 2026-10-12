ARG JDK_IMAGE=eclipse-temurin:21.0.12.1_1-jdk
ARG JRE_IMAGE=eclipse-temurin:21.0.12.1_1-jre
ARG JAR_SOURCE=build

FROM ${JDK_IMAGE} AS build
WORKDIR /workspace

ARG PUBLIC_REPOSITORIES=false

RUN --mount=type=bind,source=certs,target=/certs sh /certs/import-ca.sh /certs

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle

ARG GRADLE_DISTRIBUTION_URL
RUN if [ -n "${GRADLE_DISTRIBUTION_URL}" ]; then \
      sed -i "s#^distributionUrl=.*#distributionUrl=${GRADLE_DISTRIBUTION_URL}#" \
          gradle/wrapper/gradle-wrapper.properties; \
    fi

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain --quiet -PpublicRepositories=${PUBLIC_REPOSITORIES} dependencies

COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain -PpublicRepositories=${PUBLIC_REPOSITORIES} bootJar -x test \
    && cp "build/libs/contract-service-$(sed -n 's/^version=//p' gradle.properties).jar" application.jar

FROM ${JRE_IMAGE} AS host
WORKDIR /workspace
COPY gradle.properties ./
COPY build/libs build/libs
RUN cp "build/libs/contract-service-$(sed -n 's/^version=//p' gradle.properties).jar" application.jar

FROM ${JAR_SOURCE} AS layers
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM ${JRE_IMAGE}

RUN --mount=type=bind,source=certs,target=/certs sh /certs/import-ca.sh /certs

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=layers --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=layers --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=app:app /workspace/extracted/application/ ./

USER 10001:10001
EXPOSE 8080 8081

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "application.jar"]
