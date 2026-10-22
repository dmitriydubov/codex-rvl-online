package ru.projectx.contracts.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.database.session-timeouts")
public record DatabaseTimeoutsProperties(
        @NotBlank String lockTimeout,
        @NotBlank String statementTimeout,
        @NotBlank String idleInTransactionSessionTimeout) {
}
