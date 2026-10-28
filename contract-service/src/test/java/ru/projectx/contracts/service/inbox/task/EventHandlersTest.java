package ru.projectx.contracts.service.inbox.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.message.task.ContractTask;
import ru.projectx.contracts.message.task.ContractTaskCancellation;
import ru.projectx.contracts.service.inbox.InboxOrdering;
import ru.projectx.contracts.service.inbox.InboxWork;
import ru.projectx.contracts.service.issuance.IssuanceService;
import ru.projectx.contracts.service.cancellation.TaskCancellationService;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.Tasks;

class EventHandlersTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");

    private final RequestStatusPublication statuses = mock();

    @Test
    void upsertedHandler_parsesBeforeAndWatchesAppliesAndReportsWhenTheWorkIsApplied() {
        ContractTaskParser parser = mock();
        IssuanceService issuance = mock();
        ContractTask task = Tasks.task(Tasks.insured("0.00"));
        IssuanceSnapshot before = new IssuanceSnapshot(REQUEST, false, List.of(), List.of(), List.of(), List.of());
        given(parser.parse("body")).willReturn(task);
        given(statuses.watch(REQUEST)).willReturn(before);
        List<RequestParticipant> composition = List.of(new RequestParticipant(Tasks.INSURED, DocumentKind.NONE));
        given(issuance.apply(task, NOW)).willReturn(composition);
        ContractTaskUpsertedHandler handler = new ContractTaskUpsertedHandler(parser, issuance, statuses);

        InboxWork work = handler.prepare("body");
        verifyNoInteractions(statuses, issuance);
        work.apply(NOW);

        assertThat(handler.eventType()).isEqualTo("ContractTaskUpserted");
        assertThat(handler.ordering()).isEqualTo(InboxOrdering.BY_REQUEST_VERSION);
        InOrder order = inOrder(statuses, issuance);
        order.verify(statuses).watch(REQUEST);
        order.verify(issuance).apply(task, NOW);
        order.verify(statuses).afterTask(before, REQUEST, composition, NOW);
    }

    @Test
    void cancelledHandler_parsesBeforeAndAppliesTheCancellationAndReportsWhenTheWorkIsApplied() {
        TaskCancellationParser parser = mock();
        TaskCancellationService cancellation = mock();
        ContractTaskCancellation parsed = new ContractTaskCancellation(REQUEST, NOW, "EXPIRED", null);
        given(parser.parse("body")).willReturn(parsed);
        ContractTaskCancelledHandler handler = new ContractTaskCancelledHandler(parser, cancellation, statuses);

        InboxWork work = handler.prepare("body");
        verifyNoInteractions(cancellation, statuses);
        work.apply(NOW);

        assertThat(handler.eventType()).isEqualTo("ContractTaskCancelled");
        assertThat(handler.ordering()).isEqualTo(InboxOrdering.BY_REQUEST_VERSION);
        InOrder order = inOrder(cancellation, statuses);
        order.verify(cancellation).apply(parsed, NOW);
        order.verify(statuses).afterCancellation(REQUEST, NOW);
    }
}
