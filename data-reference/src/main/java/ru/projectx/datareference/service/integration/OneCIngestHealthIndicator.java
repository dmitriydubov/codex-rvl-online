package ru.projectx.datareference.service.integration;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Состояние приёма из 1С:УСК в ручке здоровья: компонент {@code onecIngest}.
 *
 * <p>Пока потребители очередей стоят на паузе из-за недоступной базы, компонент отвечает DOWN и называет
 * причину: код SQLSTATE и тип отказа, момент остановки и очереди без потребителей. Так мониторинг отличает
 * «сообщений от 1С нет» от «сообщения ждут в очереди, а сервис их не читает». Как только потребители
 * вернулись — UP.
 *
 * <p>Имя бина задаёт имя компонента: Spring Boot отбрасывает суффикс {@code HealthIndicator}.
 */
@Component("onecIngestHealthIndicator")
public class OneCIngestHealthIndicator implements HealthIndicator {

    private final OneCConsumerPause pause;

    public OneCIngestHealthIndicator(OneCConsumerPause pause) {
        this.pause = pause;
    }

    @Override
    public Health health() {
        return pause.paused()
                .map(OneCIngestHealthIndicator::pausedSince)
                .orElseGet(() -> Health.up().build());
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
