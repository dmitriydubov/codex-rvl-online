package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.ObjectProvider;
import ru.projectx.datareference.config.OneCQueueProperties;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OneCQueueConsumersTest {

    private static final OneCQueueProperties QUEUES = new OneCQueueProperties(
            "q-policies", "q-insurance-contracts", "q-contragents", "q-mkb10", "q-lpu-contracts",
            "q-programs", "q-appeal-types", "q-lpu-contacts", "q-price-lists", "q-price-lists-final",
            "q-contragent-services");

    @Mock
    private ObjectProvider<RabbitListenerEndpointRegistry> registryProvider;

    @Mock
    private RabbitListenerEndpointRegistry registry;

    @Mock
    private OneCConsumerSubscriptions subscriptions;

    private OneCQueueConsumers consumers;

    @BeforeEach
    void everyQueueHasAnAutoStartedContainer() {
        consumers = new OneCQueueConsumers(QUEUES, registryProvider, subscriptions);
        when(registryProvider.getIfAvailable()).thenReturn(registry);
        Arrays.stream(OneCQueue.values()).map(queue -> queue.nameIn(QUEUES)).forEach(this::autoStarted);
    }

    @Test
    void everyQueueSubscribed_noQueueWithoutConsumer() {
        subscribed(allQueues());

        assertThat(consumers.queuesWithoutConsumer()).isEmpty();
    }

    @Test
    void nothingSubscribedYet_everyQueueIsWithoutConsumerInNameOrder() {
        assertThat(consumers.queuesWithoutConsumer())
                .hasSize(OneCQueue.values().length)
                .containsExactlyInAnyOrderElementsOf(allQueues())
                .isSorted();
    }

    @Test
    void oneQueueLostItsConsumer_onlyThatQueueIsNamed() {
        subscribed(allQueues());
        when(subscriptions.subscribed("q-policies")).thenReturn(false);

        assertThat(consumers.queuesWithoutConsumer()).containsExactly("q-policies");
    }

    @Test
    void containerDisabledBySettings_itsQueueIsNotExpected() {
        subscribed(allQueues());
        when(subscriptions.subscribed("q-mkb10")).thenReturn(false);
        when(registry.getListenerContainer("q-mkb10").isAutoStartup()).thenReturn(false);

        assertThat(consumers.queuesWithoutConsumer()).isEmpty();
    }

    @Test
    void queueWithoutContainer_isNotExpected() {
        subscribed(allQueues());
        when(subscriptions.subscribed("q-programs")).thenReturn(false);
        when(registry.getListenerContainer("q-programs")).thenReturn(null);

        assertThat(consumers.queuesWithoutConsumer()).isEmpty();
    }

    @Test
    void serviceWithoutBroker_expectsNoConsumers() {
        when(registryProvider.getIfAvailable()).thenReturn(null);

        assertThat(consumers.queuesWithoutConsumer()).isEmpty();
    }

    private void autoStarted(String queue) {
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.isAutoStartup()).thenReturn(true);
        when(registry.getListenerContainer(queue)).thenReturn(container);
    }

    private void subscribed(Set<String> queues) {
        queues.forEach(queue -> when(subscriptions.subscribed(queue)).thenReturn(true));
    }

    private static Set<String> allQueues() {
        return Set.of("q-policies", "q-insurance-contracts", "q-contragents", "q-mkb10", "q-lpu-contracts",
                "q-programs", "q-appeal-types", "q-lpu-contacts", "q-price-lists", "q-price-lists-final",
                "q-contragent-services");
    }
}
