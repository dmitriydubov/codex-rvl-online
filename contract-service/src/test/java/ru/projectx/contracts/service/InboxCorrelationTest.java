package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;

class InboxCorrelationTest {

    private static final UUID CORRELATION = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ContractTaskUpserted", "ContractTaskCancelled", "KmContractRegistered", "LmPolicyIssued",
        "ContractTaskRenamed"})
    void messagesOfTheRequestServiceOfTheRegistryAndOfUnknownKinds_nameTheirRequest(String eventType) {
        InboxCorrelation.of(eventType).put(CORRELATION);

        assertThat(MDC.get(InboxLogContext.INDIVIDUAL_REQUEST_ID)).isEqualTo(CORRELATION.toString());
    }

    @Test
    void orderEvent_doesNotNameARequestBecauseItsCorrelationIsTheDocumentOrTheEventKey() {
        InboxCorrelation.of("PaymentOrderEvent").put(CORRELATION);

        assertThat(MDC.get(InboxLogContext.INDIVIDUAL_REQUEST_ID)).isNull();
    }
}
