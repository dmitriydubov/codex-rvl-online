package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import ru.projectx.contracts.config.BrokerConnectionProperties;
import ru.projectx.contracts.config.InternalBrokerProperties;

class OutboundRoutesTest {

    private final RabbitTemplate internalTemplate = mock();
    private final OutboundRoutes routes = new OutboundRoutes(internalTemplate, new InternalBrokerProperties(
            new BrokerConnectionProperties("localhost", 5672, "/", "user", "secret", Duration.ofSeconds(10),
                    new BrokerConnectionProperties.Tls(false, true, true)),
            true, new InternalBrokerProperties.Queues("tasks", "status-changes")));

    @Test
    void statusChanges_goToTheStatusQueueOfTheInternalBroker() {
        assertThat(routes.of("ContractStatusChanged")).contains(new OutboundRoute(internalTemplate, "status-changes"));
    }

    @Test
    void unknownEventType_hasNoRoute() {
        assertThat(routes.of("ContractTaskUpserted")).isEmpty();
    }
}
