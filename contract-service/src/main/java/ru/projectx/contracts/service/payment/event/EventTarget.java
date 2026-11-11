package ru.projectx.contracts.service.payment.event;

import java.util.Optional;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.OrderStanding;

record EventTarget(LockedOrder locked, Optional<OrderInvoice> invoice) {

    Optional<LockedInvoice> invoiced() {
        return invoice.map(known -> new LockedInvoice(locked.contract(), locked.order(), known));
    }

    String standing() {
        return OrderStanding.of(locked.order().view(), invoice.map(OrderInvoice::view));
    }
}
