package ru.projectx.datareference.service.integration;

import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.IngestProperties;

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
