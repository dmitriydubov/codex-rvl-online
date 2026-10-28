package ru.projectx.datareference.service.integration;

import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.service.leadership.BackgroundLeadership;

@Component
class OneCConsumerExpectation {

    private final ObjectProvider<RabbitProperties> rabbit;
    private final BackgroundLeadership leadership;

    OneCConsumerExpectation(ObjectProvider<RabbitProperties> rabbit, BackgroundLeadership leadership) {
        this.rabbit = rabbit;
        this.leadership = leadership;
    }

    boolean consumersExpected() {
        return leadership.leader() && consumersEnabled();
    }

    private boolean consumersEnabled() {
        return Optional.ofNullable(rabbit.getIfAvailable())
                .map(properties -> properties.getListener().getSimple().isAutoStartup())
                .orElse(false);
    }
}
