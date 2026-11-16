package ru.projectx.contracts.service.payment;

import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;

public record LockedInvoice(Contract contract, PaymentOrder order, OrderInvoice invoice) {

    public LockedOrder withoutInvoice() {
        return new LockedOrder(contract, order);
    }
}
