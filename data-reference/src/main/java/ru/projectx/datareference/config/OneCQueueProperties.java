package ru.projectx.datareference.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Имена очередей брокера 1С:УСК, по одной на поток, у прайсов две. Умолчаний нет: имя — адрес из контура, его списывают
 * со стенда, а неверное имя роняет запуск ({@code missing-queues-fatal}), а не даёт пустой справочник через неделю.
 * Имена должны быть разными: имя служит и идентификатором точки приёма, и повтор тоже роняет запуск.
 */
@Validated
@ConfigurationProperties("projectx.onec.queues")
public record OneCQueueProperties(
        @NotBlank String policies,
        @NotBlank String insuranceContracts,
        @NotBlank String contragents,
        @NotBlank String mkb10,
        @NotBlank String lpuContracts,
        @NotBlank String programs,
        @NotBlank String appealTypes,
        @NotBlank String lpuContacts,
        @NotBlank String priceLists,
        @NotBlank String priceListsFinal,
        @NotBlank String contragentServices) {
}
