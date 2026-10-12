package ru.projectx.contracts.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Брокер 1С:УСК: туда уходят оплаченные договоры КМ и запросы полисов ЛМ, оттуда приходят результаты регистрации
 * и выпуска. Очереди заводит 1С, сервис их не объявляет; имена — адреса контура без умолчаний в коде.
 */
@Validated
@ConfigurationProperties("projectx.brokers.onec")
public record OneCBrokerProperties(
        @Valid @NotNull BrokerConnectionProperties connection,
        boolean listenerAutoStartup,
        @Valid @NotNull Queues queues) implements BrokerProperties {

    /**
     * Две независимые пары очередей: договор КМ (отправка и ответ о регистрации) и полис ЛМ (запрос и ответ о выпуске).
     * Имена должны быть разными.
     */
    public record Queues(
            @NotBlank String kmContractSubmissions,
            @NotBlank String kmContractRegistrations,
            @NotBlank String lmPolicyRequests,
            @NotBlank String lmPolicyIssues) {

        public Queues {
            QueueNames.requireDistinct(kmContractSubmissions, kmContractRegistrations, lmPolicyRequests, lmPolicyIssues);
        }
    }
}
