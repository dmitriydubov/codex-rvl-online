package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.InvoiceView;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.support.PaymentOrders;

class OrderStandingTest {

    private static PaymentOrder orderIn(PaymentOrderState state) {
        PaymentOrder order = PaymentOrders.created();
        ReflectionTestUtils.setField(order, "state", state);
        return order;
    }

    @ParameterizedTest
    @CsvSource({
            "AWAITING_CREATION, ждёт создания",
            "CREATED, создан",
            "REJECTED, отклонён",
            "CANCEL_PENDING, ждёт отмены",
            "CANCELLED, отменён",
            "CANCEL_REJECTED, с отклонённой отменой",
            "EXPIRED, закрыт по сроку оплаты"})
    void orderWithoutInvoice_isNamedByItsOwnState(PaymentOrderState state, String standing) {
        assertThat(OrderStanding.of(orderIn(state).view(), Optional.empty())).isEqualTo(standing);
    }

    @ParameterizedTest
    @CsvSource({
            "AWAITING_DOCUMENT, ждёт подтверждения документа",
            "DOCUMENT_UNCONFIRMED, ждёт подтверждения документа",
            "READY, готов к оплате",
            "CHECKOUT, ждёт счёта",
            "ISSUED, ждёт оплаты",
            "REJECTED, с отклонённым счётом",
            "PAYMENT_ERROR, в ошибке оплаты",
            "PAID, уже оплачен",
            "OVERDUE, уже просрочен"})
    void createdOrderWithInvoice_isNamedByTheInvoiceState(InvoiceState state, String standing) {
        InvoiceView invoice = Invoices.in(state).view();

        assertThat(OrderStanding.of(orderIn(PaymentOrderState.CREATED).view(), Optional.of(invoice)))
                .isEqualTo(standing);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "CREATED")
    void orderThatIsNotCreated_isNamedByItsOwnStateEvenWithInvoice(PaymentOrderState state) {
        InvoiceView invoice = Invoices.issued().view();

        assertThat(OrderStanding.of(orderIn(state).view(), Optional.of(invoice)))
                .isEqualTo(OrderStanding.of(orderIn(state).view(), Optional.empty()))
                .isNotEqualTo("ждёт оплаты");
    }
}
