package ru.projectx.datareference.config;

import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.metrics")
public record MetricsProperties(
        @DefaultValue("30s") @DurationMin(millis = 1) Duration backlogRefreshInterval) {
}
