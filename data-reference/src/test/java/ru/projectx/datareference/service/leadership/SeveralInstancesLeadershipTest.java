package ru.projectx.datareference.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static ru.projectx.datareference.support.OneCMessages.messageWithId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import ru.projectx.datareference.DataReferenceApplication;
import ru.projectx.datareference.config.OneCQueueProperties;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;
import ru.projectx.datareference.support.AmqpIntegrationTest;
import ru.projectx.datareference.support.DatabaseCleaner;

@AmqpIntegrationTest
@TestPropertySource(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class SeveralInstancesLeadershipTest {

    private static final int INSTANCES = 3;
    private static final Duration ROLE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STABLE_FOR = Duration.ofSeconds(3);

    @Autowired
    private Environment environment;

    @Autowired
    private RabbitMQContainer broker;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OneCQueueProperties queues;

    @Autowired
    private InboundMessageRepository inboundMessages;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private final List<ConfigurableApplicationContext> instances = new ArrayList<>();

    @BeforeEach
    void startInstancesOnOneDatabaseAndBroker() {
        rabbitAdmin.initialize();
        databaseCleaner.clear();
        String group = "instances-" + UUID.randomUUID();
        for (int instance = 0; instance < INSTANCES; instance++) {
            instances.add(start(group));
        }
    }

    @AfterEach
    void stopInstances() {
        instances.forEach(ConfigurableApplicationContext::close);
    }

    @Test
    void exactlyOneInstanceLeadsAndConsumes_andAnotherTakesOverWhenItStops() {
        ConfigurableApplicationContext leader = awaitSingleLeader();
        assertOnlyLeaderConsumes(leader);

        leader.close();
        instances.remove(leader);

        ConfigurableApplicationContext successor = awaitSingleLeader();
        assertOnlyLeaderConsumes(successor);
        UUID messageId = UUID.randomUUID();
        rabbitTemplate.convertAndSend(queues.mkb10(), messageWithId(messageId));
        await().atMost(ROLE_TIMEOUT).until(() -> inboundMessages.findById(messageId).isPresent());
        assertThat(inboundMessages.count()).isEqualTo(1);
    }

    private ConfigurableApplicationContext awaitSingleLeader() {
        await().atMost(ROLE_TIMEOUT).until(() -> leaders().size() == 1);
        await().during(STABLE_FOR).atMost(ROLE_TIMEOUT.plus(STABLE_FOR)).until(() -> leaders().size() == 1);
        return leaders().getFirst();
    }

    private void assertOnlyLeaderConsumes(ConfigurableApplicationContext leader) {
        await().atMost(ROLE_TIMEOUT).untilAsserted(() -> assertThat(consumerCount(queues.mkb10())).isEqualTo(1));
        assertThat(consumerCount(queues.policies())).isEqualTo(1);
        assertThat(runningContainers(leader)).isEqualTo(listenerContainers(leader));
        instances.stream().filter(instance -> instance != leader)
                .forEach(standby -> assertThat(runningContainers(standby)).isZero());
    }

    private List<ConfigurableApplicationContext> leaders() {
        return instances.stream()
                .filter(instance -> instance.getBean(BackgroundLeadership.class).leader())
                .toList();
    }

    private int consumerCount(String queue) {
        return rabbitAdmin.getQueueInfo(queue).getConsumerCount();
    }

    private static long runningContainers(ConfigurableApplicationContext instance) {
        return instance.getBean(RabbitListenerEndpointRegistry.class).getListenerContainers().stream()
                .filter(container -> container.isRunning())
                .count();
    }

    private static long listenerContainers(ConfigurableApplicationContext instance) {
        return instance.getBean(RabbitListenerEndpointRegistry.class).getListenerContainers().size();
    }

    private ConfigurableApplicationContext start(String group) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", environment.getRequiredProperty("spring.datasource.url"));
        properties.put("spring.datasource.username", environment.getRequiredProperty("spring.datasource.username"));
        properties.put("spring.datasource.password", environment.getRequiredProperty("spring.datasource.password"));
        properties.put("spring.rabbitmq.host", broker.getHost());
        properties.put("spring.rabbitmq.port", broker.getAmqpPort());
        properties.put("spring.rabbitmq.username", broker.getAdminUsername());
        properties.put("spring.rabbitmq.password", broker.getAdminPassword());
        properties.put("spring.rabbitmq.ssl.enabled", false);
        properties.put("spring.rabbitmq.listener.simple.auto-startup", true);
        properties.put("projectx.scheduling.enabled", false);
        properties.put("projectx.leadership.group", group);
        properties.put("projectx.leadership.heartbeat", "200ms");
        properties.put("projectx.leadership.lock-ttl", "5s");
        properties.put("server.port", 0);
        properties.put("management.server.port", 0);
        String[] arguments = properties.entrySet().stream()
                .map(property -> "--" + property.getKey() + "=" + property.getValue())
                .toArray(String[]::new);
        return new SpringApplicationBuilder(DataReferenceApplication.class).run(arguments);
    }
}
