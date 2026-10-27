package ru.projectx.contracts.service.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.service.ScheduledPasses;

@Component
public class PayloadRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(PayloadRetentionScheduler.class);

    private final InboxPayloadRetention inbox;
    private final QuarantinePayloadRetention quarantine;
    private final OutboxPayloadRetention outbox;

    public PayloadRetentionScheduler(InboxPayloadRetention inbox, QuarantinePayloadRetention quarantine,
            OutboxPayloadRetention outbox) {
        this.inbox = inbox;
        this.quarantine = quarantine;
        this.outbox = outbox;
    }

    @Scheduled(fixedDelayString = "${projectx.inbox.retention.interval}")
    public void eraseInboxPayloads() {
        ScheduledPasses.run(log, "стирания тел входящих сообщений", inbox::eraseExpired);
        ScheduledPasses.run(log, "стирания тел карантина", quarantine::eraseExpired);
    }

    @Scheduled(fixedDelayString = "${projectx.outbox.retention.interval}")
    public void eraseOutboxPayloads() {
        ScheduledPasses.run(log, "стирания тел исходящих сообщений", outbox::eraseExpired);
    }
}
