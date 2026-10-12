package ru.projectx.datareference.config;

import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.onec.ingest")
public record IngestProperties(
        @DefaultValue("5s") Duration duplicateDeliveryBackoff,
        @DefaultValue("10s") @DurationMin(millis = 1) Duration storageCheckInterval) {
}
