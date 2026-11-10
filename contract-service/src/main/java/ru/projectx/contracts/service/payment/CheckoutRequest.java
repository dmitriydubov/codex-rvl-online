package ru.projectx.contracts.service.payment;

import java.time.Instant;
import ru.projectx.contracts.entity.payment.PayerEmail;

public record CheckoutRequest(String orderId, NewOrder order, Instant paymentDeadline, PayerEmail payer) {
}
