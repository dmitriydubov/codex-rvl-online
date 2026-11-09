package ru.projectx.contracts.config.payment;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.payment")
public record PaymentProperties(@NotNull Boolean enabled) {
}
