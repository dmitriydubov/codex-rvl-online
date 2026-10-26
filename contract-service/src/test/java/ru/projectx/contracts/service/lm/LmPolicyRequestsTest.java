package ru.projectx.contracts.service.lm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.Limit;
import ru.projectx.contracts.config.LmPolicyRequestProperties;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.service.reference.ReferenceMetrics;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class LmPolicyRequestsTest {

    private static final ExternalIdentifiers IDENTIFIERS = new ExternalIdentifiers(
            UUID.fromString("6b02f1cc-9d47-4e35-8a20-7f1c3e5b09a2"), UUID.fromString("8c41e2ab-55d0-4a17-9b63-2f0e7c1d4a88"),
            UUID.fromString("1d9a6e77-4c02-4b88-a15e-30f7b2c9de41"));
    private static final IssueError ARCHIVED = new IssueError("PROGRAM_ARCHIVED", "программа страхования в архиве");

    private final LmPolicyRepository policies = mock();
    private final LmPolicyReferences references = mock();
    private final LmPolicyRequestRecording recording = mock();
    private final ReferenceMetrics metrics = mock();
    private final LmPolicyRequests requests = new LmPolicyRequests(policies, references, recording, metrics,
            new LmPolicyRequestProperties(Duration.ofSeconds(10), 7, Duration.ofMinutes(1), Duration.ofHours(1)));
    private final LmPolicy first = Issuances.policy("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39", Tasks.CHILD);
    private final LmPolicy second = Issuances.policy("9f3e1c28-6b55-4da1-83f4-82a1d7b56e4a", Tasks.SPOUSE);
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();

    @BeforeEach
    void twoNewPoliciesAreDue() {
        given(policies.findByStatusOrderByCreatedAtAscIdAsc(LmPolicyStatus.NEW, Limit.of(7)))
                .willReturn(List.of(first, second));
        journal.start();
        logger().addAppender(journal);
    }

    @AfterEach
    void stopJournal() {
        logger().detachAppender(journal);
        journal.stop();
    }

    @Test
    void resolvedPolicies_areSubmittedInOrderWithTheirIdentifiers() {
        given(references.resolve(any())).willReturn(new RequestResolution.Resolved(IDENTIFIERS));

        assertThat(requests.prepareDue()).isEqualTo(2);

        InOrder order = inOrder(recording);
        order.verify(recording).submit(first.id(), IDENTIFIERS);
        order.verify(recording).submit(second.id(), IDENTIFIERS);
        verifyNoInteractions(metrics);
        assertThat(journal.list).isEmpty();
    }

    @Test
    void refusedPolicy_isRefusedWithItsErrorAndThePassGoesOn() {
        given(references.resolve(any())).willReturn(new RequestResolution.Refused(ARCHIVED),
                new RequestResolution.Resolved(IDENTIFIERS));

        assertThat(requests.prepareDue()).isEqualTo(2);

        InOrder order = inOrder(recording);
        order.verify(recording).refuse(first.id(), ARCHIVED);
        order.verify(recording).submit(second.id(), IDENTIFIERS);
    }

    @Test
    void unavailableReference_endsThePassWithAWarningAndACountedKindLeavingTheRestForTheNextPass() {
        given(references.resolve(first.state())).willReturn(new RequestResolution.Unavailable(ReferenceKind.PROGRAM));

        assertThat(requests.prepareDue()).isZero();

        verify(references).resolve(first.state());
        verifyNoInteractions(recording);
        verify(metrics).countUnavailable(ReferenceKind.PROGRAM);
        assertThat(journal.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("полис ЛМ " + first.id() + " участника " + Tasks.CHILD + " заявки " + Tasks.REQUEST
                        + ": справочный сервис недоступен, запрос отложен до следующего прохода");
    }

    @Test
    void eachPolicy_isPreparedUnderItsOwnIdAsTheRequestIdOfCallsToTheReferenceService() {
        List<String> requestIds = new ArrayList<>();
        given(references.resolve(any())).willAnswer(call -> {
            requestIds.add(RequestIds.current());
            return new RequestResolution.Resolved(IDENTIFIERS);
        });

        requests.prepareDue();

        assertThat(requestIds).containsExactly(first.id().toString(), second.id().toString());
        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }

    @Test
    void requestId_isRemovedWhenThePreparationFails() {
        given(references.resolve(any())).willThrow(new IllegalStateException("сбой"));

        assertThatThrownBy(requests::prepareDue)
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }

    @Test
    void policyRowBusyLongerThanTheLockTimeout_endsThePassWithThatFailureLeavingTheRestForTheNextPass() {
        CannotAcquireLockException busy = new CannotAcquireLockException("строка занята");
        given(references.resolve(any())).willReturn(new RequestResolution.Resolved(IDENTIFIERS));
        doThrow(busy).when(recording).submit(first.id(), IDENTIFIERS);

        assertThatThrownBy(requests::prepareDue).isSameAs(busy);

        verify(recording, never()).submit(second.id(), IDENTIFIERS);
    }

    @Test
    void nothingDue_preparesNothing() {
        given(policies.findByStatusOrderByCreatedAtAscIdAsc(LmPolicyStatus.NEW, Limit.of(7))).willReturn(List.of());

        assertThat(requests.prepareDue()).isZero();
        verifyNoInteractions(references, recording);
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(LmPolicyRequests.class);
    }
}
