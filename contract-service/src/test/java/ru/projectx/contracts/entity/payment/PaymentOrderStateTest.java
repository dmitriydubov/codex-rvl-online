package ru.projectx.contracts.entity.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PaymentOrderStateTest {

    @ParameterizedTest
    @EnumSource(names = {"CANCEL_PENDING", "CANCELLED", "CANCEL_REJECTED", "EXPIRED"})
    void orderThatLeftTheWork_isWithdrawn(PaymentOrderState state) {
        assertThat(state.withdrawn()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(names = {"AWAITING_CREATION", "CREATED", "REJECTED"})
    void orderStillInWork_isNotWithdrawn(PaymentOrderState state) {
        assertThat(state.withdrawn()).isFalse();
    }
}
