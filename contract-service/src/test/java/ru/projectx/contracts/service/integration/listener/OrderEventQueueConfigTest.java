package ru.projectx.contracts.service.integration.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import ru.projectx.contracts.config.broker.BrokerConnectionProperties;
import ru.projectx.contracts.config.broker.MarketingBrokerProperties;
import ru.projectx.contracts.config.payment.OrderingProperties;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.service.inbox.InboxMetrics;
import ru.projectx.contracts.service.inbox.receipt.InboxReceiver;
import ru.projectx.contracts.service.inbox.receipt.ReceiptOutcome;
import ru.projectx.contracts.service.integration.consumer.AdditionalInboxQueue;
import tools.jackson.databind.json.JsonMapper;

class OrderEventQueueConfigTest {

    private static final String QUEUE = "order.event.rvl.topic";
    private static final String EVENT = "{\"eventId\":\"d1a0c001-5e2f-4a3b-8c4d-000000000001\",\"orderDetails\":"
            + "[{\"product\":\"rvl\",\"documentId\":\"0196f0a2-0000-7000-8000-000000000011\"}]}";

    private final OrderEventQueueConfig config = new OrderEventQueueConfig();
    private final JsonMapper json = JsonMapper.builder().build();
    private final InboxReceiver receiver = mock();

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void orderEventsQueue_isNamedBySettingsAndConsumedByTheListenerSwitchOfTheMarketingBroker(boolean listening) {
        assertThat(config.orderEventsQueue(marketing(listening))).isEqualTo(new AdditionalInboxQueue(QUEUE, listening));
    }

    @Test
    void orderEventsEndpoint_readsTheQueueOnTheMarketingBrokerFactory() {
        SimpleRabbitListenerContainerFactory marketingFactory = new SimpleRabbitListenerContainerFactory();

        AdditionalInboxEndpoint endpoint =
                config.orderEventsEndpoint(marketing(true), marketingFactory, taskListener(), orderEvents());

        assertThat(endpoint.queue()).isEqualTo(QUEUE);
        assertThat(endpoint.containerFactory()).isSameAs(marketingFactory);
    }

    @Test
    void orderEventsEndpoint_storesAnEventAsAPaymentOrderEventCorrelatedByItsDocument() throws IOException {
        InboxReceipt receipt = new InboxReceipt(UUID.fromString("d1a0c001-5e2f-4a3b-8c4d-000000000001"),
                UUID.fromString("0196f0a2-0000-7000-8000-000000000011"), "ordering.order.events", "PaymentOrderEvent", 0);
        given(receiver.receive(any(), any())).willReturn(ReceiptOutcome.STORED);
        Channel channel = mock();
        AdditionalInboxEndpoint endpoint = config.orderEventsEndpoint(marketing(true),
                new SimpleRabbitListenerContainerFactory(), taskListener(), orderEvents());

        ((InboxQueueListener) endpoint.listener()).onMessage(message(EVENT), channel);

        verify(receiver).receive(receipt, EVENT);
        verify(channel).basicAck(7L, false);
    }

    private InboxQueueListener taskListener() {
        return new InboxQueueListener(new TaskEnvelopeParser(json), receiver, mock(InboxStorageFailures.class),
                mock(InboxMetrics.class));
    }

    private OrderEventEnvelopes orderEvents() {
        return new OrderEventEnvelopes(json, new OrderingProperties("http://ordering.test", Duration.ofSeconds(1),
                Duration.ofSeconds(1), "rvl", "rvl-lk", "notActual", new OrderingProperties.Retry(3, Duration.ZERO),
                new OrderingProperties.Document(ZoneId.of("Europe/Moscow"), Duration.ofSeconds(1)),
                new OrderingProperties.Token("http://keycloak.test/token", "contracts-stand", "secret",
                        Duration.ofSeconds(1), Duration.ofSeconds(1))));
    }

    private static MarketingBrokerProperties marketing(boolean listening) {
        return new MarketingBrokerProperties(new BrokerConnectionProperties("marketing", 5672, "/", "user", "secret",
                Duration.ofSeconds(10), new BrokerConnectionProperties.Tls(false, true, true)), listening,
                new MarketingBrokerProperties.DocumentFixes("rvl.exchange", "rvl-contract-fixed"),
                new MarketingBrokerProperties.OrderEvents(QUEUE));
    }

    private static Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue(QUEUE);
        properties.setDeliveryTag(7L);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
