package ru.projectx.contracts.config.broker;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.brokers.onec")
public record OneCBrokerProperties(
        @Valid @NotNull BrokerConnectionProperties connection,
        boolean listenerAutoStartup,
        @Valid @NotNull Queues queues) implements BrokerProperties {

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
