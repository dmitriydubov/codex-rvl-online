package ru.projectx.contracts.service.payment;

import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.payment.PaymentOrder;

public record LockedOrder(Contract contract, PaymentOrder order) {
}
