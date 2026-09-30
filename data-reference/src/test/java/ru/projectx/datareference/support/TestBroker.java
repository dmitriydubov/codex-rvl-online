package ru.projectx.datareference.support;

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
 * <p>Тесту приёма нужны и брокер, и база. Без Docker он работает, только когда заданы
 * оба адреса, {@code TEST_RABBITMQ_URL} и {@code TEST_POSTGRES_URL}; иначе он падает,
 * а не пропускается — см. {@link TestDatabase}.
 */
public final class TestBroker {

    public static final String URL_PROPERTY = "test.rabbitmq.url";

    private TestBroker() {
    }
}
