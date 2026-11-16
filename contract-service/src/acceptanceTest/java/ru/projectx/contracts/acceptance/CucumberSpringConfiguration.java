package ru.projectx.contracts.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import java.io.IOException;
import javax.sql.DataSource;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import ru.projectx.contracts.config.broker.InternalBrokerProperties;
import ru.projectx.contracts.config.broker.MarketingBrokerProperties;
import ru.projectx.contracts.config.broker.OneCBrokerProperties;
import ru.projectx.contracts.service.integration.broker.InternalBroker;
import ru.projectx.contracts.service.integration.broker.MarketingBroker;
import ru.projectx.contracts.service.integration.broker.OneCBroker;
import ru.projectx.contracts.service.leadership.LeaderLockConnections;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.health.brokers.enabled=false",
        "projectx.scheduling.enabled=false",
        "projectx.brokers.internal.connection.tls.enabled=false",
        "projectx.brokers.onec.connection.tls.enabled=false",
        "projectx.brokers.internal.listener-auto-startup=true",
        "projectx.brokers.onec.listener-auto-startup=true",
        "projectx.storage.bucket=contracts-stand",
        "projectx.storage.base-path=certificates",
        "projectx.storage.inn=1111111111",
        "projectx.storage.kpp=222222222",
        "projectx.storage.timeouts.connect=1s",
        "projectx.storage.timeouts.read=2s",
        "projectx.storage.timeouts.write=2s",
        "projectx.storage.timeouts.call=3s",
        "projectx.payment.enabled=true",
        "projectx.ordering.product=rvl",
        "projectx.ordering.channel=rvl-lk",
        "projectx.ordering.cancel-reason=notActual",
        "projectx.ordering.connect-timeout=1s",
        "projectx.ordering.read-timeout=1s",
        "projectx.ordering.retry.attempts=3",
        "projectx.ordering.retry.delay=0s",
        "projectx.ordering.token.client-id=contracts-stand",
        "projectx.ordering.token.client-secret=stand-token-secret-5b2e",
        "projectx.ordering.token.connect-timeout=1s",
        "projectx.ordering.token.read-timeout=1s",
        "projectx.brokers.marketing.connection.tls.enabled=false",
        "projectx.brokers.marketing.document-fixes.exchange=rvl.exchange",
        "projectx.brokers.marketing.document-fixes.routing-key=rvl-contract-fixed",
        "projectx.brokers.marketing.listener-auto-startup=true",
        "projectx.brokers.marketing.order-events.queue=order.event.rvl.topic"})
@Import(CucumberSpringConfiguration.Stand.class)
public class CucumberSpringConfiguration {

    @TestConfiguration(proxyBeanMethods = false)
    static class Stand {

        private static final String POSTGRES_IMAGE = "postgres:16.10-alpine";
        private static final String RABBITMQ_IMAGE = "rabbitmq:4.1.2-alpine";
        private static final String LM_POLICY_REQUESTS = "запросы на выпуск полисов ЛМ";
        private static final String LM_POLICY_RESULTS = "результаты выпуска полисов ЛМ";
        private static final String KM_CONTRACT_SUBMISSIONS = "передачи договоров КМ";
        private static final String KM_CONTRACT_REGISTRATIONS = "результаты регистрации договоров КМ";
        private static final String DATA_SOURCE_BEAN = "dataSource";

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(POSTGRES_IMAGE);
        }

        @Bean
        ReferenceStub referenceStub() throws IOException {
            return new ReferenceStub();
        }

        @Bean
        DynamicPropertyRegistrar referenceUrl(ReferenceStub stub) {
            return registry -> registry.add("projectx.reference.url", stub::url);
        }

        @Bean
        OrderingStub orderingStub(StandClock clock) throws IOException {
            return new OrderingStub(clock);
        }

        @Bean
        DynamicPropertyRegistrar orderingUrls(OrderingStub stub) {
            return registry -> {
                registry.add("projectx.ordering.url", stub::orderingUrl);
                registry.add("projectx.ordering.token.uri", stub::tokenUri);
            };
        }

        @Bean
        RabbitMQContainer rabbitContainer() {
            return new RabbitMQContainer(RABBITMQ_IMAGE);
        }

        @Bean
        DynamicPropertyRegistrar standBrokers(RabbitMQContainer container) throws IOException, InterruptedException {
            for (String virtualHost : new String[] {"onec", "marketing"}) {
                container.execInContainer("rabbitmqctl", "add_vhost", virtualHost);
                container.execInContainer("rabbitmqctl", "set_permissions", "-p", virtualHost,
                        container.getAdminUsername(), ".*", ".*", ".*");
            }
            return registry -> {
                for (String broker : new String[] {"internal", "onec", "marketing"}) {
                    String prefix = "projectx.brokers." + broker + ".connection.";
                    registry.add(prefix + "host", container::getHost);
                    registry.add(prefix + "port", container::getAmqpPort);
                    registry.add(prefix + "virtual-host", () -> "internal".equals(broker) ? "/" : broker);
                    registry.add(prefix + "username", container::getAdminUsername);
                    registry.add(prefix + "password", container::getAdminPassword);
                }
            };
        }

        @Bean
        StandStorage standStorage() throws IOException {
            return StandStorage.container();
        }

        @Bean
        MinIOContainer storageContainer(StandStorage storage) {
            return storage.startable();
        }

        @Bean
        DynamicPropertyRegistrar standStorageAccess(StandStorage storage, MinIOContainer storageContainer) {
            return registry -> {
                registry.add("projectx.storage.endpoint", storage::endpoint);
                registry.add("projectx.storage.access-key", storage::accessKey);
                registry.add("projectx.storage.secret-key", storage::secretKey);
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

        @Bean
        StatusQueue statusQueue(@InternalBroker ConnectionFactory connections, InternalBrokerProperties broker) {
            return new StatusQueue(connections, broker.queues().statusChanges());
        }

        @Bean
        OneCQueue lmPolicyRequestQueue(@OneCBroker ConnectionFactory connections, OneCBrokerProperties broker) {
            return new OneCQueue(connections, LM_POLICY_REQUESTS, broker.queues().lmPolicyRequests());
        }

        @Bean
        OneCQueue lmPolicyResultQueue(@OneCBroker ConnectionFactory connections, OneCBrokerProperties broker) {
            return new OneCQueue(connections, LM_POLICY_RESULTS, broker.queues().lmPolicyIssues());
        }

        @Bean
        OneCQueue kmContractSubmissionQueue(@OneCBroker ConnectionFactory connections, OneCBrokerProperties broker) {
            return new OneCQueue(connections, KM_CONTRACT_SUBMISSIONS, broker.queues().kmContractSubmissions());
        }

        @Bean
        OneCQueue kmContractRegistrationQueue(@OneCBroker ConnectionFactory connections, OneCBrokerProperties broker) {
            return new OneCQueue(connections, KM_CONTRACT_REGISTRATIONS, broker.queues().kmContractRegistrations());
        }

        @Bean
        DocumentFixQueue documentFixQueue(@MarketingBroker ConnectionFactory connections,
                MarketingBrokerProperties broker) {
            return new DocumentFixQueue(connections, broker.documentFixes().exchange(),
                    broker.documentFixes().routingKey());
        }

        @Bean
        OrderEventQueue orderEventQueue(@MarketingBroker ConnectionFactory connections,
                MarketingBrokerProperties broker) {
            return new OrderEventQueue(connections, broker.orderEvents().queue());
        }

        @Bean
        static BeanPostProcessor switchableDataSource() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof DataSource pool && DATA_SOURCE_BEAN.equals(beanName)) {
                        return new SwitchableDataSource(pool);
                    }
                    if (bean instanceof LeaderLockConnections pool) {
                        return new SwitchableLeaderLockConnections(pool);
                    }
                    return bean;
                }
            };
        }
    }
}
