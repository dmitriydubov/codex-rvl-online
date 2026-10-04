package ru.projectx.datareference.service.integration;

import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.IngestProperties;

/**
 * Расписание проверки базы во время паузы приёма из 1С:УСК: раз в заданный интервал сервис смотрит, отвечает
 * ли база, и возвращает потребителей очередей, когда она снова принимает записи.
 *
 * <p>Интервал берётся из настроек приёма, уже проверенных на старте. Пока расписания выключены (так делают
 * тесты), проверку вызывают явно. Вне паузы проверка ничего не делает и к базе не ходит.
 */
@Component
public class OneCConsumerPauseSchedule implements SchedulingConfigurer {

    private final OneCConsumerPause pause;
    private final IngestProperties properties;

    public OneCConsumerPauseSchedule(OneCConsumerPause pause, IngestProperties properties) {
        this.pause = pause;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new FixedDelayTask(
                pause::resumeIfStorageAvailable, properties.storageCheckInterval(), properties.storageCheckInterval()));
    }
}
