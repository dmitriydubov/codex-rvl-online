package ru.projectx.contracts.entity.payment;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.entity.issuance.Cancellation;

@Entity
@Table(name = "payment_invoices")
public class OrderInvoice {

    private static final Set<InvoiceState> STARTABLE = EnumSet.of(InvoiceState.READY, InvoiceState.CHECKOUT);
    private static final Set<InvoiceState> IN_CHECKOUT = EnumSet.of(InvoiceState.CHECKOUT);
    private static final Set<InvoiceState> PAYABLE = EnumSet.of(InvoiceState.CHECKOUT, InvoiceState.ISSUED,
            InvoiceState.PAYMENT_ERROR, InvoiceState.OVERDUE);

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private InvoiceState state;

    @Column(name = "fix_repeats", nullable = false)
    private int fixRepeats;

    @Nullable
    @Column(name = "checkout_started_at")
    private Instant checkoutStartedAt;

    @Nullable
    @Column(name = "payment_deadline")
    private Instant paymentDeadline;

    @Nullable
    @Column(name = "invoice_id")
    private String invoiceId;

    @Nullable
    @Column(name = "payment_url")
    private String paymentUrl;

    @Nullable
    @Column(name = "paid_at")
    private Instant paidAt;

    @Nullable
    @Column(name = "rejection_code")
    private String rejectionCode;

    @Nullable
    @Embedded
    @AttributeOverride(name = "reason", column = @Column(name = "deferred_cancel_reason"))
    @AttributeOverride(name = "comment", column = @Column(name = "deferred_cancel_comment"))
    @AttributeOverride(name = "cancelledAt", column = @Column(name = "deferred_cancelled_at"))
    private Cancellation deferredCancellation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderInvoice() {
    }

    private OrderInvoice(UUID orderId, UUID contractId, Instant now) {
        this.orderId = orderId;
        this.contractId = contractId;
        this.state = InvoiceState.AWAITING_DOCUMENT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static OrderInvoice open(UUID orderId, UUID contractId, Instant now) {
        return new OrderInvoice(orderId, contractId, now);
    }

    public void repeatFix(Instant now) {
        this.fixRepeats += 1;
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public boolean pass(InvoiceTransition transition, Instant now) {
        if (!enter(transition.from(), transition.to(), now)) {
            return false;
        }
        if (transition.restartsReminders()) {
            this.fixRepeats = 0;
        }
        return true;
    }

    public boolean start(Instant deadline, Instant now) {
        if (!enter(STARTABLE, InvoiceState.CHECKOUT, now)) {
            return false;
        }
        this.checkoutStartedAt = now;
        this.paymentDeadline = deadline;
        return true;
    }

    public boolean issue(InvoiceLink link, Instant now) {
        if (!enter(IN_CHECKOUT, InvoiceState.ISSUED, now) && !isLinkMissing()) {
            return false;
        }
        this.invoiceId = link.invoiceId();
        this.paymentUrl = link.paymentUrl();
        this.updatedAt = now;
        return true;
    }

    private boolean isLinkMissing() {
        return paymentUrl == null && (state == InvoiceState.ISSUED || state == InvoiceState.PAYMENT_ERROR);
    }

    public boolean reject(String code, Instant now) {
        if (!enter(IN_CHECKOUT, InvoiceState.REJECTED, now)) {
            return false;
        }
        this.rejectionCode = code;
        return true;
    }

    public boolean pay(Instant paidMoment, Instant now) {
        if (!enter(PAYABLE, InvoiceState.PAID, now)) {
            return false;
        }
        this.paidAt = paidMoment;
        return true;
    }

    public void deferCancellation(Cancellation cancellation, Instant now) {
        if (deferredCancellation == null) {
            this.deferredCancellation = cancellation;
            this.updatedAt = now;
        }
    }

    public Optional<Cancellation> liftDeferredCancellation(Instant now) {
        Optional<Cancellation> deferred = Optional.ofNullable(deferredCancellation);
        if (deferred.isPresent()) {
            this.deferredCancellation = null;
            this.updatedAt = now;
        }
        return deferred;
    }

    public InvoiceView view() {
        Optional<InvoiceTerm> term = Optional.ofNullable(paymentDeadline)
                .map(deadline -> new InvoiceTerm(checkoutStartedAt, deadline));
        Optional<InvoiceLink> link = Optional.ofNullable(paymentUrl).map(url -> new InvoiceLink(invoiceId, url));
        return new InvoiceView(orderId, contractId, state, fixRepeats, term, link, Optional.ofNullable(paidAt),
                Optional.ofNullable(rejectionCode), Optional.ofNullable(deferredCancellation));
    }

    private boolean enter(Set<InvoiceState> from, InvoiceState to, Instant now) {
        if (!from.contains(state)) {
            return false;
        }
        this.state = to;
        this.updatedAt = now;
        return true;
    }
}
