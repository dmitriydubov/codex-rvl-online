package ru.projectx.contracts.service.integration.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;
import java.util.Set;
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
                    "km-submissions", "km-registrations", "lm-requests", "lm-issues")), List.of());

    @Test
    void statusChanges_goToTheStatusQueueOfTheInternalBroker() {
        assertThat(routes.of("ContractStatusChanged")).contains(new OutboundRoute(internalTemplate, "", "status-changes"));
    }

    @Test
    void lmPolicyRequests_goToTheRequestQueueOfTheOneCBroker() {
        assertThat(routes.of("LmPolicyRequested")).contains(new OutboundRoute(onecTemplate, "", "lm-requests"));
    }

    @Test
    void kmContractSubmissions_goToTheSubmissionQueueOfTheOneCBroker() {
        assertThat(routes.of("KmContractSubmitted")).contains(new OutboundRoute(onecTemplate, "", "km-submissions"));
    }

    @Test
    void unknownEventType_hasNoRoute() {
        assertThat(routes.of("ContractTaskUpserted")).isEmpty();
    }

    @Test
    void additionalRoute_goesToItsExchangeWithItsRoutingKey() {
        RabbitTemplate marketingTemplate = mock();
        OutboundRoute fixes = new OutboundRoute(marketingTemplate, "rvl.exchange", "rvl-contract-fixed");

        OutboundRoutes withFixes = routesWith(List.of(new AdditionalRoute("PaymentOrderFixed", fixes)));

        assertThat(withFixes.of("PaymentOrderFixed")).contains(fixes);
        assertThat(withFixes.of("ContractStatusChanged"))
                .contains(new OutboundRoute(internalTemplate, "", "status-changes"));
    }

    @Test
    void additionalRouteForAKnownEventType_failsTheStart() {
        AdditionalRoute duplicate = new AdditionalRoute("ContractStatusChanged",
                new OutboundRoute(mock(RabbitTemplate.class), "other.exchange", "other-key"));

        assertThatThrownBy(() -> routesWith(List.of(duplicate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ContractStatusChanged");
    }

    @Test
    void eventTypes_areGroupedByTheBrokerThatCarriesThem() {
        assertThat(routes.eventTypesByBroker()).containsExactlyInAnyOrder(Set.of("ContractStatusChanged"),
                Set.of("KmContractSubmitted", "LmPolicyRequested"));
    }

    @Test
    void additionalRouteOnItsOwnBroker_makesAGroupOfItsOwn() {
        OutboundRoutes withFixes = routesWith(List.of(new AdditionalRoute("PaymentOrderFixed",
                new OutboundRoute(mock(RabbitTemplate.class), "rvl.exchange", "rvl-contract-fixed"))));

        assertThat(withFixes.eventTypesByBroker()).containsExactlyInAnyOrder(Set.of("ContractStatusChanged"),
                Set.of("KmContractSubmitted", "LmPolicyRequested"), Set.of("PaymentOrderFixed"));
    }

    @Test
    void additionalRouteOnAKnownBroker_joinsThatBrokersGroup() {
        OutboundRoutes withFixes = routesWith(List.of(new AdditionalRoute("PaymentOrderFixed",
                new OutboundRoute(internalTemplate, "rvl.exchange", "rvl-contract-fixed"))));

        assertThat(withFixes.eventTypesByBroker()).containsExactlyInAnyOrder(
                Set.of("ContractStatusChanged", "PaymentOrderFixed"), Set.of("KmContractSubmitted", "LmPolicyRequested"));
    }

    @Test
    void groups_cannotBeChangedByTheCaller() {
        assertThatThrownBy(() -> routes.eventTypesByBroker().add(Set.of("ContractTaskUpserted")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private OutboundRoutes routesWith(List<AdditionalRoute> additional) {
        return new OutboundRoutes(internalTemplate,
                new InternalBrokerProperties(connection(), true, new InternalBrokerProperties.Queues("tasks",
                        "status-changes")),
                onecTemplate, new OneCBrokerProperties(connection(), true, new OneCBrokerProperties.Queues(
                        "km-submissions", "km-registrations", "lm-requests", "lm-issues")), additional);
    }

    private static BrokerConnectionProperties connection() {
        return new BrokerConnectionProperties("localhost", 5672, "/", "user", "secret", Duration.ofSeconds(10),
                new BrokerConnectionProperties.Tls(false, true, true));
    }
}
