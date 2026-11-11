package ru.projectx.contracts.service;

import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import ru.projectx.contracts.message.payment.PaymentOrderEvents;

enum InboxCorrelation {
    REQUEST {
        @Override
        void put(UUID correlationId) {
            MDC.put(InboxLogContext.INDIVIDUAL_REQUEST_ID, correlationId.toString());
        }
    },
    MESSAGE_KEY;

    private static final Map<String, InboxCorrelation> NOT_REQUESTS =
            Map.of(PaymentOrderEvents.ORDER_EVENT, MESSAGE_KEY);

    static InboxCorrelation of(String eventType) {
        return NOT_REQUESTS.getOrDefault(eventType, REQUEST);
    }

    void put(UUID correlationId) {
    }
}
