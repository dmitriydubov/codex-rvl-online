package ru.projectx.contracts.service.payment.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.service.inbox.DecidingInboxWork;
import ru.projectx.contracts.service.inbox.InboxOrdering;
import ru.projectx.contracts.service.inbox.InboxWork;
import ru.projectx.contracts.service.payment.IgnoredEvent;
import ru.projectx.contracts.service.payment.PaymentMetrics;
import ru.projectx.contracts.support.CapturedLog;

class PaymentOrderEventHandlerTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:30:00Z");
    private static final String PAYLOAD = "{\"eventType\": \"paid\"}";
    private static final OrderEvent EVENT = new OrderEvent(Optional.of("paid"), Optional.of("order-1"),
            new EventDocument.Foreign("стенд"), Optional.empty());

    private final OrderEventParser parser = mock();
    private final OrderEventApplication application = mock();
    private final PaymentMetrics metrics = mock();
    private final PaymentOrderEventHandler handler = new PaymentOrderEventHandler(parser, application, metrics);
    private final CapturedLog log = CapturedLog.of(PaymentOrderEventHandler.class);

    @AfterEach
    void releaseLog() {
        log.close();
    }

    @Test
    void handler_takesOrderEventsWithoutOrderingByRequestVersion() {
        assertThat(handler.eventType()).isEqualTo("PaymentOrderEvent");
        assertThat(handler.ordering()).isEqualTo(InboxOrdering.NONE);
    }

    @Test
    void appliedEvent_isNotSkippedNotCountedAndNotLogged() {
        given(parser.parse(PAYLOAD)).willReturn(EVENT);
        given(application.apply(EVENT, NOW)).willReturn(Optional.empty());

        assertThat(handler.prepare(PAYLOAD).applyOrSkip(NOW)).isEmpty();

        verify(application).apply(EVENT, NOW);
        verifyNoInteractions(metrics);
        assertThat(log.events()).isEmpty();
    }

    @Test
    void ignoredEvent_isSkippedWithItsReasonCountedByReasonAndLoggedAsWarning() {
        given(parser.parse(PAYLOAD)).willReturn(EVENT);
        given(application.apply(EVENT, NOW)).willReturn(
                Optional.of(new Ignored(IgnoredEvent.OUT_OF_ORDER, "событие paid не по порядку: заказ уже оплачен")));

        Optional<String> skipped = handler.prepare(PAYLOAD).applyOrSkip(NOW);

        assertThat(skipped).contains("событие paid не по порядку: заказ уже оплачен");
        verify(metrics).countIgnored(IgnoredEvent.OUT_OF_ORDER);
        assertThat(log.at(Level.WARN))
                .containsExactly("событие Ordering не применено: событие paid не по порядку: заказ уже оплачен");
        assertThat(log.events()).hasSize(1);
        assertThat(log.events().get(0).getThrowableProxy()).isNull();
    }

    @Test
    void work_decidesItselfWhetherTheEventIsSkippedAndAppliesItOnPlainApplyToo() {
        given(parser.parse(PAYLOAD)).willReturn(EVENT);
        given(application.apply(EVENT, NOW)).willReturn(Optional.empty());

        InboxWork work = handler.prepare(PAYLOAD);
        work.apply(NOW);

        assertThat(work).isInstanceOf(DecidingInboxWork.class);
        verify(application).apply(EVENT, NOW);
    }

    @Test
    void unreadableBody_failsThePreparationBeforeAnythingIsApplied() {
        given(parser.parse(PAYLOAD)).willThrow(new ContractViolationException("тело не читается как JSON"));

        assertThatThrownBy(() -> handler.prepare(PAYLOAD)).isInstanceOf(ContractViolationException.class);

        verifyNoInteractions(application, metrics);
    }

    @Test
    void body_isParsedOnceAtPreparationAndNotAtApplication() {
        given(parser.parse(PAYLOAD)).willReturn(EVENT);
        given(application.apply(EVENT, NOW)).willReturn(Optional.empty());

        handler.prepare(PAYLOAD);

        verify(parser).parse(PAYLOAD);
        verifyNoInteractions(application);
    }
}
