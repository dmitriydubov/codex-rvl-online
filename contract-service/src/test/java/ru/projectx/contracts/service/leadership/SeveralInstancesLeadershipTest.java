package ru.projectx.contracts.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import ru.projectx.contracts.ContractServiceApplication;
import ru.projectx.contracts.config.broker.InternalBrokerProperties;
import ru.projectx.contracts.config.broker.OneCBrokerProperties;
import ru.projectx.contracts.repository.journal.InboxMessageRepository;
import ru.projectx.contracts.service.integration.InternalBroker;
import ru.projectx.contracts.service.integration.OneCBroker;
import ru.projectx.contracts.support.AmqpIntegrationTest;
import ru.projectx.contracts.support.TaskBodies;
import ru.projectx.contracts.support.TestQueues;

@AmqpIntegrationTest
@TestPropertySource(properties = {
        "projectx.brokers.internal.listener-auto-startup=false",
        "projectx.brokers.onec.listener-auto-startup=false"})
class SeveralInstancesLeadershipTest {

    private static final int INSTANCES = 3;
    private static final Duration ROLE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STABLE_FOR = Duration.ofSeconds(3);
    private static final List<String> INHERITED = List.of(
            "spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "projectx.reference.url", "projectx.storage.endpoint", "projectx.storage.bucket",
            "projectx.storage.access-key", "projectx.storage.secret-key");
    private static final List<String> BROKER_SETTINGS = List.of(
            "connection.host", "connection.port", "connection.virtual-host", "connection.username",
            "connection.password", "connection.tls.enabled");

    @Autowired
    private Environment environment;

    @Autowired
    @InternalBroker
    private RabbitTemplate internalTemplate;

    @Autowired
    @InternalBroker
    private TestQueues internalQueues;

    @Autowired
    @OneCBroker
    private TestQueues onecQueues;

    @Autowired
    private InternalBrokerProperties internalBroker;

    @Autowired
    private OneCBrokerProperties onecBroker;

    @Autowired
    private InboxMessageRepository inbox;

    private final List<ConfigurableApplicationContext> instances = new ArrayList<>();

    @BeforeEach
    void startInstancesOnOneDatabaseAndBrokers() {
        inbox.deleteAll();
        internalQueues.purge(internalBroker.queues().taskChanges());
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
    void exactlyOneInstanceLeadsAndConsumesBothBrokers_andAnotherTakesOverWhenItStops() {
        ConfigurableApplicationContext leader = awaitSingleLeader();
        assertOnlyLeaderConsumes(leader);

        leader.close();
        instances.remove(leader);

        ConfigurableApplicationContext successor = awaitSingleLeader();
        assertOnlyLeaderConsumes(successor);
        publishTask();
        await().atMost(ROLE_TIMEOUT).until(() -> inbox.existsByMessageId(UUID.fromString(TaskBodies.MESSAGE_ID)));
        assertThat(inbox.count()).isEqualTo(1);
    }

    private ConfigurableApplicationContext awaitSingleLeader() {
        await().atMost(ROLE_TIMEOUT).until(() -> leaders().size() == 1);
        await().during(STABLE_FOR).atMost(ROLE_TIMEOUT.plus(STABLE_FOR)).until(() -> leaders().size() == 1);
        return leaders().getFirst();
    }

    private void assertOnlyLeaderConsumes(ConfigurableApplicationContext leader) {
        await().atMost(ROLE_TIMEOUT).untilAsserted(() -> {
            assertThat(internalQueues.consumers(internalBroker.queues().taskChanges())).isEqualTo(1);
            assertThat(onecQueues.consumers(onecBroker.queues().lmPolicyIssues())).isEqualTo(1);
            assertThat(onecQueues.consumers(onecBroker.queues().kmContractRegistrations())).isEqualTo(1);
        });
        assertThat(runningContainers(leader)).isEqualTo(listenerContainers(leader));
        instances.stream().filter(instance -> instance != leader)
                .forEach(standby -> assertThat(runningContainers(standby)).isZero());
    }

    private List<ConfigurableApplicationContext> leaders() {
        return instances.stream()
                .filter(instance -> instance.getBean(BackgroundLeadership.class).leader())
                .toList();
    }

    private void publishTask() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        internalTemplate.send("", internalBroker.queues().taskChanges(),
                MessageBuilder.withBody(TaskBodies.upserted().toBytes()).andProperties(properties).build());
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
        INHERITED.forEach(name -> properties.put(name, environment.getRequiredProperty(name)));
        for (String broker : List.of("internal", "onec")) {
            BROKER_SETTINGS.forEach(setting -> properties.put("projectx.brokers." + broker + "." + setting,
                    environment.getRequiredProperty("projectx.brokers." + broker + "." + setting)));
            properties.put("projectx.brokers." + broker + ".listener-auto-startup", true);
        }
        properties.put("projectx.scheduling.enabled", false);
        properties.put("management.health.brokers.enabled", false);
        properties.put("projectx.leadership.group", group);
        properties.put("projectx.leadership.heartbeat", "200ms");
        properties.put("projectx.leadership.lock-ttl", "5s");
        properties.put("server.port", 0);
        properties.put("management.server.port", 0);
        String[] arguments = properties.entrySet().stream()
                .map(property -> "--" + property.getKey() + "=" + property.getValue())
                .toArray(String[]::new);
        return new SpringApplicationBuilder(ContractServiceApplication.class).run(arguments);
    }
}
