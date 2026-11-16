package ru.projectx.contracts.service.payment.event;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.OrderingProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.message.payment.PaymentOrderEvents;
import ru.projectx.contracts.service.inbox.parse.PayloadParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class OrderEventParser implements PayloadParser<OrderEvent> {

    private final ObjectReader json;
    private final String product;

    public OrderEventParser(JsonMapper json, OrderingProperties settings) {
        this.json = json.reader(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.product = settings.product();
    }

    @Override
    public OrderEvent parse(String payload) {
        JsonNode event = read(payload);
        return new OrderEvent(text(event, PaymentOrderEvents.EVENT_TYPE), text(event, PaymentOrderEvents.ORDER_ID),
                OrderEventDocuments.of(event, product), moment(event.path(PaymentOrderEvents.UPDATED).asString("")));
    }

    private JsonNode read(String payload) {
        try {
            return json.readTree(payload);
        }
        catch (JacksonException unreadable) {
            throw ContractViolationException.unreadableBody(unreadable);
        }
    }

    private static Optional<String> text(JsonNode event, String field) {
        return Optional.of(event.path(field))
                .filter(JsonNode::isString)
                .map(JsonNode::asString)
                .filter(value -> !value.isBlank());
    }

    private static Optional<Instant> moment(String text) {
        try {
            return Optional.of(OffsetDateTime.parse(text).toInstant());
        }
        catch (DateTimeException unparsed) {
            return Optional.empty();
        }
    }
}
