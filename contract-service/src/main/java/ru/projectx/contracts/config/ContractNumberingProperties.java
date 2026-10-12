package ru.projectx.contracts.config;

import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.contracts.numbering")
public record ContractNumberingProperties(@NotNull ZoneId zone) {
}
