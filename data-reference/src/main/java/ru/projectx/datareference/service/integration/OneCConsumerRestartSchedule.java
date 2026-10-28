package ru.projectx.datareference.service.integration;

import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.IngestProperties;

@Component
public class OneCConsumerRestartSchedule implements SchedulingConfigurer {

    private final OneCConsumerRestart restart;
    private final IngestProperties properties;

    public OneCConsumerRestartSchedule(OneCConsumerRestart restart, IngestProperties properties) {
        this.restart = restart;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new FixedDelayTask(
                restart::restartStopped, properties.consumerRestartInterval(), properties.consumerRestartInterval()));
    }
}
