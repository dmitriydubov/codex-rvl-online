package ru.projectx.contracts.support;

import java.time.Instant;
import java.util.UUID;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.payment.InvoiceLink;
import ru.projectx.contracts.entity.payment.InvoiceTransition;
import ru.projectx.contracts.entity.payment.OrderInvoice;

public final class Invoices {

    public static final Instant CHECKOUT_STARTED = Instant.parse("2026-02-10T07:05:00Z");
    public static final Instant DEADLINE = Instant.parse("2026-02-13T07:05:00Z");
    public static final String PAYMENT_URL = "https://pay.example.test/pay/7c1e5a90";
    public static final InvoiceLink LINK = new InvoiceLink("7c1e5a90-3b42-4d6f-8a17-9e0f2b6c4d31", PAYMENT_URL);
    public static final Cancellation REFUSED = new Cancellation(CancelReason.INSURED_REFUSED, null,
            Instant.parse("2026-02-12T11:30:00Z"));

    private Invoices() {
    }

    public static OrderInvoice awaitingDocument(UUID contractId) {
        return OrderInvoice.open(PaymentOrders.ORDER, contractId, Issuances.OPENED);
    }

    public static OrderInvoice ready(UUID contractId) {
        OrderInvoice invoice = awaitingDocument(contractId);
        invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, Issuances.OPENED);
        return invoice;
    }

    public static OrderInvoice inCheckout(UUID contractId) {
        OrderInvoice invoice = ready(contractId);
        invoice.start(DEADLINE, CHECKOUT_STARTED);
        return invoice;
    }

    public static OrderInvoice issued(UUID contractId) {
        OrderInvoice invoice = inCheckout(contractId);
        invoice.issue(LINK, CHECKOUT_STARTED);
        return invoice;
    }

    public static OrderInvoice issuedWithoutLink(UUID contractId) {
        OrderInvoice invoice = inCheckout(contractId);
        invoice.pass(InvoiceTransition.REPLY_LOST, CHECKOUT_STARTED);
        return invoice;
    }

    public static OrderInvoice cancellationDeferred(OrderInvoice invoice) {
        invoice.deferCancellation(REFUSED, REFUSED.cancelledAt());
        return invoice;
    }
}
