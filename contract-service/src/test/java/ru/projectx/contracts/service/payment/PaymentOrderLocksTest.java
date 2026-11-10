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
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.payment.OrderInvoiceRepository;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.PaymentOrders;

class PaymentOrderLocksTest {

    private final ContractRepository contracts = mock();
    private final PaymentOrderRepository orders = mock();
    private final OrderInvoiceRepository invoices = mock();
    private final PaymentOrderLocks locks = new PaymentOrderLocks(contracts, orders, invoices);

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
        verifyNoInteractions(invoices);
    }

    @Test
    void missingContract_failsBeforeTouchingOrders() {
        given(contracts.findLockedById(PaymentOrders.CONTRACT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> locks.lock(PaymentOrders.CONTRACT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("договора КМ " + PaymentOrders.CONTRACT + " нет");
        verifyNoInteractions(orders);
    }

    @Test
    void missingOrder_fails() {
        given(contracts.findLockedById(PaymentOrders.CONTRACT)).willReturn(Optional.of(PaymentOrders.acceptedContract()));
        given(orders.findLockedByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> locks.lock(PaymentOrders.CONTRACT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("у договора КМ " + PaymentOrders.CONTRACT + " нет заказа на оплату");
    }

    @Test
    void invoice_isReadAfterTheContractAndOrderRowsAreLocked() {
        Contract contract = PaymentOrders.acceptedContract();
        PaymentOrder order = PaymentOrders.created();
        OrderInvoice invoice = OrderInvoice.open(PaymentOrders.ORDER, PaymentOrders.CONTRACT, Issuances.OPENED);
        given(contracts.findLockedById(PaymentOrders.CONTRACT)).willReturn(Optional.of(contract));
        given(orders.findLockedByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.of(order));
        given(invoices.findByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.of(invoice));

        LockedInvoice locked = locks.lockInvoice(PaymentOrders.CONTRACT);

        assertThat(locked).isEqualTo(new LockedInvoice(contract, order, invoice));
        InOrder sequence = inOrder(contracts, orders, invoices);
        sequence.verify(contracts).findLockedById(PaymentOrders.CONTRACT);
        sequence.verify(orders).findLockedByContractId(PaymentOrders.CONTRACT);
        sequence.verify(invoices).findByContractId(PaymentOrders.CONTRACT);
    }

    @Test
    void orderWithoutInvoice_failsTheInvoiceLock() {
        given(contracts.findLockedById(PaymentOrders.CONTRACT)).willReturn(Optional.of(PaymentOrders.acceptedContract()));
        given(orders.findLockedByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.of(PaymentOrders.created()));
        given(invoices.findByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> locks.lockInvoice(PaymentOrders.CONTRACT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("у заказа на оплату договора КМ " + PaymentOrders.CONTRACT + " нет счёта");
    }

    @Test
    void missingOrder_failsTheInvoiceLockBeforeReadingInvoices() {
        given(contracts.findLockedById(PaymentOrders.CONTRACT)).willReturn(Optional.of(PaymentOrders.acceptedContract()));
        given(orders.findLockedByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> locks.lockInvoice(PaymentOrders.CONTRACT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("нет заказа на оплату");
        verifyNoInteractions(invoices);
    }
}
