package ru.projectx.contracts.service.integration.listener;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.OrderingProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.exception.InboxEnvelopeException;
import ru.projectx.contracts.message.payment.PaymentOrderEvents;
import ru.projectx.contracts.service.Guids;
import ru.projectx.contracts.service.payment.event.OrderEventDocuments;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class OrderEventEnvelopes implements InboxEnvelopes {

    static final String NO_EVENT_ID = "в событии нет eventId";
    private static final String EVENT_ID = "eventId";
    private static final int NO_VERSION = 0;

    private final JsonMapper json;
    private final String product;

    public OrderEventEnvelopes(JsonMapper json, OrderingProperties settings) {
        this.json = json;
        this.product = settings.product();
    }

    @Override
    public TaskEnvelope parse(byte[] body) {
        JsonNode event = EnvelopeBodies.read(json, body);
        UUID messageId = messageId(event.path(EVENT_ID));
        UUID correlationId = OrderEventDocuments.ourDocumentId(event, product).orElse(messageId);
        InboxReceipt receipt = new InboxReceipt(messageId, correlationId, PaymentOrderEvents.ORDER_EVENTS_TOPIC,
                PaymentOrderEvents.ORDER_EVENT, NO_VERSION);
        return EnvelopeBodies.envelope(event, receipt);
    }

    private static UUID messageId(JsonNode eventId) {
        if (!eventId.isString() || eventId.stringValue().isBlank()) {
            throw new InboxEnvelopeException(NO_EVENT_ID, InboxEnvelopeException.UNUSABLE_ENVELOPE);
        }
        String id = eventId.stringValue();
        return Guids.canonical(id).orElseGet(() -> UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)));
    }
}
