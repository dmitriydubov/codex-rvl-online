package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import ru.projectx.contracts.entity.inbox.InboxReceipt;

class InboxLogContextTest {

    private static final InboxReceipt RECEIPT = new InboxReceipt(
            UUID.fromString("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11"),
            UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"), "topic", "ContractTaskUpserted", 1);

    @Test
    void run_putsIdentifiersIntoMdcForTheActionAndRemovesThemAfterwards() {
        AtomicReference<String> messageId = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();

        InboxLogContext.run(RECEIPT, () -> {
            messageId.set(MDC.get(InboxLogContext.MESSAGE_ID));
            requestId.set(MDC.get(InboxLogContext.INDIVIDUAL_REQUEST_ID));
        });

        assertThat(messageId.get()).isEqualTo("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11");
        assertThat(requestId.get()).isEqualTo("61a7982c-a0b4-4bbf-9527-60b40b752803");
        assertThat(MDC.get(InboxLogContext.MESSAGE_ID)).isNull();
        assertThat(MDC.get(InboxLogContext.INDIVIDUAL_REQUEST_ID)).isNull();
    }

    @Test
    void run_forAnOrderEventKeepsTheDocumentIdentifierOutOfTheRequestKeyButSetsTheMessageKeys() {
        InboxReceipt orderEvent = new InboxReceipt(UUID.fromString("d1a0c001-5e2f-4a3b-8c4d-000000000001"),
                UUID.fromString("0196f0a2-0000-7000-8000-000000000011"), "ordering.order.events", "PaymentOrderEvent", 0);
        AtomicReference<String> messageId = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> traceId = new AtomicReference<>();

        InboxLogContext.run(orderEvent, () -> {
            messageId.set(MDC.get(InboxLogContext.MESSAGE_ID));
            requestId.set(MDC.get(InboxLogContext.INDIVIDUAL_REQUEST_ID));
            traceId.set(RequestIds.current());
        });

        assertThat(messageId.get()).isEqualTo("d1a0c001-5e2f-4a3b-8c4d-000000000001");
        assertThat(requestId.get()).isNull();
        assertThat(traceId.get()).isEqualTo("d1a0c001-5e2f-4a3b-8c4d-000000000001");
        assertThat(MDC.get(InboxLogContext.MESSAGE_ID)).isNull();
    }

    @Test
    void run_makesTheMessageIdTheRequestIdOfCallsToTheReferenceService() {
        AtomicReference<String> requestId = new AtomicReference<>();

        InboxLogContext.run(RECEIPT, () -> requestId.set(RequestIds.current()));

        assertThat(requestId.get()).isEqualTo("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11");
        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }

    @Test
    void run_removesIdentifiersWhenTheActionFailsAndPassesTheFailureOn() {
        assertThatThrownBy(() -> InboxLogContext.run(RECEIPT, () -> {
            throw new IOException("канал");
        })).isInstanceOf(IOException.class).hasMessage("канал");

        assertThat(MDC.get(InboxLogContext.MESSAGE_ID)).isNull();
        assertThat(MDC.get(InboxLogContext.INDIVIDUAL_REQUEST_ID)).isNull();
        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }
}
