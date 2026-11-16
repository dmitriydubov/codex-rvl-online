package ru.projectx.contracts.service.payment.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.projectx.contracts.entity.contract.AcceptanceChannel;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.OfferAcceptance;
import ru.projectx.contracts.entity.payment.OrderDocument;
import ru.projectx.contracts.entity.payment.PayerEmail;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.entity.payment.PaymentOrderView;
import ru.projectx.contracts.exception.PayerEmailRequiredException;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.support.OrderingTestProperties;
import ru.projectx.contracts.support.PaymentOrders;
import ru.projectx.contracts.support.Tasks;

class PaymentOrderOpeningsTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final Instant ACCEPTED_AT = Instant.parse("2026-02-10T07:04:58Z");
    private static final String EMAIL = "insured@example.test";
    private static final OfferAcceptance OFFER = new OfferAcceptance(1, Tasks.INSURED, ACCEPTED_AT,
            AcceptanceChannel.WEB, null, null);

    private final PaymentOrderRepository orders = mock();
    private final PaymentOrderOpenings openings = new PaymentOrderOpenings(orders, OrderingTestProperties.attempts(3));

    @Test
    void acceptedContract_opensAnAwaitingOrderForTheInsuredWithTheContractSnapshotAndThePayerAddress() {
        given(orders.save(any())).willAnswer(saved -> saved.getArgument(0));
        Contract contract = PaymentOrders.acceptedContract();

        openings.forPayer(EMAIL).open(contract, OFFER, NOW);

        ArgumentCaptor<PaymentOrder> saved = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(orders).save(saved.capture());
        PaymentOrderView order = saved.getValue().view();
        assertThat(order.state()).isEqualTo(PaymentOrderState.AWAITING_CREATION);
        assertThat(order.contractId()).isEqualTo(PaymentOrders.CONTRACT);
        assertThat(order.clientId()).isEqualTo(Tasks.INSURED);
        assertThat(order.correlationId()).isNotNull();
        assertThat(order.document()).isEqualTo(new OrderDocument("DKM-2026-000001", new BigDecimal("34000.00"),
                ACCEPTED_AT, Instant.parse("2026-04-09T20:59:59Z")));
        assertThat(order.payer()).contains(new PayerEmail(EMAIL));
    }

    @Test
    void everyOrder_getsItsOwnCorrelationId() {
        given(orders.save(any())).willAnswer(saved -> saved.getArgument(0));

        openings.forPayer(EMAIL).open(PaymentOrders.acceptedContract(), OFFER, NOW);
        openings.forPayer(EMAIL).open(PaymentOrders.acceptedContract(), OFFER, NOW);

        ArgumentCaptor<PaymentOrder> saved = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(orders, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).view().correlationId())
                .isNotEqualTo(saved.getAllValues().get(1).view().correlationId());
    }

    @Test
    void acceptanceWithoutAddress_isRefusedBeforeAnythingIsOpened() {
        assertThatThrownBy(() -> openings.forPayer(null))
                .isInstanceOf(PayerEmailRequiredException.class)
                .hasMessage("в акцепте нет адреса электронной почты плательщика, а оплата включена");

        verifyNoInteractions(orders);
    }

    @Test
    void opening_savesNothingUntilTheContractIsAccepted() {
        openings.forPayer(EMAIL);

        verifyNoInteractions(orders);
    }
}
