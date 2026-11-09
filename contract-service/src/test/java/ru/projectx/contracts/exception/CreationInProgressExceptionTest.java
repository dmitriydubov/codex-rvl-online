package ru.projectx.contracts.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreationInProgressExceptionTest {

    @Test
    void namesTheConcurrentPassNotAnOutageAndStaysRetryable() {
        CreationInProgressException busy = new CreationInProgressException();

        assertThat(busy).isInstanceOf(OrderingUnavailableException.class)
                .hasMessage("создание заказа начато другим проходом, заказ будет найден поиском");
        assertThat(busy.operation()).isEqualTo(OrderingOperation.CREATE_ORDER);
    }
}
