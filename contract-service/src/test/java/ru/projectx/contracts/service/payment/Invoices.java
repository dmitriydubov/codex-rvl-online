package ru.projectx.contracts.service.payment;

import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStep;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.payment.InvoiceLink;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.InvoiceTransition;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.PaymentOrders;

public final class Invoices {

    public static final Instant STARTED = Instant.parse("2026-02-10T07:20:00Z");
    public static final Instant DEADLINE = Instant.parse("2026-02-13T07:20:00Z");
    public static final InvoiceLink LINK = new InvoiceLink("inv-0001", "https://pay.example.test/pay/inv-0001");
    public static final String REJECTION = "CheckoutValidatorService.valid";
    public static final Cancellation REFUSED =
            new Cancellation(CancelReason.INSURED_REFUSED, "стенд", Instant.parse("2026-02-12T11:30:00Z"));

    private Invoices() {
    }

    public static OrderInvoice awaitingDocument() {
        return OrderInvoice.open(PaymentOrders.ORDER, PaymentOrders.CONTRACT, Issuances.OPENED);
    }

    public static OrderInvoice ready() {
        OrderInvoice invoice = awaitingDocument();
        invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, Issuances.OPENED);
        return invoice;
    }

    public static OrderInvoice inCheckout() {
        OrderInvoice invoice = ready();
        invoice.start(DEADLINE, STARTED);
        return invoice;
    }

    public static OrderInvoice issued() {
        OrderInvoice invoice = inCheckout();
        invoice.issue(LINK, STARTED);
        return invoice;
    }

    public static OrderInvoice in(InvoiceState state) {
        return switch (state) {
            case AWAITING_DOCUMENT -> awaitingDocument();
            case DOCUMENT_UNCONFIRMED -> passed(awaitingDocument(), InvoiceTransition.REMINDERS_EXHAUSTED);
            case READY -> ready();
            case CHECKOUT -> inCheckout();
            case ISSUED -> issued();
            case REJECTED -> rejected();
            case PAYMENT_ERROR -> passed(issued(), InvoiceTransition.PAYMENT_FAILED);
            case PAID -> paid();
            case OVERDUE -> passed(issued(), InvoiceTransition.PAYMENT_OVERDUE);
        };
    }

    public static Contract invoicedContract() {
        Contract contract = PaymentOrders.acceptedContract();
        contract.advance(ContractStep.INVOICE_ISSUED);
        return contract;
    }

    public static String logLine(String reason) {
        return "заказ на оплату договора КМ " + PaymentOrders.CONTRACT
                + " участника 9275471c-d2fe-484f-97a6-d6d7b1267b5e заявки 61a7982c-a0b4-4bbf-9527-60b40b752803: " + reason;
    }

    public static Instant updatedAt(OrderInvoice invoice) {
        return (Instant) ReflectionTestUtils.getField(invoice, "updatedAt");
    }

    private static OrderInvoice rejected() {
        OrderInvoice invoice = inCheckout();
        invoice.reject(REJECTION, STARTED);
        return invoice;
    }

    private static OrderInvoice paid() {
        OrderInvoice invoice = issued();
        invoice.pay(STARTED, STARTED);
        return invoice;
    }

    private static OrderInvoice passed(OrderInvoice invoice, InvoiceTransition transition) {
        invoice.pass(transition, STARTED);
        return invoice;
    }
}
