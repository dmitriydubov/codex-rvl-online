package ru.projectx.datareference.acceptance;

import static org.awaitility.Awaitility.await;

import io.cucumber.java.After;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.datareference.service.inbound.InboundMessageService;
import ru.projectx.datareference.service.inbound.InboxService;
import ru.projectx.datareference.service.integration.OneCQueueConsumers;
import ru.projectx.datareference.service.leadership.BackgroundLeadership;

public class LeadershipSteps {

    private static final int AFTER_QUEUES_RESTORED = 10;
    private static final Duration ROLE_TIMEOUT = Duration.ofSeconds(45);

    private final LeaderLockStand stand;
    private final OneCBroker broker;
    private final OneCInbox inbox;
    private final InboxService inboxService;
    private final InboundMessageService inboundMessages;
    private final TransactionTemplate transaction;
    private final BackgroundLeadership leadership;
    private final OneCQueueConsumers consumers;

    public LeadershipSteps(LeaderLockStand stand, OneCBroker broker, OneCInbox inbox, InboxService inboxService,
            InboundMessageService inboundMessages, PlatformTransactionManager transactions,
            BackgroundLeadership leadership, OneCQueueConsumers consumers) {

        this.stand = stand;
        this.broker = broker;
        this.inbox = inbox;
        this.inboxService = inboxService;
        this.inboundMessages = inboundMessages;
        this.transaction = new TransactionTemplate(transactions);
        this.leadership = leadership;
        this.consumers = consumers;
    }

    @Дано("другой экземпляр Дата-референса перехватил роль ведущего")
    public void otherInstanceTookOver() {
        stand.takeOver();
    }

    @Когда("другой экземпляр Дата-референса остановился")
    public void otherInstanceStopped() {
        stand.release();
    }

    @Когда("срок блокировки погибшего экземпляра истёк")
    public void deadInstanceLockExpired() {
        stand.expire();
    }

    @Когда("другой экземпляр Дата-референса принял из очереди договор страхования ДМС и погиб, не подтвердив его брокеру:")
    public void otherInstanceStoredAndDied(Map<String, String> contract) {
        OneCMessage message = OneCMessages.insuranceContract(contract);
        transaction.executeWithoutResult(status -> {
            inboxService.registerIfNew(message.messageId());
            inboundMessages.store(message.messageId(), message.stream(), message.queue(), message.envelope());
        });
        inbox.expect(message);
        broker.publish(message.queue(), message.messageId(), message.envelope().getBytes(StandardCharsets.UTF_8));
    }

    @After(order = AFTER_QUEUES_RESTORED)
    public void thisInstanceLeadsAgain() {
        stand.release();
        await().atMost(ROLE_TIMEOUT).until(() -> leadership.leader() && consumers.queuesWithoutConsumer().isEmpty());
    }
}
