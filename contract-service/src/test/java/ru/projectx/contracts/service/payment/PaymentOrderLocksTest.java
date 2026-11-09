package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.support.PaymentOrders;

class PaymentOrderLocksTest {

    private final ContractRepository contracts = mock();
    private final PaymentOrderRepository orders = mock();
    private final PaymentOrderLocks locks = new PaymentOrderLocks(contracts, orders);

    @Test
    void contractRow_isLockedBeforeTheOrderRow() {
        Contract contract = PaymentOrders.acceptedContract();
        PaymentOrder order = PaymentOrders.awaiting();
        given(contracts.findLockedById(PaymentOrders.CONTRACT)).willReturn(Optional.of(contract));
        given(orders.findLockedByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.of(order));

        LockedOrder locked = locks.lock(PaymentOrders.CONTRACT);

        assertThat(locked).isEqualTo(new LockedOrder(contract, order));
        InOrder sequence = inOrder(contracts, orders);
        sequence.verify(contracts).findLockedById(PaymentOrders.CONTRACT);
        sequence.verify(orders).findLockedByContractId(PaymentOrders.CONTRACT);
    }

    @Test
    void missingContract_failsBeforeTouchingOrders() {
        given(contracts.findLockedById(PaymentOrders.CONTRACT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> locks.lock(PaymentOrders.CONTRACT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PaymentOrders.CONTRACT.toString());
        verifyNoInteractions(orders);
    }

    @Test
    void missingOrder_fails() {
        given(contracts.findLockedById(PaymentOrders.CONTRACT)).willReturn(Optional.of(PaymentOrders.acceptedContract()));
        given(orders.findLockedByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> locks.lock(PaymentOrders.CONTRACT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("нет заказа на оплату");
    }
}
