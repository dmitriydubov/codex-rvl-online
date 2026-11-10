package ru.projectx.contracts.service.payment.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.payment.PaymentOrderKeys;
import ru.projectx.contracts.repository.payment.OrderInvoiceRepository;
import ru.projectx.contracts.service.payment.IgnoredEvent;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.service.payment.PaymentOrderLog;

@Service
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class OrderEventApplication {

    private final OrderEventMatching matching;
    private final PaymentOrderLocks locks;
    private final OrderInvoiceRepository invoices;
    private final Map<String, OrderEventReaction> reactions;

    OrderEventApplication(OrderEventMatching matching, PaymentOrderLocks locks, OrderInvoiceRepository invoices,
            List<OrderEventReaction> reactions) {
        this.matching = matching;
        this.locks = locks;
        this.invoices = invoices;
        this.reactions = reactions.stream()
                .collect(Collectors.toUnmodifiableMap(OrderEventReaction::eventType, Function.identity()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Ignored> apply(OrderEvent event, Instant now) {
        return matching.unusable(event).or(() -> applyUsable(event, now));
    }

    private Optional<Ignored> applyUsable(OrderEvent event, Instant now) {
        String orderId = event.orderId().orElseThrow();
        EventDocument.Ours document = (EventDocument.Ours) event.document();
        Optional<PaymentOrderKeys> order = matching.orderOf(orderId, document.documentId());
        if (order.isEmpty()) {
            return Optional.of(new Ignored(IgnoredEvent.UNKNOWN_ORDER, OrderEventMatching.UNKNOWN_ORDER));
        }
        return PaymentOrderLog.supply(order.get().correlationId(), () -> applyTo(order.get(), event, now));
    }

    private Optional<Ignored> applyTo(PaymentOrderKeys order, OrderEvent event, Instant now) {
        LockedOrder locked = locks.lock(order.contractId());
        Optional<Ignored> mismatch = matching.mismatch(locked.order().view(), event.orderId().orElseThrow(),
                (EventDocument.Ours) event.document());
        if (mismatch.isPresent()) {
            return mismatch;
        }
        EventTarget target = new EventTarget(locked, invoices.findFreshByContractId(order.contractId()));
        String type = event.type().orElseThrow();
        return Optional.ofNullable(reactions.get(type))
                .map(reaction -> reaction.react(target, event, now))
                .orElseGet(() -> Optional.of(Ignored.notApplicable(type)));
    }
}
