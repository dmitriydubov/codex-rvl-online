package ru.projectx.datareference.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.import")
public record ImportProperties(
        @DefaultValue("10s") @DurationMin(millis = 1) Duration pollInterval,
        @DefaultValue("30s") @DurationMin(millis = 0) Duration initialDelay,
        @DefaultValue("100") @Positive int batchSize,
        @DefaultValue("1h") @DurationMin(millis = 1) Duration stuckTransferThreshold,
        @DefaultValue("15m") @DurationMin(millis = 1) Duration stuckTransferPollInterval,
        @DefaultValue("20") @Positive int stuckTransferReportLimit,
        @DefaultValue("30d") @DurationMin(days = 1) Duration payloadTtl,
        @DefaultValue("1h") @DurationMin(millis = 1) Duration retentionPollInterval,
        @DefaultValue("500") @Positive int retentionBatchSize,
        @DefaultValue("30d") @DurationMin(days = 1) Duration quarantineTtl,
        @DefaultValue("24h") @DurationMin(millis = 1) Duration parentWaitTimeout,
        @DefaultValue("20") @Positive int parentWaitReportLimit,
        @DefaultValue("30d") @DurationMin(days = 1) Duration policyPersonalDataGrace) {

    @AssertTrue(message = "срок ожидания родителя (parent-wait-timeout) должен быть меньше срока хранения тела "
            + "(payload-ttl)")
    public boolean isParentWaitShorterThanPayloadTtl() {
        return parentWaitTimeout.compareTo(payloadTtl) < 0;
    }
}
