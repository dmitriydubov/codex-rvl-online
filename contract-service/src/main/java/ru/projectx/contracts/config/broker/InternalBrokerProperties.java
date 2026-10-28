package ru.projectx.contracts.config.broker;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.brokers.internal")
public record InternalBrokerProperties(
        @Valid @NotNull BrokerConnectionProperties connection,
        boolean listenerAutoStartup,
        @Valid @NotNull Queues queues) implements BrokerProperties {

    public record Queues(@NotBlank String taskChanges, @NotBlank String statusChanges) {

        public Queues {
            QueueNames.requireDistinct(taskChanges, statusChanges);
        }
    }
}
