package ru.projectx.contracts.service.integration.broker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.broker.InternalBrokerProperties;
import ru.projectx.contracts.config.broker.OneCBrokerProperties;
import ru.projectx.contracts.message.usk.KmContractEvents;
import ru.projectx.contracts.message.usk.LmPolicyEvents;
import ru.projectx.contracts.message.status.StatusEvents;

@Component
public class OutboundRoutes {

    private final Map<String, OutboundRoute> byEventType;

    OutboundRoutes(@InternalBroker RabbitTemplate internalTemplate, InternalBrokerProperties internal,
            @OneCBroker RabbitTemplate onecTemplate, OneCBrokerProperties onec, List<AdditionalRoute> additional) {
        Map<String, OutboundRoute> routes = new HashMap<>(Map.of(
                StatusEvents.CONTRACT_STATUS_CHANGED,
                OutboundRoute.toQueue(internalTemplate, internal.queues().statusChanges()),
                LmPolicyEvents.LM_POLICY_REQUESTED, OutboundRoute.toQueue(onecTemplate, onec.queues().lmPolicyRequests()),
                KmContractEvents.KM_CONTRACT_SUBMITTED,
                OutboundRoute.toQueue(onecTemplate, onec.queues().kmContractSubmissions())));
        for (AdditionalRoute route : additional) {
            if (routes.putIfAbsent(route.eventType(), route.route()) != null) {
                throw new IllegalStateException("для типа события " + route.eventType() + " задано два маршрута");
            }
        }
        this.byEventType = Map.copyOf(routes);
    }

    Optional<OutboundRoute> of(String eventType) {
        return Optional.ofNullable(byEventType.get(eventType));
    }
}
