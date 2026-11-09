package ru.projectx.contracts.message.payment;

public final class PaymentOrderEvents {

    public static final String AGGREGATE_TYPE = "PAYMENT_ORDER";
    public static final String PAYMENT_ORDER_FIXED = "PaymentOrderFixed";
    public static final String DOCUMENT_FIXED_TOPIC = "ordering.document.fixed";
    public static final String SOURCE = "source";
    public static final String EVENT_TYPE = "eventType";
    public static final String FIXED = "fixed";
    public static final String DATA = "data";
    public static final String AGREEMENT_ID = "agreementId";

    private PaymentOrderEvents() {
    }
}
