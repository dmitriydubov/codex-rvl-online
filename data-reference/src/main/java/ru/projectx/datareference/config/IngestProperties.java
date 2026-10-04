package ru.projectx.datareference.config;

import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Приём сообщений из брокера 1С:УСК.
 *
 * @param duplicateDeliveryBackoff пауза перед возвратом сообщения источнику, когда две доставки одного
 *                сообщения столкнулись на записи и вторую нужно повторить. Без неё брокер отдаёт сообщение
 *                обратно немедленно, и повтор превращается в горячий цикл: десятки повторов в секунду на
 *                очередь, лог, в котором ничего не найти, и нагрузка на чужой брокер. Это единственный случай
 *                возврата сообщения в очередь: недоступная база потребителей не возвращает, а останавливает
 * @param storageCheckInterval как часто, пока потребители очередей стоят на паузе из-за недоступной базы,
 *                сервис проверяет, отвечает ли база снова, чтобы вернуть потребителей. Это и наибольшая
 *                задержка возобновления приёма после восстановления базы
 */
@Validated
@ConfigurationProperties("projectx.onec.ingest")
public record IngestProperties(
        @DefaultValue("5s") Duration duplicateDeliveryBackoff,
        @DefaultValue("10s") @DurationMin(millis = 1) Duration storageCheckInterval) {
}
