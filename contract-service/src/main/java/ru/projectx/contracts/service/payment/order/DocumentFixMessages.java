package ru.projectx.contracts.service.payment.order;

import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.OrderingProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.outbox.OutboxDraft;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.message.payment.PaymentOrderEvents;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class DocumentFixMessages {

    private static final int FIRST_VERSION = 1;

    private final JsonMapper json;
    private final String product;

    public DocumentFixMessages(JsonMapper json, OrderingProperties settings) {
        this.json = json;
        this.product = settings.product();
    }

    public OutboxDraft draft(Contract contract, PaymentOrder order, Instant now) {
        MessageEnvelope envelope = new MessageEnvelope(UUID.randomUUID(),
                contract.state().request().individualRequestId(), PaymentOrderEvents.DOCUMENT_FIXED_TOPIC,
                PaymentOrderEvents.PAYMENT_ORDER_FIXED, FIRST_VERSION);
        return new OutboxDraft(PaymentOrderEvents.AGGREGATE_TYPE, order.view().id(), envelope, payload(order));
    }

    private String payload(PaymentOrder order) {
        ObjectNode body = json.createObjectNode();
        body.put(PaymentOrderEvents.SOURCE, product);
        body.put(PaymentOrderEvents.EVENT_TYPE, PaymentOrderEvents.FIXED);
        body.putObject(PaymentOrderEvents.DATA).put(PaymentOrderEvents.AGREEMENT_ID, order.view().id().toString());
        return json.writeValueAsString(body);
    }
}
