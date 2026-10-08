package ru.projectx.datareference.support;

/**
 * Откуда тестам приёма взять RabbitMQ: контейнер Testcontainers, а без Docker тестовый брокер из
 * {@code TEST_RABBITMQ_URL}, иначе тесты заберут из очередей настоящие сообщения. Без Docker нужен и
 * {@code TEST_POSTGRES_URL}, а без адресов тест падает, а не пропускается ({@link TestDatabase}).
 */
public final class TestBroker {

    public static final String URL_PROPERTY = "test.rabbitmq.url";

    private TestBroker() {
    }
}
