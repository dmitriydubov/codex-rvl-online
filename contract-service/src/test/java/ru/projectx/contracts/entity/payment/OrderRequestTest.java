package ru.projectx.contracts.entity.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderRequestTest {

    @ParameterizedTest
    @CsvSource({"false, false, true, false", "false, true, true, true", "true, false, true, false",
        "true, true, true, true"})
    void creationRequest_marksTheCreationAndKeepsTheCancellationMark(boolean creation, boolean cancellation,
            boolean creationAfter, boolean cancellationAfter) {
        assertThat(OrderRequest.CREATION.after(new OrderRequests(creation, cancellation)))
                .isEqualTo(new OrderRequests(creationAfter, cancellationAfter));
    }

    @ParameterizedTest
    @CsvSource({"false, false, false, true", "true, false, true, true", "false, true, false, true",
        "true, true, true, true"})
    void cancellationRequest_marksTheCancellationAndKeepsTheCreationMark(boolean creation, boolean cancellation,
            boolean creationAfter, boolean cancellationAfter) {
        assertThat(OrderRequest.CANCELLATION.after(new OrderRequests(creation, cancellation)))
                .isEqualTo(new OrderRequests(creationAfter, cancellationAfter));
    }
}
