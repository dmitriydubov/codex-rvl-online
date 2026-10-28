package ru.projectx.datareference.service.integration;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.ConsumeOkEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OneCConsumerOutageJournal {

    private static final Logger log = LoggerFactory.getLogger(OneCConsumerOutageJournal.class);

    private final Set<String> queuesWithoutConsumer = ConcurrentHashMap.newKeySet();

    void startFailed(String queue, String reason) {
        if (queuesWithoutConsumer.add(queue)) {
            log.error("потребитель очереди {} не запущен: {}", queue, reason);
            return;
        }
        log.debug("потребитель очереди {} по-прежнему не запущен: {}", queue, reason);
    }

    @EventListener
    public void consumeOk(ConsumeOkEvent subscribed) {
        if (queuesWithoutConsumer.remove(subscribed.getQueue())) {
            log.info("потребитель очереди {} запущен: очередь снова на брокере", subscribed.getQueue());
        }
    }
}
