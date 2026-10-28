package ru.projectx.contracts.service.integration.consumer;

import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.journal.InboxProperties;

@Component
public class InboxConsumerPauseSchedule implements SchedulingConfigurer {

    private final InboxConsumerPause pause;
    private final InboxProperties properties;

    public InboxConsumerPauseSchedule(InboxConsumerPause pause, InboxProperties properties) {
        this.pause = pause;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new FixedDelayTask(pause::resumeIfStorageAvailable,
                properties.pause().storageCheckInterval(), properties.pause().storageCheckInterval()));
    }
}
