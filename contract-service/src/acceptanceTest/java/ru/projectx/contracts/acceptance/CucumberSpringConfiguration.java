package ru.projectx.contracts.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import java.io.IOException;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import ru.projectx.contracts.config.InternalBrokerProperties;
import ru.projectx.contracts.service.integration.InternalBroker;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.health.brokers.enabled=false",
        "projectx.scheduling.enabled=false",
        "projectx.inbox.requeue-delay=1s",
        "projectx.brokers.internal.connection.tls.enabled=false",
        "projectx.brokers.onec.connection.tls.enabled=false",
        "projectx.brokers.internal.listener-auto-startup=true",
        "projectx.brokers.onec.listener-auto-startup=false"})
@Import(CucumberSpringConfiguration.Stand.class)
public class CucumberSpringConfiguration {

    @TestConfiguration(proxyBeanMethods = false)
    static class Stand {

        private static final String POSTGRES_IMAGE = "postgres:16.10-alpine";
        private static final String RABBITMQ_IMAGE = "rabbitmq:4.1.2-alpine";

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(POSTGRES_IMAGE);
        }

        @Bean
        RabbitMQContainer rabbitContainer() {
            return new RabbitMQContainer(RABBITMQ_IMAGE);
        }

        @Bean
        DynamicPropertyRegistrar standBrokers(RabbitMQContainer container) throws IOException, InterruptedException {
            container.execInContainer("rabbitmqctl", "add_vhost", "onec");
            container.execInContainer("rabbitmqctl", "set_permissions", "-p", "onec", container.getAdminUsername(),
                    ".*", ".*", ".*");
            return registry -> {
                for (String broker : new String[] {"internal", "onec"}) {
                    String prefix = "projectx.brokers." + broker + ".connection.";
                    registry.add(prefix + "host", container::getHost);
                    registry.add(prefix + "port", container::getAmqpPort);
                    registry.add(prefix + "virtual-host", () -> "internal".equals(broker) ? "/" : "onec");
                    registry.add(prefix + "username", container::getAdminUsername);
                    registry.add(prefix + "password", container::getAdminPassword);
                }
            };
        }

        @Bean
        @Primary
        StandClock standClock() {
            return new StandClock();
        }

        @Bean
        TaskQueue taskQueue(@InternalBroker ConnectionFactory connections, InternalBrokerProperties broker) {
            return new TaskQueue(connections, broker.queues().taskChanges());
        }
    }
}
