package ru.projectx.contracts.service.integration;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Компонент здоровья одного подключения к брокеру: UP, когда соединение открыто, иначе DOWN с типом отказа.
 * Проверка сама открывает соединение, поэтому брокер, недоступный на старте, виден в health, а не роняет сервис.
 * В деталях адрес брокера без учётных данных.
 */
final class BrokerHealthIndicator implements HealthIndicator {

    private static final String BROKER = "broker";
    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final String VIRTUAL_HOST = "virtualHost";
    private static final String FAILURE = "failure";

    private final String broker;
    private final ConnectionFactory connectionFactory;

    BrokerHealthIndicator(String broker, ConnectionFactory connectionFactory) {
        this.broker = broker;
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (Connection connection = connectionFactory.createConnection()) {
            return connection.isOpen() ? address(Health.up()).build() : address(Health.down()).build();
        }
        catch (AmqpException failure) {
            return address(Health.down()).withDetail(FAILURE, failure.getClass().getSimpleName()).build();
        }
    }

    private Health.Builder address(Health.Builder health) {
        return health
                .withDetail(BROKER, broker)
                .withDetail(HOST, connectionFactory.getHost())
                .withDetail(PORT, connectionFactory.getPort())
                .withDetail(VIRTUAL_HOST, connectionFactory.getVirtualHost());
    }
}
