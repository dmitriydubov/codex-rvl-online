package ru.projectx.contracts.entity.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "payment_orders")
public class PaymentOrder {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Embedded
    private OrderDocument document;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private PaymentOrderState state;

    @Nullable
    @Column(name = "external_order_id")
    private String externalOrderId;

    @Nullable
    @Column(name = "rejection_code")
    private String rejectionCode;

    @Column(name = "creation_requested", nullable = false)
    private boolean creationRequested;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Nullable
    @Column(name = "payer_email")
    private String payerEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentOrder() {
    }

    private PaymentOrder(OrderDraft draft, Instant now) {
        this.contractId = draft.contractId();
        this.clientId = draft.clientId();
        this.correlationId = UUID.randomUUID();
        this.document = draft.document();
        this.payerEmail = draft.payer().address();
        this.state = PaymentOrderState.AWAITING_CREATION;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static PaymentOrder open(OrderDraft draft, Instant now) {
        return new PaymentOrder(draft, now);
    }

    public void markRequested(OrderRequest request, Instant now) {
        OrderRequests sent = request.after(new OrderRequests(creationRequested, cancelRequested));
        this.creationRequested = sent.creation();
        this.cancelRequested = sent.cancellation();
        this.updatedAt = now;
    }

    public boolean recordOrder(String orderId, Instant now) {
        if (externalOrderId != null) {
            return false;
        }
        this.externalOrderId = orderId;
        this.updatedAt = now;
        if (state == PaymentOrderState.CANCELLED) {
            this.state = PaymentOrderState.CANCEL_PENDING;
        }
        return enter(PaymentOrderState.AWAITING_CREATION, PaymentOrderState.CREATED, now);
    }

    public boolean reject(String code, Instant now) {
        if (state != PaymentOrderState.AWAITING_CREATION && state != PaymentOrderState.CREATED) {
            return false;
        }
        this.rejectionCode = code;
        this.state = PaymentOrderState.REJECTED;
        this.updatedAt = now;
        return true;
    }

    public void withdraw(Instant now) {
        if (state.withdrawn()) {
            return;
        }
        PaymentOrderState next = view().mayExistInOrdering()
                ? PaymentOrderState.CANCEL_PENDING
                : PaymentOrderState.CANCELLED;
        this.payerEmail = null;
        enter(state, next, now);
    }

    public boolean expire(Instant now) {
        if (creationRequested || !enter(PaymentOrderState.AWAITING_CREATION, PaymentOrderState.EXPIRED, now)) {
            return false;
        }
        this.payerEmail = null;
        return true;
    }

    public void forgetPayer(Instant now) {
        if (payerEmail != null) {
            this.payerEmail = null;
            this.updatedAt = now;
        }
    }

    public boolean recordCancelled(String orderId, Instant now) {
        if (state == PaymentOrderState.CANCEL_PENDING) {
            recordOrder(orderId, now);
        }
        return enter(PaymentOrderState.CANCEL_PENDING, PaymentOrderState.CANCELLED, now);
    }

    public boolean recordNotFound(Instant now) {
        if (externalOrderId != null) {
            return false;
        }
        return enter(PaymentOrderState.CANCEL_PENDING, PaymentOrderState.CANCELLED, now);
    }

    public boolean rejectCancellation(String code, Instant now) {
        if (state != PaymentOrderState.CANCEL_PENDING) {
            return false;
        }
        this.rejectionCode = code;
        this.state = PaymentOrderState.CANCEL_REJECTED;
        this.updatedAt = now;
        return true;
    }

    public PaymentOrderView view() {
        return new PaymentOrderView(id, contractId, clientId, correlationId, document, state,
                Optional.ofNullable(externalOrderId), Optional.ofNullable(rejectionCode),
                new OrderRequests(creationRequested, cancelRequested), Optional.ofNullable(payerEmail).map(PayerEmail::new));
    }

    private boolean enter(PaymentOrderState from, PaymentOrderState to, Instant now) {
        if (state != from) {
            return false;
        }
        this.state = to;
        this.updatedAt = now;
        return true;
    }
}
