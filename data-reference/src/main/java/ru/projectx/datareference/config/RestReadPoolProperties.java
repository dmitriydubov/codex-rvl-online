package ru.projectx.datareference.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Отдельный пул соединений REST-чтений: всплеск запросов соседей занимает только его, и приёму сообщений 1С:УСК
 * с разбором соединения остаются всегда.
 */
@Validated
@ConfigurationProperties("projectx.database.rest-read-pool")
public record RestReadPoolProperties(
        // Столько REST-чтений идут к базе одновременно, остальные ждут соединения.
        @Min(1) int maximumPoolSize,
        // Сколько ждать свободного соединения до ответа 503; меньше 250 мс пул не принимает.
        @NotNull @DurationMin(millis = 250) Duration connectionTimeout,
        // Сетевой тайм-аут драйвера обрывает чтение, когда база зависла и не исполняет даже отмену по лимиту
        // REST-чтения. Он короткий, а у пула приложения длинный, потому что разбор ведёт долгие операции.
        @Min(1) int socketTimeoutSeconds,
        // Сколько драйвер ждёт отправки отмены запроса, прежде чем вернуть ошибку; без предела к ответу прибавились бы
        // 10 с драйвера по умолчанию. Лимит чтения вместе с этим пределом не должен превышать сетевой тайм-аут.
        @Min(1) int cancelSignalTimeoutSeconds) {
}
