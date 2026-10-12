package ru.projectx.contracts.support;

import java.io.IOException;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import ru.projectx.contracts.config.InternalBrokerProperties;
import ru.projectx.contracts.service.integration.InternalBroker;

@TestConfiguration(proxyBeanMethods = false)
public class RabbitTestcontainer {

    public static final String ONEC_VHOST = "onec";

    private static final String IMAGE = "rabbitmq:4.1.2-alpine";
    private static final String INTERNAL_VHOST = "/";
    private static final String ALL_PERMISSIONS = ".*";

    @Bean
    @ConditionalOnExpression("'${" + TestBroker.URL_PROPERTY + ":}'.isEmpty()")
    public RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(IMAGE);
    }

    @Bean
    @ConditionalOnExpression("'${" + TestBroker.URL_PROPERTY + ":}'.isEmpty()")
    public DynamicPropertyRegistrar containerBrokers(RabbitMQContainer container)
            throws IOException, InterruptedException {
        addVirtualHost(container, ONEC_VHOST);
        TestBroker.Address internal = new TestBroker.Address(container.getHost(), container.getAmqpPort(),
                INTERNAL_VHOST, container.getAdminUsername(), container.getAdminPassword());
        return brokers(internal, internal.withVirtualHost(ONEC_VHOST));
    }

    @Bean
    public TestQueues internalBrokerQueues(@InternalBroker ConnectionFactory connections,
            InternalBrokerProperties properties) {
        return new TestQueues(connections).declare(properties.queues().taskChanges());
    }

    @Bean
    @ConditionalOnExpression("!'${" + TestBroker.URL_PROPERTY + ":}'.isEmpty()")
    public DynamicPropertyRegistrar externalBrokers(Environment environment) {
        TestBroker.Address address = TestBroker.Address.parse(environment.getRequiredProperty(TestBroker.URL_PROPERTY));
        return brokers(address, address);
    }

    private static DynamicPropertyRegistrar brokers(TestBroker.Address internal, TestBroker.Address onec) {
        return registry -> {
            register(registry, "internal", internal);
            register(registry, "onec", onec);
        };
    }

    private static void register(DynamicPropertyRegistry registry, String broker, TestBroker.Address address) {
        String prefix = "projectx.brokers." + broker + ".connection.";
        registry.add(prefix + "host", address::host);
        registry.add(prefix + "port", address::port);
        registry.add(prefix + "virtual-host", address::virtualHost);
        registry.add(prefix + "username", address::username);
        registry.add(prefix + "password", address::password);
    }

    private static void addVirtualHost(RabbitMQContainer container, String virtualHost)
            throws IOException, InterruptedException {
        execute(container, "rabbitmqctl", "add_vhost", virtualHost);
        execute(container, "rabbitmqctl", "set_permissions", "-p", virtualHost, container.getAdminUsername(),
                ALL_PERMISSIONS, ALL_PERMISSIONS, ALL_PERMISSIONS);
    }

    private static void execute(RabbitMQContainer container, String... command)
            throws IOException, InterruptedException {
        ExecResult result = container.execInContainer(command);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("команда брокера не выполнена: " + String.join(" ", command)
                    + System.lineSeparator() + result.getStderr());
        }
    }
}
