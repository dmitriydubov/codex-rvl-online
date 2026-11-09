package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Когда;
import java.io.IOException;
import org.testcontainers.containers.Container;
import org.testcontainers.rabbitmq.RabbitMQContainer;

public class BrokerConnectionSteps {

    private final RabbitMQContainer broker;

    public BrokerConnectionSteps(RabbitMQContainer broker) {
        this.broker = broker;
    }

    @Когда("брокер разорвал все соединения Дата-референса")
    public void allConnectionsClosed() throws IOException, InterruptedException {
        Container.ExecResult result = broker.execInContainer("rabbitmqctl", "close_all_connections", "стенд приёмки");

        assertThat(result.getExitCode()).as("rabbitmqctl close_all_connections: %s", result.getStderr()).isZero();
    }
}
