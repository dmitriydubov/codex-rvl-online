package ru.projectx.contracts.service.payment;

import org.jspecify.annotations.Nullable;

public interface PaymentOrderOpening {

    OrderOpening forPayer(@Nullable String email);
}
