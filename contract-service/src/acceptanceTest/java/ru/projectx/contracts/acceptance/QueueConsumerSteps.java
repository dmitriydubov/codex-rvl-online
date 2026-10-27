package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Тогда;
import java.time.Duration;
import org.awaitility.Awaitility;

public class QueueConsumerSteps {

    private static final Duration PATIENCE = Duration.ofSeconds(45);

    private final StandQueues queues;
    private final PublishedMessages published;
    private final StandDatabase database;

    public QueueConsumerSteps(StandQueues queues, PublishedMessages published, StandDatabase database) {
        this.queues = queues;
        this.published = published;
        this.database = database;
    }

    @Тогда("у очереди {string} есть потребитель")
    public void queueHasConsumer(String alias) {
        StandQueue target = queues.called(alias);
        Awaitility.await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(target.state().getConsumerCount()).as("потребителей очереди %s", target.name()).isPositive());
    }

    @Тогда("у очереди {string} нет потребителя")
    public void queueHasNoConsumer(String alias) {
        StandQueue target = queues.called(alias);
        Awaitility.await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(target.state().getConsumerCount()).as("потребителей очереди %s", target.name()).isZero());
    }

    @Тогда("в очереди {string} лежат все опубликованные сообщения и у неё нет потребителя")
    public void queueHoldsEveryPublishedMessageWithoutConsumer(String alias) {
        StandQueue target = queues.called(alias);
        assertThat(published.queuedTo(alias).size() + published.returnedTo(alias))
                .as("в очередь %s за сценарий публиковали", alias).isPositive();
        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> {
            var state = target.state();
            assertThat(state.getConsumerCount()).as("потребителей очереди %s", target.name()).isZero();
            assertThat(state.getMessageCount()).as("сообщений в очереди %s", target.name())
                    .isEqualTo(awaitingReceipt(alias));
        });
    }

    private int awaitingReceipt(String alias) {
        long notYetReceived = published.queuedTo(alias).stream()
                .filter(messageId -> !database.receivedIntoInbox(messageId))
                .count();
        return (int) notYetReceived + published.returnedTo(alias);
    }
}
