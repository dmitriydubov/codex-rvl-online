package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.contracts.config.InternalBrokerProperties;
import ru.projectx.contracts.service.outbox.OutboundMessage;
import ru.projectx.contracts.service.outbox.PublishOutcome;
import ru.projectx.contracts.support.AmqpIntegrationTest;
import ru.projectx.contracts.support.TestQueues;

@AmqpIntegrationTest
class BrokerPublisherIT {

    private static final UUID MESSAGE_ID = UUID.fromString("7d2a41c9-9f01-4f77-8a1c-2b9cf0e5a442");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final OutboundMessage STATUS = new OutboundMessage(MESSAGE_ID, REQUEST, "ContractStatusChanged",
            "{\"data\": {\"status\": \"COMPLETED\"}}");

    @Autowired
    private BrokerPublisher publisher;

    @Autowired
    @InternalBroker
    private RabbitTemplate internalTemplate;

    @Autowired
    private InternalBrokerProperties broker;

    @Autowired
    private TestQueues queues;

    @BeforeEach
    void emptyStatusQueue() {
        queues.declare(broker.queues().statusChanges()).purge(broker.queues().statusChanges());
    }

    @AfterEach
    void restoreStatusQueue() {
        queues.declare(broker.queues().statusChanges());
    }

    @Test
    void messageToAnExistingQueue_isDeliveredAndLandsThereUnderItsMessageId() {
        PublishOutcome outcome = publisher.publish(STATUS);

        assertThat(outcome).isEqualTo(new PublishOutcome.Delivered());
        Message received = internalTemplate.receive(broker.queues().statusChanges());
        assertThat(received).isNotNull();
        assertThat(new String(received.getBody(), StandardCharsets.UTF_8)).isEqualTo(STATUS.payload());
        assertThat(received.getMessageProperties().getMessageId()).isEqualTo(MESSAGE_ID.toString());
        assertThat(received.getMessageProperties().getReceivedDeliveryMode().name()).isEqualTo("PERSISTENT");
    }

    @Test
    void messageToAMissingQueue_isRefusedAsReturnedWithNoRoute() {
        queues.delete(broker.queues().statusChanges());

        PublishOutcome outcome = publisher.publish(STATUS);

        assertThat(outcome).isEqualTo(new PublishOutcome.Refused("брокер вернул сообщение: NO_ROUTE (312)"));
    }
}
