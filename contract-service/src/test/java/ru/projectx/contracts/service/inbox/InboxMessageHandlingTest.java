package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.MDC;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.InboxMessage;
import ru.projectx.contracts.entity.InboxReceipt;
import ru.projectx.contracts.entity.InboxStatus;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.repository.InboxMessageRepository;

class InboxMessageHandlingTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final Set<String> TASKS = Set.of("ContractTaskUpserted", "ContractTaskCancelled");
    private static final InboxReceipt RECEIPT = new InboxReceipt(
            UUID.fromString("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11"), REQUEST,
            "rvl.contract-task.upserted", "ContractTaskUpserted", 2);
    private static final InboxReceipt ISSUED = new InboxReceipt(
            UUID.fromString("5b1e7d44-2c09-4a63-8f17-6e0b9a3c1d05"), REQUEST,
            "sogaz1c.lm-policy.issued", "LmPolicyIssued", 1);

    private final InboxMessageRepository messages = mock();
    private final InboxEventHandlers handlers = mock();
    private final InboxEventHandler handler = mock();
    private final InboxWork work = mock();
    private final PlatformTransactionManager transactions = mock();
    private final InboxMessageHandling handling = new InboxMessageHandling(messages, handlers,
            new TransactionTemplate(transactions), Clock.fixed(NOW, ZoneOffset.UTC));
    private final InboxMessage message = InboxMessage.receive(RECEIPT, "{\"data\":{}}", NOW.minusSeconds(60));

    @BeforeEach
    void messageIsStoredAndHandlerKnown() {
        given(messages.findById(message.id())).willReturn(Optional.of(message));
        given(handlers.forEventType("ContractTaskUpserted")).willReturn(handler);
        given(handlers.orderedByRequestVersion()).willReturn(TASKS);
        given(handler.ordering()).willReturn(InboxOrdering.BY_REQUEST_VERSION);
        given(handler.prepare("{\"data\":{}}")).willReturn(work);
    }

    @Test
    void firstVersionOfRequest_isPreparedOutsideAndAppliedInsideTheSecondTransaction() {
        given(messages.maxProcessedVersion(REQUEST, TASKS)).willReturn(Optional.empty());

        handling.process(message.id());

        InOrder order = inOrder(transactions, handler, work);
        order.verify(transactions).getTransaction(any());
        order.verify(transactions).commit(any());
        order.verify(handler).prepare("{\"data\":{}}");
        order.verify(transactions).getTransaction(any());
        order.verify(work).apply(NOW);
        order.verify(transactions).commit(any());
        assertThat(message.status()).isEqualTo(InboxStatus.PROCESSED);
        assertThat(message).hasFieldOrPropertyWithValue("processedAt", NOW);
    }

    @Test
    void handlerRunsWithMessageIdentifiersInTheLogContext() {
        given(messages.maxProcessedVersion(REQUEST, TASKS)).willReturn(Optional.empty());
        given(handler.prepare(anyString())).willAnswer(invocation -> {
            assertThat(MDC.get("messageId")).isEqualTo("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11");
            assertThat(MDC.get("individualRequestId")).isEqualTo(REQUEST.toString());
            return work;
        });

        handling.process(message.id());

        verify(work).apply(NOW);
        assertThat(MDC.get("messageId")).isNull();
    }

    @Test
    void higherVersionThanProcessed_isHandled() {
        given(messages.maxProcessedVersion(REQUEST, TASKS)).willReturn(Optional.of(1));

        handling.process(message.id());

        verify(work).apply(NOW);
        assertThat(message.status()).isEqualTo(InboxStatus.PROCESSED);
    }

    @Test
    void sameVersionAsProcessed_isSkippedWithReason() {
        given(messages.maxProcessedVersion(REQUEST, TASKS)).willReturn(Optional.of(2));

        handling.process(message.id());

        verify(handler, never()).prepare(anyString());
        assertThat(message.status()).isEqualTo(InboxStatus.SKIPPED);
        assertThat(message.errorMessage()).contains("версия объекта 2 не выше обработанной 2");
    }

    @Test
    void lowerVersionThanProcessed_isSkippedWithReason() {
        given(messages.maxProcessedVersion(REQUEST, TASKS)).willReturn(Optional.of(3));

        handling.process(message.id());

        assertThat(message.status()).isEqualTo(InboxStatus.SKIPPED);
        assertThat(message.errorMessage()).contains("версия объекта 2 не выше обработанной 3");
    }

    @Test
    void messageWithoutRequestOrdering_isHandledWhateverTheProcessedVersion() {
        InboxMessage issued = InboxMessage.receive(ISSUED, "{\"data\":{}}", NOW);
        InboxEventHandler issuedHandler = mock();
        given(messages.findById(issued.id())).willReturn(Optional.of(issued));
        given(handlers.forEventType("LmPolicyIssued")).willReturn(issuedHandler);
        given(issuedHandler.ordering()).willReturn(InboxOrdering.NONE);
        given(issuedHandler.prepare("{\"data\":{}}")).willReturn(work);

        handling.process(issued.id());

        verify(messages, never()).maxProcessedVersion(any(), any());
        verify(work).apply(NOW);
        assertThat(issued.status()).isEqualTo(InboxStatus.PROCESSED);
    }

    @Test
    void erasedPayload_isContractViolation() {
        InboxMessage erased = InboxMessage.receive(RECEIPT, null, NOW);
        given(messages.findById(erased.id())).willReturn(Optional.of(erased));
        given(messages.maxProcessedVersion(REQUEST, TASKS)).willReturn(Optional.empty());

        assertThatThrownBy(() -> handling.process(erased.id()))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("тело сообщения стёрто по сроку хранения");
        assertThat(erased.status()).isEqualTo(InboxStatus.NEW);
        verify(transactions).rollback(any());
    }

    @Test
    void preparationFailure_leavesMessageUntouchedWithoutASecondTransaction() {
        given(messages.maxProcessedVersion(REQUEST, TASKS)).willReturn(Optional.empty());
        given(handler.prepare(anyString())).willThrow(new ContractViolationException("нет обязательного поля data.policy"));

        assertThatThrownBy(() -> handling.process(message.id())).hasMessage("нет обязательного поля data.policy");
        assertThat(message.status()).isEqualTo(InboxStatus.NEW);
        verify(transactions).commit(any());
        verify(transactions, never()).rollback(any());
    }

    @Test
    void applicationFailure_rollsTheSecondTransactionBackAndLeavesMessageUntouched() {
        given(messages.maxProcessedVersion(REQUEST, TASKS)).willReturn(Optional.empty());
        org.mockito.BDDMockito.willThrow(new IllegalStateException("отказ")).given(work).apply(NOW);

        assertThatThrownBy(() -> handling.process(message.id())).hasMessage("отказ");
        assertThat(message.status()).isEqualTo(InboxStatus.NEW);
        verify(transactions).rollback(any());
    }

    @Test
    void messageFinishedWhileTheWorkWasPrepared_isNotAppliedInTheSecondTransaction() {
        given(handler.prepare("{\"data\":{}}")).willAnswer(invocation -> {
            message.markProcessed(NOW.minusSeconds(1));
            return work;
        });

        handling.process(message.id());

        verify(work, never()).apply(any());
        assertThat(message.status()).isEqualTo(InboxStatus.PROCESSED);
    }

    @Test
    void messageAlreadyFinished_isLeftAlone() {
        message.markProcessed(NOW.minusSeconds(1));

        handling.process(message.id());

        verify(handler, never()).prepare(anyString());
        verify(messages, never()).maxProcessedVersion(any(), any());
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
