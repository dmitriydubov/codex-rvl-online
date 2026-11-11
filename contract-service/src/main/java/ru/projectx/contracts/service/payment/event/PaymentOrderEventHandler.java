package ru.projectx.contracts.service.payment.event;

import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.message.payment.PaymentOrderEvents;
import ru.projectx.contracts.service.inbox.DecidingInboxWork;
import ru.projectx.contracts.service.inbox.InboxEventHandler;
import ru.projectx.contracts.service.inbox.InboxOrdering;
import ru.projectx.contracts.service.inbox.InboxWork;
import ru.projectx.contracts.service.payment.PaymentMetrics;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentOrderEventHandler implements InboxEventHandler {

    static final String NOT_APPLIED = "событие Ordering не применено: {}";
    private static final Logger log = LoggerFactory.getLogger(PaymentOrderEventHandler.class);

    private final OrderEventParser parser;
    private final OrderEventApplication application;
    private final PaymentMetrics metrics;

    public PaymentOrderEventHandler(OrderEventParser parser, OrderEventApplication application,
            PaymentMetrics metrics) {
        this.parser = parser;
        this.application = application;
        this.metrics = metrics;
    }

    @Override
    public String eventType() {
        return PaymentOrderEvents.ORDER_EVENT;
    }

    @Override
    public InboxOrdering ordering() {
        return InboxOrdering.NONE;
    }

    @Override
    public InboxWork prepare(String payload) {
        OrderEvent event = parser.parse(payload);
        return (DecidingInboxWork) now -> apply(event, now);
    }

    private Optional<String> apply(OrderEvent event, Instant now) {
        return application.apply(event, now).map(this::ignored);
    }

    private String ignored(Ignored ignored) {
        metrics.countIgnored(ignored.reason());
        log.warn(NOT_APPLIED, ignored.text());
        return ignored.text();
    }
}
