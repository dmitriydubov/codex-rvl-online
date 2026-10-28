package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.contracts.config.broker.InternalBrokerProperties;
import ru.projectx.contracts.entity.inbox.InboxStatus;
import ru.projectx.contracts.repository.journal.InboxMessageRepository;
import ru.projectx.contracts.service.integration.broker.InternalBroker;
import ru.projectx.contracts.support.AmqpIntegrationTest;
import ru.projectx.contracts.support.TaskBodies;
import ru.projectx.contracts.support.TestQueues;

@AmqpIntegrationTest
class InboxQueueListenerIT {

    private static final Duration PATIENCE = Duration.ofSeconds(15);

    @Autowired
    @InternalBroker
    private RabbitTemplate internalTemplate;

    @Autowired
    private InternalBrokerProperties broker;

    @Autowired
    private InboxMessageRepository messages;

    @Autowired
    @InternalBroker
    private TestQueues queues;

    @BeforeEach
    void emptyJournalAndQueue() {
        messages.deleteAll();
        queues.purge(broker.queues().taskChanges());
    }

    @Test
    void publishedTask_landsInTheJournalOnceAndLeavesTheQueue() {
        publish(TaskBodies.upserted().toBytes());
        publish(TaskBodies.upserted().toBytes());

        Awaitility.await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(messages.existsByMessageId(UUID.fromString(TaskBodies.MESSAGE_ID))).isTrue());
        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> assertThat(queueDepth()).isZero());
        assertThat(messages.findAll()).singleElement().satisfies(message -> {
            assertThat(message.status()).isEqualTo(InboxStatus.NEW);
            assertThat(message.receipt().eventType()).isEqualTo("ContractTaskUpserted");
        });
    }

    @Test
    void messageWithoutUsableEnvelope_leavesTheQueueWithoutAJournalEntry() {
        publish(TaskBodies.upserted().withoutMetaField("message_id").toBytes());

        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> assertThat(queueDepth()).isZero());
        assertThat(messages.count()).isZero();
    }

    private void publish(byte[] body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        internalTemplate.send("", broker.queues().taskChanges(), MessageBuilder.withBody(body).andProperties(properties).build());
    }

    private long queueDepth() {
        return internalTemplate.execute(channel -> channel.messageCount(broker.queues().taskChanges()));
    }
}
