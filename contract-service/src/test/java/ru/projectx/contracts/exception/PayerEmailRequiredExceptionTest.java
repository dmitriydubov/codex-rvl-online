package ru.projectx.contracts.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PayerEmailRequiredExceptionTest {

    @Test
    void message_namesTheMissingFieldAndWhyItIsRequired() {
        assertThat(new PayerEmailRequiredException())
                .hasMessage("в акцепте нет адреса электронной почты плательщика, а оплата включена")
                .hasNoCause();
    }
}
