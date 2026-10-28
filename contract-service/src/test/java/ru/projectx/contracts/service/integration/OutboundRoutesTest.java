package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import ru.projectx.contracts.config.broker.BrokerConnectionProperties;
import ru.projectx.contracts.config.broker.InternalBrokerProperties;
import ru.projectx.contracts.config.broker.OneCBrokerProperties;

class OutboundRoutesTest {

    private final RabbitTemplate internalTemplate = mock();
    private final RabbitTemplate onecTemplate = mock();
    private final OutboundRoutes routes = new OutboundRoutes(internalTemplate,
            new InternalBrokerProperties(connection(), true, new InternalBrokerProperties.Queues("tasks", "status-changes")),
            onecTemplate, new OneCBrokerProperties(connection(), true, new OneCBrokerProperties.Queues(
                    "km-submissions", "km-registrations", "lm-requests", "lm-issues")));

    @Test
    void statusChanges_goToTheStatusQueueOfTheInternalBroker() {
        assertThat(routes.of("ContractStatusChanged")).contains(new OutboundRoute(internalTemplate, "status-changes"));
    }

    @Test
    void lmPolicyRequests_goToTheRequestQueueOfTheOneCBroker() {
        assertThat(routes.of("LmPolicyRequested")).contains(new OutboundRoute(onecTemplate, "lm-requests"));
    }

    @Test
    void kmContractSubmissions_goToTheSubmissionQueueOfTheOneCBroker() {
        assertThat(routes.of("KmContractSubmitted")).contains(new OutboundRoute(onecTemplate, "km-submissions"));
    }

    @Test
    void unknownEventType_hasNoRoute() {
        assertThat(routes.of("ContractTaskUpserted")).isEmpty();
    }

    private static BrokerConnectionProperties connection() {
        return new BrokerConnectionProperties("localhost", 5672, "/", "user", "secret", Duration.ofSeconds(10),
                new BrokerConnectionProperties.Tls(false, true, true));
    }
}
