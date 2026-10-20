package ru.projectx.datareference.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static ru.projectx.datareference.support.OneCMessages.messageWithId;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import ru.projectx.datareference.config.OneCQueueProperties;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;
import ru.projectx.datareference.service.integration.OneCQueueConsumers;
import ru.projectx.datareference.support.AmqpIntegrationTest;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.RunningListeners;

@AmqpIntegrationTest
@TestPropertySource(properties = {"projectx.leadership.heartbeat=5s", "projectx.leadership.lock-ttl=60s"})
class LostLockRecoveryTest {

    private static final Duration RECOVERY_TIMEOUT = Duration.ofSeconds(45);
    private static final String DELIVERIES = "onec.ingest.deliveries";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private LeaderLock lock;

    @Autowired
    private BackgroundLeadership leadership;

    @Autowired
    private OneCQueueConsumers consumers;

    @Autowired
    private RabbitListenerEndpointRegistry listeners;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OneCQueueProperties queues;

    @Autowired
    private InboundMessageRepository inboundMessages;

    @Autowired
    private MeterRegistry meters;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void leaderWithRunningConsumers() {
        databaseCleaner.clear();
        RunningListeners.assertAllRunning(listeners);
    }

    @Test
    void lockRowMissingWhileLeading_messageReturnsTheRoleIsRetakenAndConsumersRunAgain() {
        JdbcClient.create(dataSource).sql("delete from leader_lock where region = :group")
                .param("group", lock.group())
                .update();
        UUID messageId = UUID.randomUUID();

        rabbitTemplate.convertAndSend(queues.mkb10(), messageWithId(messageId));

        await().atMost(RECOVERY_TIMEOUT).until(() -> inboundMessages.findById(messageId).isPresent());
        assertThat(returnedDeliveries()).as("доставки, возвращённые в очередь из-за чужой строки блокировки").isPositive();
        await().atMost(RECOVERY_TIMEOUT).untilAsserted(() -> {
            assertThat(leadership.leader()).isTrue();
            assertThat(consumers.queuesWithoutConsumer()).isEmpty();
        });
        assertThat(inboundMessages.count()).isEqualTo(1);
    }

    private double returnedDeliveries() {
        return meters.find(DELIVERIES).tag("outcome", "returned").counters().stream()
                .mapToDouble(counter -> counter.count())
                .sum();
    }
}
