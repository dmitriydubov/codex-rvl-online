package ru.projectx.contracts.service.payment.invoice;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.config.payment.PaymentOrderPassesProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.InvoiceTerm;
import ru.projectx.contracts.entity.payment.InvoiceView;
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
    private final Duration abandonedAfter;

    public CheckoutClaims(PaymentTerms terms, PaymentTermExpiry expiry, PaymentOrderPassesProperties passes) {
        this.terms = terms;
        this.expiry = expiry;
        this.abandonedAfter = passes.reconciliation().threshold();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CheckoutRequest> claim(LockedInvoice locked, Instant now) {
        if (!awaitsCheckout(locked, now)) {
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

    private boolean awaitsCheckout(LockedInvoice locked, Instant now) {
        return locked.order().view().state() == PaymentOrderState.CREATED
                && locked.contract().status() == ContractStatus.ACCEPTED
                && claimable(locked.invoice().view(), now);
    }

    private boolean claimable(InvoiceView invoice, Instant now) {
        return invoice.state() == InvoiceState.READY || abandoned(invoice, now);
    }

    private boolean abandoned(InvoiceView invoice, Instant now) {
        Instant startedBefore = now.minus(abandonedAfter);
        return invoice.state() == InvoiceState.CHECKOUT
                && invoice.term().map(InvoiceTerm::startedAt).filter(started -> !started.isAfter(startedBefore)).isPresent();
    }
}
