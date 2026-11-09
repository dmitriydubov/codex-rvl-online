package ru.projectx.contracts.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderingOrderFailureExceptionTest {

    @Test
    void concernsOneOrderAndKeepsItsCause() {
        OrderingRejectedException search = new OrderingRejectedException(OrderingOperation.FIND_ORDERS, "C");

        OrderingOrderFailureException failure =
                new OrderingOrderFailureException(OrderingOperation.FIND_ORDERS, "поиск отклонён", search);

        assertThat(failure.serviceWide()).isFalse();
        assertThat(failure.operation()).isEqualTo(OrderingOperation.FIND_ORDERS);
        assertThat(failure).hasMessage("сервис управления заказами не обработал заказ: find_orders, поиск отклонён")
                .hasCause(search);
    }

    @Test
    void outageOfTheService_isServiceWide() {
        assertThat(new OrderingUnavailableException(OrderingOperation.TOKEN, "HTTP 503").serviceWide()).isTrue();
        assertThat(new CreationInProgressException().serviceWide()).isFalse();
    }
}
