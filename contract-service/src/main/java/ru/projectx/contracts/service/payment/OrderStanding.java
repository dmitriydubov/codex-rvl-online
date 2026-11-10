package ru.projectx.contracts.service.payment;

import java.util.Map;
import java.util.Optional;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.InvoiceView;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.entity.payment.PaymentOrderView;

public final class OrderStanding {

    private static final Map<PaymentOrderState, String> ORDERS = Map.of(
            PaymentOrderState.AWAITING_CREATION, "ждёт создания",
            PaymentOrderState.CREATED, "создан",
            PaymentOrderState.REJECTED, "отклонён",
            PaymentOrderState.CANCEL_PENDING, "ждёт отмены",
            PaymentOrderState.CANCELLED, "отменён",
            PaymentOrderState.CANCEL_REJECTED, "с отклонённой отменой",
            PaymentOrderState.EXPIRED, "закрыт по сроку оплаты");
    private static final Map<InvoiceState, String> INVOICES = Map.of(
            InvoiceState.AWAITING_DOCUMENT, "ждёт подтверждения документа",
            InvoiceState.DOCUMENT_UNCONFIRMED, "ждёт подтверждения документа",
            InvoiceState.READY, "готов к оплате",
            InvoiceState.CHECKOUT, "ждёт счёта",
            InvoiceState.ISSUED, "ждёт оплаты",
            InvoiceState.REJECTED, "с отклонённым счётом",
            InvoiceState.PAYMENT_ERROR, "в ошибке оплаты",
            InvoiceState.PAID, "уже оплачен",
            InvoiceState.OVERDUE, "уже просрочен");

    private OrderStanding() {
    }

    public static String of(PaymentOrderView order, Optional<InvoiceView> invoice) {
        return invoice.filter(known -> order.state() == PaymentOrderState.CREATED)
                .map(known -> INVOICES.get(known.state()))
                .orElseGet(() -> ORDERS.get(order.state()));
    }
}
