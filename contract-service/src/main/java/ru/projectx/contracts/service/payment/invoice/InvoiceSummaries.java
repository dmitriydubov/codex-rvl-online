package ru.projectx.contracts.service.payment.invoice;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.config.payment.PaymentOrderPassesProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.repository.payment.OrderInvoiceRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;

@Service
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class InvoiceSummaries {

    static final String WITHOUT_INVOICE = "contracts.km.contracts.without.invoice.overdue";
    static final String WITHOUT_OUTCOME = "contracts.km.invoices.without.outcome.overdue";
    private static final Set<InvoiceState> NOT_INVOICED =
            EnumSet.of(InvoiceState.AWAITING_DOCUMENT, InvoiceState.DOCUMENT_UNCONFIRMED, InvoiceState.READY);
    private static final Set<InvoiceState> AWAITING_OUTCOME = EnumSet.of(InvoiceState.ISSUED, InvoiceState.PAYMENT_ERROR);

    private final OrderInvoiceRepository invoices;
    private final BackgroundLeadership leadership;
    private final Duration awaitingThreshold;
    private final Duration grace;
    private final Clock clock;
    private final AtomicLong withoutInvoice = new AtomicLong();
    private final AtomicLong withoutOutcome = new AtomicLong();

    public InvoiceSummaries(OrderInvoiceRepository invoices, BackgroundLeadership leadership,
            PaymentOrderPassesProperties passes, Clock clock, MeterRegistry registry) {
        this.invoices = invoices;
        this.leadership = leadership;
        this.awaitingThreshold = passes.invoices().awaitingThreshold();
        this.grace = passes.reconciliation().paymentGrace();
        this.clock = clock;
        Gauge.builder(WITHOUT_INVOICE, withoutInvoice, AtomicLong::get)
                .description("договоры КМ с заказом на оплату, счёт по которым не выставлен дольше порога")
                .register(registry);
        Gauge.builder(WITHOUT_OUTCOME, withoutOutcome, AtomicLong::get)
                .description("счета договоров КМ, по которым после срока оплаты нет ни оплаты, ни просрочки")
                .register(registry);
    }

    @Transactional(readOnly = true)
    public void refresh() {
        leadership.runByRole(this::count, this::reset);
    }

    private void count() {
        Instant now = clock.instant();
        withoutInvoice.set(invoices.countCreatedBefore(PaymentOrderState.CREATED, NOT_INVOICED,
                now.minus(awaitingThreshold)));
        withoutOutcome.set(invoices.countDueBefore(PaymentOrderState.CREATED, AWAITING_OUTCOME, now.minus(grace)));
    }

    private void reset() {
        withoutInvoice.set(0);
        withoutOutcome.set(0);
    }
}
