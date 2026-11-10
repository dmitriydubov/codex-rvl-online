package ru.projectx.contracts.service.payment.invoice;

import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.payment.PayerEmail;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.entity.payment.PaymentOrderView;
import ru.projectx.contracts.service.payment.CheckoutRequest;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.NewOrder;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class CheckoutClaims {

    private final PaymentTerms terms;
    private final PaymentTermExpiry expiry;

    public CheckoutClaims(PaymentTerms terms, PaymentTermExpiry expiry) {
        this.terms = terms;
        this.expiry = expiry;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CheckoutRequest> claim(LockedInvoice locked, Instant now) {
        if (!awaitsCheckout(locked)) {
            return Optional.empty();
        }
        Optional<Cancellation> deferred = locked.invoice().liftDeferredCancellation(now);
        if (deferred.isPresent()) {
            locked.contract().cancel(deferred.get(), now);
            locked.order().withdraw(now);
            return Optional.empty();
        }
        return locked.order().view().payer().flatMap(payer -> claimFor(payer, locked, now));
    }

    private Optional<CheckoutRequest> claimFor(PayerEmail payer, LockedInvoice locked, Instant now) {
        PaymentOrderView order = locked.order().view();
        Optional<Instant> deadline = terms.deadline(now, order.document().documentTtl());
        if (deadline.isEmpty()) {
            expiry.close(locked.withoutInvoice(), PaymentTermExpiry.BEFORE_INVOICE, now);
            return Optional.empty();
        }
        locked.invoice().start(deadline.get(), now);
        return order.orderId().map(orderId -> new CheckoutRequest(orderId,
                new NewOrder(order.clientId(), order.id(), order.document()), deadline.get(), payer));
    }

    private static boolean awaitsCheckout(LockedInvoice locked) {
        return locked.order().view().state() == PaymentOrderState.CREATED
                && locked.contract().status() == ContractStatus.ACCEPTED
                && locked.invoice().view().state().awaitsCheckout();
    }
}
