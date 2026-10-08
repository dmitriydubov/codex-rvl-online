package ru.projectx.datareference.config;

import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки кэша ответов справочных ручек, общие для всех кэшей. Главное обновление — сброс по применённому сообщению
 * 1С:УСК, а срок жизни страхует от правок базы мимо 1С и от чтения, положившего старый ответ в кэш после сброса.
 */
@Validated
@ConfigurationProperties("projectx.cache")
public record ReferenceCacheProperties(
        // Срок считается от записи, чтение его не продлевает: столько, не больше, потребитель видит данные, изменённые
        // мимо приёма из 1С.
        @DefaultValue("10m") @DurationMin(millis = 1) Duration ttl,
        // Предел каждого кэша; сверх него вытесняются ответы, которые читают реже.
        @DefaultValue("1000") @Positive long maximumSize) {
}
