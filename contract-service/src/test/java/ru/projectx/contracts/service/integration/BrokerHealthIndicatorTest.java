package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import ru.projectx.contracts.config.broker.BrokerConnectionProperties;

class BrokerHealthIndicatorTest {

    private static final int PORT = 5672;
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(2);

    @Test
    void openConnection_isUpWithBrokerAddress() {
        BrokerHealthIndicator indicator = new BrokerHealthIndicator("internal", factoryWith(connection(true)));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsOnly(
                entry("broker", "internal"), entry("host", "broker.example"), entry("port", PORT),
                entry("virtualHost", "/"));
    }

    @Test
    void closedConnection_isDown() {
        BrokerHealthIndicator indicator = new BrokerHealthIndicator("internal", factoryWith(connection(false)));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).doesNotContainKey("failure");
    }

    @Test
    void connectionFailure_isDownWithFailureTypeOnly() {
        ConnectionFactory factory = factoryWith(null);
        when(factory.createConnection()).thenThrow(new AmqpConnectException(new IOException("refused by secret")));
        BrokerHealthIndicator indicator = new BrokerHealthIndicator("onec", factory);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("failure", "AmqpConnectException")
                .containsEntry("broker", "onec")
                .doesNotContainKeys("username", "password", "error");
        assertThat(health.getDetails().toString()).doesNotContain("secret");
    }

    @Test
    void unreachableBroker_isDownOnRealConnectionFactory() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        CachingConnectionFactory factory = BrokerConnectionFactories.create("internal", new BrokerConnectionProperties(
                "localhost", closedPort, "/", "contract_service", "secret", SHORT_TIMEOUT,
                new BrokerConnectionProperties.Tls(false, true, true)));
        try {
            Health health = new BrokerHealthIndicator("internal", factory).health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("failure", "AmqpConnectException")
                    .containsEntry("port", closedPort);
        }
        finally {
            factory.destroy();
        }
    }

    private static ConnectionFactory factoryWith(Connection connection) {
        ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.getHost()).thenReturn("broker.example");
        when(factory.getPort()).thenReturn(PORT);
        when(factory.getVirtualHost()).thenReturn("/");
        when(factory.getUsername()).thenReturn("contract_service");
        when(factory.createConnection()).thenReturn(connection);
        return factory;
    }

    private static Connection connection(boolean open) {
        Connection connection = mock(Connection.class);
        when(connection.isOpen()).thenReturn(open);
        return connection;
    }

    private static Map.Entry<String, Object> entry(String key, Object value) {
        return Map.entry(key, value);
    }
}
