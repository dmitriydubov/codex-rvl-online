package ru.projectx.contracts.service.payment.cancellation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.support.PaymentOrders;

class PaymentOrderWithdrawalTest {

    private static final Instant NOW = Instant.parse("2026-02-12T12:00:00Z");

    private final PaymentOrderRepository orders = mock();
    private final PaymentOrderWithdrawal withdrawal = new PaymentOrderWithdrawal(orders);

    @Test
    void stoppedContract_withdrawsItsLockedOrder() {
        PaymentOrder order = PaymentOrders.created();
        given(orders.findLockedByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.of(order));

        withdrawal.contractStopped(PaymentOrders.CONTRACT, NOW);

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
    }

    @Test
    void recordWithoutOrder_isLeftAlone() {
        UUID policy = UUID.fromString("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39");
        given(orders.findLockedByContractId(policy)).willReturn(Optional.empty());

        withdrawal.contractStopped(policy, NOW);

        verify(orders).findLockedByContractId(policy);
    }
}
