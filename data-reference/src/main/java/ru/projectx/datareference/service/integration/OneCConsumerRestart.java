package ru.projectx.datareference.service.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OneCConsumerRestart {

    static final String QUEUE_MISSING = "очереди нет на брокере, попытки запуска продолжаются";

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
        pause.unlessPaused(this::restartEachStopped);
    }

    private void restartEachStopped() {
        for (String queue : containers.stopped()) {
            restart(queue);
        }
    }

    private void restart(String queue) {
        OneCQueuePresence.OnBroker onBroker = presence.of(queue);
        if (onBroker == OneCQueuePresence.OnBroker.PRESENT) {
            containers.start(queue);
            return;
        }
        if (onBroker == OneCQueuePresence.OnBroker.ABSENT) {
            journal.startFailed(queue, QUEUE_MISSING);
            return;
        }
        log.debug("брокер не отвечает, запуск потребителя очереди {} отложен", queue);
    }
}
