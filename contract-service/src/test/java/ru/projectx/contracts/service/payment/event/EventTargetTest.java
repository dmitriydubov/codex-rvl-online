package ru.projectx.contracts.service.payment.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.support.PaymentOrders;

class EventTargetTest {

    @Test
    void targetWithInvoice_givesTheLockedInvoiceAndStandsWhereItsInvoiceStands() {
        LockedOrder locked = new LockedOrder(Invoices.invoicedContract(), PaymentOrders.created());
        OrderInvoice invoice = Invoices.issued();
        EventTarget target = new EventTarget(locked, Optional.of(invoice));

        assertThat(target.invoiced()).contains(new LockedInvoice(locked.contract(), locked.order(), invoice));
        assertThat(target.standing()).isEqualTo("ждёт оплаты");
    }

    @Test
    void targetWithoutInvoice_givesNoLockedInvoiceAndStandsWhereItsOrderStands() {
        PaymentOrder order = PaymentOrders.cancelPending();
        EventTarget target = new EventTarget(new LockedOrder(Invoices.invoicedContract(), order), Optional.empty());

        assertThat(target.invoiced()).isEmpty();
        assertThat(target.standing()).isEqualTo("ждёт отмены");
    }
}
