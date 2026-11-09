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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentOrder() {
    }

    private PaymentOrder(UUID contractId, UUID clientId, OrderDocument document, Instant now) {
        this.contractId = contractId;
        this.clientId = clientId;
        this.correlationId = UUID.randomUUID();
        this.document = document;
        this.state = PaymentOrderState.AWAITING_CREATION;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static PaymentOrder open(UUID contractId, UUID clientId, OrderDocument document, Instant now) {
        return new PaymentOrder(contractId, clientId, document, now);
    }

    public void requestCreation(Instant now) {
        this.creationRequested = true;
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
        enter(state, mayExistInOrdering() ? PaymentOrderState.CANCEL_PENDING : PaymentOrderState.CANCELLED, now);
    }

    private boolean mayExistInOrdering() {
        return externalOrderId != null || creationRequested && state == PaymentOrderState.AWAITING_CREATION;
    }

    public void requestCancellation(Instant now) {
        this.cancelRequested = true;
        this.updatedAt = now;
    }

    public boolean recordCancelled(Instant now) {
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

    public PaymentOrderState state() {
        return state;
    }

    public PaymentOrderView view() {
        return new PaymentOrderView(id, contractId, clientId, correlationId, document, state,
                Optional.ofNullable(externalOrderId), Optional.ofNullable(rejectionCode), creationRequested,
                cancelRequested);
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
