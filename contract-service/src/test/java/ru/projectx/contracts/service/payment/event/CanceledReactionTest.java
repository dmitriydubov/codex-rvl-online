package ru.projectx.contracts.service.payment.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.service.payment.IgnoredEvent;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.PaymentAlert;
import ru.projectx.contracts.service.payment.PaymentMetrics;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.PaymentOrders;

class CanceledReactionTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:30:00Z");
    private static final OrderEvent EVENT = new OrderEvent(Optional.of("canceled"), Optional.of(PaymentOrders.ORDER_ID),
            new EventDocument.Foreign("стенд"), Optional.empty());

    private final PaymentMetrics metrics = mock();
    private final CanceledReaction canceled = new CanceledReaction(metrics);
    private final Contract contract = PaymentOrders.acceptedContract();
    private final CapturedLog log = CapturedLog.of(CanceledReaction.class);

    @AfterEach
    void releaseLog() {
        log.close();
    }

    private Optional<Ignored> react(PaymentOrder order) {
        return canceled.react(new EventTarget(new LockedOrder(contract, order), Optional.of(Invoices.ready())), EVENT,
                NOW);
    }

    @Test
    void reaction_namesTheEventTypeOfOrdering() {
        assertThat(canceled.eventType()).isEqualTo("canceled");
    }

    @Test
    void cancellationContractsAskedFor_changesNothing() {
        assertThat(react(PaymentOrders.cancelPending()))
                .contains(new Ignored(IgnoredEvent.NOT_APPLICABLE, "событие canceled заказ не меняет"));

        verifyNoInteractions(metrics);
        assertThat(log.events()).isEmpty();
    }

    @Test
    void cancellationContractsDidNotAskFor_raisesAnAlertAndLeavesTheOrderAndTheContract() {
        PaymentOrder order = PaymentOrders.created();

        assertThat(react(order)).isEmpty();

        verify(metrics).countAlert(PaymentAlert.UNEXPECTED_CANCEL);
        assertThat(log.at(Level.ERROR))
                .containsExactly(Invoices.logLine("Ordering отменил заказ без запроса Договоров"));
        assertThat(log.events()).hasSize(1);
        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CREATED);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
    }
}
