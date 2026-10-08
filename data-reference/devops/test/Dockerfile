FROM nexus.sogaz.ru/library/openjdk:21-jdk-slim
WORKDIR /app
COPY app.jar app.jar
EXPOSE 8080 8081
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
