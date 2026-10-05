package ru.projectx.datareference.service.integration;

import java.util.List;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Состояние приёма из 1С:УСК в ручке здоровья: компонент {@code onecIngest}.
 *
 * <p>Компонент отвечает UP, только когда у каждой очереди 1С:УСК, которую сервису велено читать, есть потребитель,
 * подписку которого подтвердил брокер. Так мониторинг отличает «сообщений от 1С нет» от «сообщения ждут в очереди,
 * а сервис их не читает».
 *
 * <p>Пока потребители стоят на паузе из-за недоступной базы, компонент отвечает DOWN и называет причину паузы: код
 * SQLSTATE и тип отказа, момент остановки и остановленные очереди. Вне паузы очередь без потребителя тоже даёт DOWN,
 * а в деталях перечислены такие очереди. Это и окно до первого запуска потребителей при старте сервиса, и
 * возобновление после паузы, пока потребители ещё подписываются, и потребитель, который не поднялся после паузы или
 * потерял очередь на брокере: сам сервис его не перезапускает, вернёт его перезапуск сервиса.
 *
 * <p>Имя бина задаёт имя компонента: Spring Boot отбрасывает суффикс {@code HealthIndicator}.
 */
@Component("onecIngestHealthIndicator")
public class OneCIngestHealthIndicator implements HealthIndicator {

    private static final String QUEUES_WITHOUT_CONSUMER = "queuesWithoutConsumer";

    private final OneCConsumerPause pause;
    private final OneCQueueConsumers consumers;

    public OneCIngestHealthIndicator(OneCConsumerPause pause, OneCQueueConsumers consumers) {
        this.pause = pause;
        this.consumers = consumers;
    }

    @Override
    public Health health() {
        return pause.paused()
                .map(OneCIngestHealthIndicator::pausedSince)
                .orElseGet(this::consumersHealth);
    }

    private Health consumersHealth() {
        List<String> withoutConsumer = consumers.queuesWithoutConsumer();
        if (withoutConsumer.isEmpty()) {
            return Health.up().build();
        }
        return Health.down().withDetail(QUEUES_WITHOUT_CONSUMER, withoutConsumer).build();
    }

    private static Health pausedSince(OneCConsumerPause.Paused paused) {
        return Health.down()
                .withDetail("sqlstate", paused.outage().sqlState())
                .withDetail("failure", paused.outage().failureType())
                .withDetail("since", paused.since().toString())
                .withDetail("queues", paused.queues())
                .build();
    }
}
