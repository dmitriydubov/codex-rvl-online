package ru.projectx.contracts.support;

import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Настоящий RabbitMQ для тестов подключений: подтверждения издателя и состояние соединения у мока нет. Один контейнер
 * изображает оба брокера контура двумя виртуальными хостами: межсервисный — {@code /}, 1С:УСК — свой, так что
 * подключения, очереди и права у них раздельные. Без Docker берётся внешний брокер ({@link TestBroker}).
 */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitTestcontainer {

    /** Второй виртуальный хост в контейнере, под брокер 1С:УСК. */
    public static final String ONEC_VHOST = "onec";

    /**
     * Та же версия, что у брокеров контура (RabbitMQ 4.1.2): подтверждения проверяются на ней, а не на последней
     * 4.1.x, которую принёс бы плавающий тег.
     */
    private static final String IMAGE = "rabbitmq:4.1.2-alpine";
    private static final String INTERNAL_VHOST = "/";
    private static final String ALL_PERMISSIONS = ".*";

    @Bean
    @ConditionalOnExpression("'${" + TestBroker.URL_PROPERTY + ":}'.isEmpty()")
    public RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(IMAGE);
    }

    /** Контейнер к этому моменту запущен: Boot стартует его при создании бина, поэтому второй vhost заводится здесь. */
    @Bean
    @ConditionalOnExpression("'${" + TestBroker.URL_PROPERTY + ":}'.isEmpty()")
    public DynamicPropertyRegistrar containerBrokers(RabbitMQContainer container)
            throws IOException, InterruptedException {
        addVirtualHost(container, ONEC_VHOST);
        TestBroker.Address internal = new TestBroker.Address(container.getHost(), container.getAmqpPort(),
                INTERNAL_VHOST, container.getAdminUsername(), container.getAdminPassword());
        return brokers(internal, internal.withVirtualHost(ONEC_VHOST));
    }

    /** Внешний брокер один, поэтому оба подключения идут в его vhost: два подключения к одному брокеру. */
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

    /** Командой брокера, потому что у контейнера Testcontainers 2.0 методов для vhost и прав больше нет. */
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
