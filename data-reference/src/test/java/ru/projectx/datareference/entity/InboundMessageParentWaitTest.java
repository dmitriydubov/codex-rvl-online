package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Защищает переходы сообщения 1С, которое ждёт родителя: начало ожидания, повторное ожидание, отказ по сроку и выход
 * из ожидания.
 *
 * <p>Момент начала ожидания отсчитывает срок. Если повторное ожидание сдвинет его, сообщение не уйдёт в отказ
 * никогда; если выход из ожидания его не сотрёт, отказ по другой причине потеряет тело по сроку хранения, как отказ по
 * сроку ожидания.
 */
class InboundMessageParentWaitTest {

    private static final UUID MESSAGE_ID = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-8e9f0a1b2c31");
    private static final UUID RECORD = UUID.fromString("5f14a8d2-c3e7-4b90-a1d6-9e2b7c4f0a83");
    private static final AwaitedParent BRANCH =
            new AwaitedParent(ParentKind.CONTRAGENT, UUID.fromString("6d0e4a7b-2c95-4f18-8b3d-e9a1c7f50d62"));
    private static final AwaitedParent HEAD_OFFICE =
            new AwaitedParent(ParentKind.CONTRAGENT, UUID.fromString("b515b8fc-d13c-4b0b-aae3-33e0ca86ff1c"));
    private static final Instant RECEIVED_AT = Instant.parse("2026-10-06T09:00:00Z");
    private static final Instant WAITING_SINCE = RECEIVED_AT.plus(Duration.ofMinutes(1));
    private static final Instant LATER = WAITING_SINCE.plus(Duration.ofHours(3));

    @Test
    void awaitingParent_namesTheParent_andRemembersWhatAndSinceWhen() {
        InboundMessage message = received();

        message.markAwaitingParent(BRANCH, RECORD, WAITING_SINCE);

        assertThat(message.outcome()).isEqualTo(new InboundOutcome(InboundStatus.AWAITING_PARENT, 0, null,
                "ждёт родителя: контрагент 6d0e4a7b-2c95-4f18-8b3d-e9a1c7f50d62", null,
                new ParentWait(BRANCH, WAITING_SINCE, RECORD)));
    }

    @Test
    void waitingAgain_keepsTheMomentOfTheFirstWait() {
        InboundMessage message = received();
        message.markAwaitingParent(BRANCH, RECORD, WAITING_SINCE);

        message.markAwaitingParent(HEAD_OFFICE, RECORD, LATER);

        assertThat(message.outcome().parentWait()).contains(new ParentWait(HEAD_OFFICE, WAITING_SINCE, RECORD));
        assertThat(message.outcome().lastError()).isEqualTo(HEAD_OFFICE.waitingReason());
    }

    /**
     * Дежурный возвращает отклонённое по сроку сообщение в разбор обычным SQL, переводя строку в NEW; в сущности такого
     * перехода нет, поэтому тест ставит статус так же, как его поставил бы SQL. Новое ожидание отсчитывает срок заново.
     */
    @Test
    void waitingAfterManualReturnToParsing_startsTheWaitAnew() {
        InboundMessage message = waiting();
        message.markParentWaitExpired();
        ReflectionTestUtils.setField(message, "status", InboundStatus.NEW);

        message.markAwaitingParent(BRANCH, RECORD, LATER);

        assertThat(message.outcome().parentWait()).contains(new ParentWait(BRANCH, LATER, RECORD));
    }

    /** Часть передачи прайса, вручную возвращённая в разбор, ждёт услугу заново, а не с прежнего момента. */
    @Test
    void transferPartAfterManualReturnToParsing_startsTheWaitAnew() {
        InboundMessage part = waiting();
        part.markParentWaitExpired();
        ReflectionTestUtils.setField(part, "status", InboundStatus.NEW);

        part.markBuffered(UUID.randomUUID(), 1, 1);
        part.markAwaitingParent(BRANCH, null, LATER);

        assertThat(part.outcome().parentWait().orElseThrow().since()).isEqualTo(LATER);
    }

    /**
     * Первая попытка после ручного возврата упала на временной ошибке базы. Повтор ждёт родителя заново: прежний срок
     * отклонил бы сообщение в том же проходе.
     */
    @Test
    void waitingAfterManualReturnAndRetry_startsTheWaitAnew() {
        InboundMessage message = returnedToParsingAfterExpiry();

        message.markRetrying("временный отказ базы", LATER);
        message.markAwaitingParent(BRANCH, RECORD, LATER);

        assertThat(message.outcome().parentWait()).contains(new ParentWait(BRANCH, LATER, RECORD));
    }

    /** То же для части передачи прайса: после повтора она собирается в буфере и ждёт услугу заново. */
    @Test
    void transferPartAfterManualReturnAndRetry_startsTheWaitAnew() {
        InboundMessage part = returnedToParsingAfterExpiry();

        part.markRetrying("временный отказ базы", LATER);
        part.markBuffered(UUID.randomUUID(), 1, 1);
        part.markAwaitingParent(BRANCH, null, LATER);

        assertThat(part.outcome().parentWait().orElseThrow().since()).isEqualTo(LATER);
    }

    /** Ждущая часть, которую сборщик снова кладёт в буфер при пересмотре, сохраняет момент начала ожидания. */
    @Test
    void waitingTransferPartBufferedAgain_keepsTheMomentOfTheWait() {
        InboundMessage part = waiting();

        part.markBuffered(UUID.randomUUID(), 1, 1);
        part.markAwaitingParent(BRANCH, null, LATER);

        assertThat(part.outcome().parentWait().orElseThrow().since()).isEqualTo(WAITING_SINCE);
    }

    @Test
    void expiredWait_isFinalRejectionNamingTheParent_andKeepsTheWait() {
        InboundMessage message = received();
        message.markAwaitingParent(BRANCH, RECORD, WAITING_SINCE);

        message.markParentWaitExpired();

        assertThat(message.outcome()).isEqualTo(new InboundOutcome(InboundStatus.FAILED, 0, null,
                "срок ожидания родителя истёк: контрагент 6d0e4a7b-2c95-4f18-8b3d-e9a1c7f50d62", null,
                new ParentWait(BRANCH, WAITING_SINCE, RECORD)));
    }

    @Test
    void expiringAMessageThatWaitsForNothing_isAProgrammingError() {
        InboundMessage message = received();

        assertThatIllegalStateException()
                .isThrownBy(message::markParentWaitExpired)
                .withMessageContaining(MESSAGE_ID.toString());
    }

    @Test
    void applied_forgetsTheWait() {
        InboundMessage message = waiting();

        message.markProcessed(LATER);

        assertThat(message.outcome().parentWait()).isEmpty();
    }

    @Test
    void skipped_forgetsTheWait() {
        InboundMessage message = waiting();

        message.markSkipped("устарело", LATER);

        assertThat(message.outcome().parentWait()).isEmpty();
    }

    /** Отказ по другой причине ожидание стирает: по нему тело отказа стиралось бы как у отказа по сроку ожидания. */
    @Test
    void failedForAnotherReason_forgetsTheWait() {
        InboundMessage message = waiting();

        message.markFailed("в договоре нет даты начала");

        assertThat(message.outcome().parentWait()).isEmpty();
    }

    /** Временный отказ при пересмотре ожидание не стирает: срок продолжает идти от первого ожидания. */
    @Test
    void retrying_keepsTheWait() {
        InboundMessage message = waiting();

        message.markRetrying("временный отказ базы", LATER);

        assertThat(message.outcome().parentWait()).contains(new ParentWait(BRANCH, WAITING_SINCE, RECORD));
    }

    private static InboundMessage waiting() {
        InboundMessage message = received();
        message.markAwaitingParent(BRANCH, RECORD, WAITING_SINCE);
        return message;
    }

    /** Отклонённое по сроку сообщение, которое дежурный вернул в разбор SQL-запросом, переводя строку в NEW. */
    private static InboundMessage returnedToParsingAfterExpiry() {
        InboundMessage message = waiting();
        message.markParentWaitExpired();
        ReflectionTestUtils.setField(message, "status", InboundStatus.NEW);
        return message;
    }

    private static InboundMessage received() {
        return InboundMessage.receive(new InboundReceipt(MESSAGE_ID, OneCStream.LPU_CONTRACTS,
                "1c-usk_to_rvl-online_contragents-contracts-changes", RECEIVED_AT), "{\"data\":{}}");
    }
}
