package ru.projectx.datareference.service.schedulers;

import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.service.leadership.BackgroundLeadership;

@Component
public class ScheduledPasses implements SchedulingConfigurer {

    private final ReferenceImportScheduler importScheduler;
    private final PersonalDataRetention retention;
    private final StuckTransferAlert stuckTransferAlert;
    private final ImportProperties properties;
    private final BackgroundLeadership leadership;

    public ScheduledPasses(
            ReferenceImportScheduler importScheduler,
            PersonalDataRetention retention,
            StuckTransferAlert stuckTransferAlert,
            ImportProperties properties,
            BackgroundLeadership leadership) {

        this.importScheduler = importScheduler;
        this.retention = retention;
        this.stuckTransferAlert = stuckTransferAlert;
        this.properties = properties;
        this.leadership = leadership;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new FixedDelayTask(
                this::importOnSchedule, properties.pollInterval(), properties.initialDelay()));
        registrar.addFixedDelayTask(new FixedDelayTask(
                this::retentionOnSchedule, properties.retentionPollInterval(), properties.initialDelay()));
        registrar.addFixedDelayTask(new FixedDelayTask(
                this::stuckTransferCheckOnSchedule, properties.stuckTransferPollInterval(), properties.initialDelay()));
    }

    public void importOnSchedule() {
        leadership.runAsLeader(importScheduler::importPendingMessages);
    }

    public void retentionOnSchedule() {
        leadership.runAsLeader(retention::forgetExpiredPersonalData);
    }

    public void stuckTransferCheckOnSchedule() {
        leadership.runAsLeader(stuckTransferAlert::reportStuckTransfers);
    }
}
