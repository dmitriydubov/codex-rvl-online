package ru.projectx.contracts.service.inbox.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.service.ScheduledPasses;

@Component
public class InboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(InboxScheduler.class);

    private final InboxProcessor processor;

    public InboxScheduler(InboxProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${projectx.inbox.processing.interval}")
    public void processDue() {
        ScheduledPasses.run(log, "обработки входящих сообщений", processor::processDue);
    }
}
