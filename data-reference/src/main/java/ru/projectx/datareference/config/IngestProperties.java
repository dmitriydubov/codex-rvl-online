package ru.projectx.datareference.config;

import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Настройки приёма сообщений из брокера 1С:УСК. */
@Validated
@ConfigurationProperties("projectx.onec.ingest")
public record IngestProperties(
        // Пауза перед возвратом сообщения в очередь, когда две доставки одного сообщения столкнулись при записи.
        // Без неё брокер отдаёт сообщение сразу, и повтор становится горячим циклом с нагрузкой на чужой брокер.
        // Других возвратов в очередь нет: при недоступной базе потребители не возвращают сообщения, а останавливаются.
        @DefaultValue("5s") Duration duplicateDeliveryBackoff,
        // Как часто проверяется, ожила ли база, пока потребители стоят из-за её недоступности. Это же наибольшая
        // задержка возобновления приёма после восстановления базы.
        @DefaultValue("10s") @DurationMin(millis = 1) Duration storageCheckInterval) {
}
