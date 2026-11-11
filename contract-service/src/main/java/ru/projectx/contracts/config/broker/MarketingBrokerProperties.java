package ru.projectx.contracts.config.broker;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import ru.projectx.contracts.config.payment.PaymentSwitch;

@Validated
@ConfigurationProperties("projectx.brokers.marketing")
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public record MarketingBrokerProperties(
        @Valid @NotNull BrokerConnectionProperties connection,
        boolean listenerAutoStartup,
        @Valid @NotNull DocumentFixes documentFixes,
        @Valid @NotNull OrderEvents orderEvents) {

    public record OrderEvents(@NotBlank String queue) {
    }

    public record DocumentFixes(@NotBlank String exchange, @NotBlank String routingKey) {
    }
}
