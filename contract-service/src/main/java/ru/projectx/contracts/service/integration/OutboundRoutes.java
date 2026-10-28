package ru.projectx.contracts.service.integration;

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

    public OutboundRoutes(@InternalBroker RabbitTemplate internalTemplate, InternalBrokerProperties internal,
            @OneCBroker RabbitTemplate onecTemplate, OneCBrokerProperties onec) {
        this.byEventType = Map.of(
                StatusEvents.CONTRACT_STATUS_CHANGED, new OutboundRoute(internalTemplate, internal.queues().statusChanges()),
                LmPolicyEvents.LM_POLICY_REQUESTED, new OutboundRoute(onecTemplate, onec.queues().lmPolicyRequests()),
                KmContractEvents.KM_CONTRACT_SUBMITTED,
                new OutboundRoute(onecTemplate, onec.queues().kmContractSubmissions()));
    }

    Optional<OutboundRoute> of(String eventType) {
        return Optional.ofNullable(byEventType.get(eventType));
    }
}
