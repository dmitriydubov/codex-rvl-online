package ru.projectx.contracts.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderingOperationTest {

    @Test
    void checkout_isTaggedByItsOwnNameAmongTheOperationsOfTheOrderingService() {
        assertThat(OrderingOperation.CHECKOUT.tag()).isEqualTo("checkout");
        assertThat(OrderingOperation.values()).extracting(OrderingOperation::tag).doesNotHaveDuplicates();
    }
}
