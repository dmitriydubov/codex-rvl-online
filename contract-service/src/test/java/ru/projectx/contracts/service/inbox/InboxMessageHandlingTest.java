package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.InboxMessage;
import ru.projectx.contracts.entity.InboxReceipt;
import ru.projectx.contracts.entity.InboxStatus;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.repository.InboxMessageRepository;

class InboxMessageHandlingTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final InboxReceipt RECEIPT = new InboxReceipt(
            UUID.fromString("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11"), REQUEST,
            "rvl.contract-task.upserted", "ContractTaskUpserted", 2);

    private final InboxMessageRepository messages = mock();
    private final InboxEventHandlers handlers = mock();
    private final InboxEventHandler handler = mock();
    private final InboxMessageHandling handling =
            new InboxMessageHandling(messages, handlers, Clock.fixed(NOW, ZoneOffset.UTC));
    private final InboxMessage message = InboxMessage.receive(RECEIPT, "{\"data\":{}}", NOW.minusSeconds(60));

    @BeforeEach
    void messageIsStored() {
        given(messages.findById(message.id())).willReturn(Optional.of(message));
        given(handlers.forEventType("ContractTaskUpserted")).willReturn(handler);
    }

    @Test
    void firstVersionOfRequest_isHandledAndMarkedProcessed() {
        given(messages.maxProcessedVersion(REQUEST)).willReturn(Optional.empty());

        handling.process(message.id());

        verify(handler).handle("{\"data\":{}}", NOW);
        assertThat(message.status()).isEqualTo(InboxStatus.PROCESSED);
        assertThat(message).hasFieldOrPropertyWithValue("processedAt", NOW);
    }

    @Test
    void higherVersionThanProcessed_isHandled() {
        given(messages.maxProcessedVersion(REQUEST)).willReturn(Optional.of(1));

        handling.process(message.id());

        verify(handler).handle(anyString(), any());
        assertThat(message.status()).isEqualTo(InboxStatus.PROCESSED);
    }

    @Test
    void sameVersionAsProcessed_isSkippedWithReason() {
        given(messages.maxProcessedVersion(REQUEST)).willReturn(Optional.of(2));

        handling.process(message.id());

        verify(handler, never()).handle(anyString(), any());
        assertThat(message.status()).isEqualTo(InboxStatus.SKIPPED);
        assertThat(message.errorMessage()).contains("версия объекта 2 не выше обработанной 2");
    }

    @Test
    void lowerVersionThanProcessed_isSkippedWithReason() {
        given(messages.maxProcessedVersion(REQUEST)).willReturn(Optional.of(3));

        handling.process(message.id());

        assertThat(message.status()).isEqualTo(InboxStatus.SKIPPED);
        assertThat(message.errorMessage()).contains("версия объекта 2 не выше обработанной 3");
    }

    @Test
    void erasedPayload_isContractViolation() {
        InboxMessage erased = InboxMessage.receive(RECEIPT, null, NOW);
        given(messages.findById(erased.id())).willReturn(Optional.of(erased));
        given(messages.maxProcessedVersion(REQUEST)).willReturn(Optional.empty());

        assertThatThrownBy(() -> handling.process(erased.id()))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("тело сообщения стёрто по сроку хранения");
        assertThat(erased.status()).isEqualTo(InboxStatus.NEW);
    }

    @Test
    void handlerFailure_leavesMessageUntouched() {
        given(messages.maxProcessedVersion(REQUEST)).willReturn(Optional.empty());
        given(handlers.forEventType("ContractTaskUpserted")).willThrow(new ContractViolationException("неизвестный тип события X"));

        assertThatThrownBy(() -> handling.process(message.id())).hasMessage("неизвестный тип события X");
        assertThat(message.status()).isEqualTo(InboxStatus.NEW);
    }

    @Test
    void messageAlreadyFinished_isLeftAlone() {
        message.markProcessed(NOW.minusSeconds(1));

        handling.process(message.id());

        verify(handler, never()).handle(anyString(), any());
        verify(messages, never()).maxProcessedVersion(any());
        assertThat(message.status()).isEqualTo(InboxStatus.PROCESSED);
    }

    @Test
    void unknownId_isIllegalState() {
        UUID unknown = UUID.fromString("00000000-0000-4000-8000-000000000001");
        given(messages.findById(unknown)).willReturn(Optional.empty());

        assertThatThrownBy(() -> handling.process(unknown)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(unknown.toString());
    }
}
