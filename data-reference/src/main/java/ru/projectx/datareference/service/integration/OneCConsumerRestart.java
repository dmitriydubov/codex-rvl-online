package ru.projectx.datareference.service.integration;

import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OneCConsumerRestart {

    static final String QUEUE_MISSING = "очереди нет на брокере, попытки запуска продолжаются";
    static final String QUEUE_REFUSED = "брокер отказал в доступе к очереди, попытки запуска продолжаются";

    private static final Logger log = LoggerFactory.getLogger(OneCConsumerRestart.class);

    private final OneCConsumerPause pause;
    private final OneCQueueContainers containers;
    private final OneCQueuePresence presence;
    private final OneCConsumerOutageJournal journal;

    OneCConsumerRestart(OneCConsumerPause pause, OneCQueueContainers containers, OneCQueuePresence presence,
            OneCConsumerOutageJournal journal) {

        this.pause = pause;
        this.containers = containers;
        this.presence = presence;
        this.journal = journal;
    }

    public void restartStopped() {
        if (pause.paused().isPresent()) {
            return;
        }
        Set<String> backOnBroker = queuesBackOnBroker(containers.stopped());
        if (!backOnBroker.isEmpty()) {
            pause.unlessPaused(() -> startStillStopped(backOnBroker));
        }
    }

    private Set<String> queuesBackOnBroker(Set<String> stopped) {
        Set<String> backOnBroker = new LinkedHashSet<>();
        for (String queue : stopped) {
            OneCQueuePresence.OnBroker onBroker = presence.of(queue);
            if (onBroker == OneCQueuePresence.OnBroker.UNREACHABLE) {
                log.debug("брокер не отвечает, запуск остановленных потребителей отложен до следующего прохода");
                return backOnBroker;
            }
            noteOutcome(queue, onBroker, backOnBroker);
        }
        return backOnBroker;
    }

    private void noteOutcome(String queue, OneCQueuePresence.OnBroker onBroker, Set<String> backOnBroker) {
        switch (onBroker) {
            case PRESENT -> backOnBroker.add(queue);
            case ABSENT -> journal.startFailed(queue, QUEUE_MISSING);
            case REFUSED -> journal.startFailed(queue, QUEUE_REFUSED);
            default -> throw new IllegalStateException("брокер не отвечает: запуск потребителя не планируется");
        }
    }

    private void startStillStopped(Set<String> backOnBroker) {
        for (String queue : containers.stopped()) {
            if (backOnBroker.contains(queue)) {
                containers.start(queue);
            }
        }
    }
}
