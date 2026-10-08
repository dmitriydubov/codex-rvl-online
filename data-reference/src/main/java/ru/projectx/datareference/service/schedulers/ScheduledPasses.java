package ru.projectx.datareference.service.schedulers;

import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ImportProperties;

/**
 * Расписание проходов по сырью: разбора, стирания персональных данных и проверки зависших передач прайсов. Паузы
 * берутся из проверенных на старте {@link ImportProperties}, а не из аннотаций, которые явно пустую задержку приняли
 * бы за нулевую. Проход начинается после конца предыдущего, и долгий проход одной задачи не задерживает другие.
 */
@Component
public class ScheduledPasses implements SchedulingConfigurer {

    private final ReferenceImportScheduler importScheduler;
    private final PersonalDataRetention retention;
    private final StuckTransferAlert stuckTransferAlert;
    private final ImportProperties properties;

    public ScheduledPasses(
            ReferenceImportScheduler importScheduler,
            PersonalDataRetention retention,
            StuckTransferAlert stuckTransferAlert,
            ImportProperties properties) {

        this.importScheduler = importScheduler;
        this.retention = retention;
        this.stuckTransferAlert = stuckTransferAlert;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new FixedDelayTask(
                importScheduler::importPendingMessages, properties.pollInterval(), properties.initialDelay()));
        registrar.addFixedDelayTask(new FixedDelayTask(
                retention::forgetExpiredPersonalData, properties.retentionPollInterval(), properties.initialDelay()));
        registrar.addFixedDelayTask(new FixedDelayTask(
                stuckTransferAlert::reportStuckTransfers, properties.stuckTransferPollInterval(),
                properties.initialDelay()));
    }
}
