package ru.projectx.contracts.config.broker;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

public record BrokerConnectionProperties(
        @NotBlank String host,
        @Min(MIN_PORT) @Max(MAX_PORT) int port,
        @NotBlank String virtualHost,
        @NotBlank String username,
        @NotBlank String password,
        @NotNull Duration connectionTimeout,
        @Valid @NotNull @DefaultValue Tls tls) {

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65_535;

    public record Tls(
            boolean enabled,
            @DefaultValue("true") boolean validateServerCertificate,
            @DefaultValue("true") boolean verifyHostname) {
    }
}
