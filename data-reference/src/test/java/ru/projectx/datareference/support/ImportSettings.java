package ru.projectx.datareference.support;

import java.time.Duration;
import ru.projectx.datareference.config.ImportProperties;

public final class ImportSettings {

    private Duration pollInterval = Duration.ofSeconds(10);
    private Duration initialDelay = Duration.ofSeconds(30);
    private int batchSize = 100;
    private Duration stuckTransferThreshold = Duration.ofHours(1);
    private Duration stuckTransferPollInterval = Duration.ofMinutes(15);
    private int stuckTransferReportLimit = 20;
    private Duration payloadTtl = Duration.ofDays(30);
    private Duration retentionPollInterval = Duration.ofHours(1);
    private int retentionBatchSize = 500;
    private Duration quarantineTtl = Duration.ofDays(30);
    private Duration parentWaitTimeout = Duration.ofHours(24);
    private int parentWaitReportLimit = 20;
    private final Duration policyPersonalDataGrace = Duration.ofDays(30);

    private ImportSettings() {
    }

    public static ImportSettings defaults() {
        return new ImportSettings();
    }

    public ImportSettings pollInterval(Duration value) {
        pollInterval = value;
        return this;
    }

    public ImportSettings initialDelay(Duration value) {
        initialDelay = value;
        return this;
    }

    public ImportSettings batchSize(int value) {
        batchSize = value;
        return this;
    }

    public ImportSettings stuckTransferThreshold(Duration value) {
        stuckTransferThreshold = value;
        return this;
    }

    public ImportSettings stuckTransferPollInterval(Duration value) {
        stuckTransferPollInterval = value;
        return this;
    }

    public ImportSettings stuckTransferReportLimit(int value) {
        stuckTransferReportLimit = value;
        return this;
    }

    public ImportSettings payloadTtl(Duration value) {
        payloadTtl = value;
        return this;
    }

    public ImportSettings retentionPollInterval(Duration value) {
        retentionPollInterval = value;
        return this;
    }

    public ImportSettings retentionBatchSize(int value) {
        retentionBatchSize = value;
        return this;
    }

    public ImportSettings quarantineTtl(Duration value) {
        quarantineTtl = value;
        return this;
    }

    public ImportSettings parentWaitTimeout(Duration value) {
        parentWaitTimeout = value;
        return this;
    }

    public ImportSettings parentWaitReportLimit(int value) {
        parentWaitReportLimit = value;
        return this;
    }

    public ImportProperties build() {
        return new ImportProperties(pollInterval, initialDelay, batchSize, stuckTransferThreshold,
                stuckTransferPollInterval, stuckTransferReportLimit, payloadTtl, retentionPollInterval,
                retentionBatchSize, quarantineTtl, parentWaitTimeout, parentWaitReportLimit, policyPersonalDataGrace);
    }
}
