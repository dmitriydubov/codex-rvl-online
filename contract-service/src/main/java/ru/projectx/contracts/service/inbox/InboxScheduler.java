package ru.projectx.contracts.service.inbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.service.SqlStates;

@Component
public class InboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(InboxScheduler.class);

    private final InboxProcessor processor;
    private final InboxPayloadErasure erasure;

    public InboxScheduler(InboxProcessor processor, InboxPayloadErasure erasure) {
        this.processor = processor;
        this.erasure = erasure;
    }

    @Scheduled(fixedDelayString = "${projectx.inbox.processing.interval}")
    public void processDue() {
        try {
            processor.processDue();
        }
        catch (RuntimeException failure) {
            log.warn("проход обработки входящих сообщений не выполнен: {}, SQLSTATE {}",
                    failure.getClass().getName(), SqlStates.deepestOrNone(failure));
        }
    }

    @Scheduled(fixedDelayString = "${projectx.inbox.retention.interval}")
    public void erasePayloads() {
        try {
            erasure.eraseExpired();
        }
        catch (RuntimeException failure) {
            log.warn("проход стирания тел входящих сообщений не выполнен: {}, SQLSTATE {}",
                    failure.getClass().getName(), SqlStates.deepestOrNone(failure));
        }
    }
}
