package ru.projectx.contracts.service.payment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

    static final String TERM_EXPIRED = "contracts.payment.term.expired";
    static final String PAYMENT_ERRORS = "contracts.payment.errors";
    static final String PAID_AFTER_CANCELLATION = "contracts.km.contracts.paid.after.cancellation";
    static final String PAID_CANCELLED = "contracts.km.contracts.paid.cancelled";
    static final String EVENTS_IGNORED = "contracts.payment.events.ignored";
    static final String ALERTS = "contracts.payment.reconciliation.alerts";
    static final String REASON = "reason";

    private final MeterRegistry registry;

    public PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void countTermExpired() {
        count(TERM_EXPIRED, "договоры КМ, прекращённые до счёта, потому что срок оплаты истёк");
    }

    public void countPaymentError() {
        count(PAYMENT_ERRORS, "сообщения сервиса управления заказами об ошибке оплаты счёта");
    }

    public void countPaidAfterCancellation() {
        count(PAID_AFTER_CANCELLATION, "оплаты договоров КМ, пришедшие после прекращения договора: деньги возвращают");
    }

    public void countPaidCancelled() {
        count(PAID_CANCELLED, "оплаченные договоры КМ, работа по которым прекращена: деньги возвращают");
    }

    public void countIgnored(IgnoredEvent reason) {
        Counter.builder(EVENTS_IGNORED)
                .description("события сервиса управления заказами, не применённые к заказу, по причине")
                .tag(REASON, reason.tag())
                .register(registry)
                .increment();
    }

    public void countAlert(PaymentAlert reason) {
        Counter.builder(ALERTS)
                .description("заказы на оплату, по которым нужен разбор дежурного, по причине")
                .tag(REASON, reason.tag())
                .register(registry)
                .increment();
    }

    private void count(String name, String description) {
        Counter.builder(name).description(description).register(registry).increment();
    }
}
