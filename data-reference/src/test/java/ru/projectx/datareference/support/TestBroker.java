package ru.projectx.datareference.support;

import java.util.Optional;

/**
 * Откуда тестам приёма взять RabbitMQ.
 *
 * <p>Тот же порядок, что у {@link TestDatabase}: основной источник — контейнер
 * Testcontainers, запасной — внешний брокер из переменной окружения
 * {@code TEST_RABBITMQ_URL} вида {@code amqp://guest:guest@localhost:5672/}, для машин
 * без Docker. Очереди на нём объявляет сам тест, готовить их заранее не нужно. Брокер
 * должен быть <b>тестовым</b>: тесты публикуют в очереди с именами контура и вычитывают
 * из них, и указать сюда брокер 1С:УСК значит забрать оттуда настоящие сообщения.
 *
 * <p>Тесту приёма нужны и брокер, и база, поэтому «доступно» здесь означает оба сразу.
 */
public final class TestBroker {

    public static final String URL_PROPERTY = "test.rabbitmq.url";

    private static final boolean AVAILABLE = DockerAvailability.isDockerAvailable()
            || externalUrl().isPresent() && TestDatabase.externalUrl().isPresent();

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /** Адрес внешнего брокера из окружения; пусто — тесты поднимают контейнер. */
    public static Optional<String> externalUrl() {
        return Optional.ofNullable(System.getenv("TEST_RABBITMQ_URL")).filter(url -> !url.isBlank());
    }

    private TestBroker() {
    }
}
