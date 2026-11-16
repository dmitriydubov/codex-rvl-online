package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.PaymentOrders;

class LockedInvoiceTest {

    @Test
    void withoutInvoice_keepsTheSameLockedContractAndOrder() {
        Contract contract = PaymentOrders.acceptedContract();
        PaymentOrder order = PaymentOrders.created();
        OrderInvoice invoice = OrderInvoice.open(PaymentOrders.ORDER, PaymentOrders.CONTRACT, Issuances.OPENED);

        LockedOrder locked = new LockedInvoice(contract, order, invoice).withoutInvoice();

        assertThat(locked.contract()).isSameAs(contract);
        assertThat(locked.order()).isSameAs(order);
    }
}
