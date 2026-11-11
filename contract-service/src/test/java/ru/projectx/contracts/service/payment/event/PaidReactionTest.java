package ru.projectx.contracts.service.payment.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.service.payment.IgnoredEvent;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.PaymentAlert;
import ru.projectx.contracts.service.payment.PaymentMetrics;
import ru.projectx.contracts.service.payment.invoice.PaymentOutcomes;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.PaymentOrders;

class PaidReactionTest {

    private static final Instant NOW = Instant.parse("2026-02-11T10:00:05Z");
    private static final Instant UPDATED = Instant.parse("2026-02-11T10:00:00.250Z");
    private static final OrderEvent PAID = new OrderEvent(Optional.of("paid"), Optional.of(PaymentOrders.ORDER_ID),
            new EventDocument.Ours(PaymentOrders.ORDER, Optional.of(new BigDecimal("34000.00"))), Optional.of(UPDATED));
    private static final String UNEXPECTED = "оплата поступила по заказу, который не ждёт оплаты";

    private final PaymentOutcomes outcomes = mock();
    private final PaymentMetrics metrics = mock();
    private final PaidReaction paid = new PaidReaction(outcomes, metrics);
    private final Contract contract = Invoices.invoicedContract();
    private final PaymentOrder order = PaymentOrders.created();
    private final CapturedLog log = CapturedLog.of(PaidReaction.class);

    @AfterEach
    void releaseLog() {
        log.close();
    }

    private EventTarget target(OrderInvoice invoice) {
        return new EventTarget(new LockedOrder(contract, order), Optional.of(invoice));
    }

    @Test
    void reaction_namesTheEventTypeOfOrdering() {
        assertThat(paid.eventType()).isEqualTo("paid");
    }

    @Test
    void paid_appliesThePaymentAtTheMomentOfTheEventWithoutAnyAlert() {
        OrderInvoice invoice = Invoices.issued();
        given(outcomes.paid(new LockedInvoice(contract, order, invoice), UPDATED, NOW)).willReturn(true);

        assertThat(paid.react(target(invoice), PAID, NOW)).isEmpty();

        verify(outcomes).paid(new LockedInvoice(contract, order, invoice), UPDATED, NOW);
        verifyNoInteractions(metrics);
        assertThat(log.events()).isEmpty();
    }

    @Test
    void paid_repeatedForPaidInvoice_isOutOfOrderWithoutAlert() {
        OrderInvoice invoice = Invoices.in(InvoiceState.PAID);

        assertThat(paid.react(target(invoice), PAID, NOW)).contains(
                new Ignored(IgnoredEvent.OUT_OF_ORDER, "событие paid не по порядку: заказ уже оплачен"));

        verifyNoInteractions(metrics);
        assertThat(log.events()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED", "READY", "REJECTED"})
    void paid_forInvoiceThatDoesNotAwaitPayment_isSkippedWithAnAlertForTheOperator(InvoiceState state) {
        OrderInvoice invoice = Invoices.in(state);

        Optional<Ignored> ignored = paid.react(target(invoice), PAID, NOW);

        assertThat(ignored).map(Ignored::reason).contains(IgnoredEvent.OUT_OF_ORDER);
        assertThat(ignored).map(Ignored::text).hasValueSatisfying(
                text -> assertThat(text).startsWith("событие paid не по порядку: заказ "));
        verify(metrics).countAlert(PaymentAlert.UNEXPECTED_STATE);
        assertThat(log.at(Level.ERROR)).containsExactly(Invoices.logLine(UNEXPECTED));
        assertThat(log.events()).hasSize(1);
        assertThat(invoice.view().state()).isEqualTo(state);
    }

    @Test
    void paid_forOrderWithoutInvoice_isSkippedWithAnAlertAndAppliesNothing() {
        EventTarget withoutInvoice = new EventTarget(new LockedOrder(contract, order), Optional.empty());

        assertThat(paid.react(withoutInvoice, PAID, NOW))
                .contains(new Ignored(IgnoredEvent.OUT_OF_ORDER, "событие paid не по порядку: заказ создан"));

        verifyNoInteractions(outcomes);
        verify(metrics).countAlert(PaymentAlert.UNEXPECTED_STATE);
        assertThat(log.at(Level.ERROR)).containsExactly(Invoices.logLine(UNEXPECTED));
    }

    @Test
    void paid_withoutTheMomentOfTheChange_violatesTheContractAndAppliesNothing() {
        OrderEvent withoutMoment = new OrderEvent(PAID.type(), PAID.orderId(), PAID.document(), Optional.empty());

        assertThatThrownBy(() -> paid.react(target(Invoices.issued()), withoutMoment, NOW))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля updated");

        verifyNoInteractions(outcomes, metrics);
    }
}
