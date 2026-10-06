package ru.projectx.datareference.config;

import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Кэш ответов справочных ручек (имена кэшей — {@link ReferenceCaches}). Значения общие для всех кэшей.
 *
 * <p>Главный путь обновления — сброс: применённое сообщение 1С:УСК сбрасывает ответы, которые зависят от его
 * потока. Срок жизни — страховка на случай, когда сброс не помог: правка базы мимо 1С, ошибка сброса или чтение,
 * которое прочитало базу до фиксации изменений, а положило ответ в кэш уже после сброса.
 *
 * @param ttl         сколько ответ живёт в кэше, считая от записи; чтение срок не продлевает. Столько, не больше,
 *                    потребитель может видеть данные, изменённые мимо приёма из 1С
 * @param maximumSize сколько ответов держит каждый кэш. Сверх предела вытесняются те, к которым обращаются реже
 */
@Validated
@ConfigurationProperties("projectx.cache")
public record ReferenceCacheProperties(
        @DefaultValue("10m") @DurationMin(millis = 1) Duration ttl,
        @DefaultValue("1000") @Positive long maximumSize) {
}
