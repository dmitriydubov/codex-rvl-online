package ru.projectx.datareference.service.schedulers;

import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ImportProperties;

/**
 * Расписание трёх проходов по таблице сырых сообщений: разбора, стирания персональных данных и проверки
 * зависших передач прайсов.
 *
 * <p>Задачи регистрируются здесь, а не аннотациями на методах, чтобы паузы и начальная задержка брались
 * из {@link ImportProperties} — уже проверенными на старте и с теми же умолчаниями, что видит остальной
 * код. Аннотация читает только строку свойства и, например, явно пустую задержку приняла бы за нулевую.
 *
 * <p>Каждая задача идёт с паузой между проходами: следующий начинается после окончания предыдущего, так что
 * проход сам на себя не накладывается. Потоков в пуле планировщика хватает на все три задачи сразу, и
 * долгий проход одной не задерживает две другие. Пока расписания выключены (так делают тесты), Spring
 * этот класс не вызывает.
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
                retention::forgetExpiredPayloads, properties.retentionPollInterval(), properties.initialDelay()));
        registrar.addFixedDelayTask(new FixedDelayTask(
                stuckTransferAlert::reportStuckTransfers, properties.stuckTransferPollInterval(),
                properties.initialDelay()));
    }
}
