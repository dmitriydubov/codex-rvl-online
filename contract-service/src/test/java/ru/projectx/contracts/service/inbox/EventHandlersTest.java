package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.message.ContractTask;
import ru.projectx.contracts.message.ContractTaskCancellation;
import ru.projectx.contracts.service.IssuanceService;
import ru.projectx.contracts.service.TaskCancellationService;

class EventHandlersTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");

    @Test
    void upsertedHandler_parsesTaskAndAppliesIssuance() {
        ContractTaskParser parser = mock();
        IssuanceService issuance = mock();
        ContractTask task = mock();
        given(parser.parse("body")).willReturn(task);
        ContractTaskUpsertedHandler handler = new ContractTaskUpsertedHandler(parser, issuance);

        handler.handle("body", NOW);

        assertThat(handler.eventType()).isEqualTo("ContractTaskUpserted");
        verify(issuance).apply(task, NOW);
    }

    @Test
    void cancelledHandler_parsesCancellationAndAppliesIt() {
        TaskCancellationParser parser = mock();
        TaskCancellationService cancellation = mock();
        ContractTaskCancellation parsed = mock();
        given(parser.parse("body")).willReturn(parsed);
        ContractTaskCancelledHandler handler = new ContractTaskCancelledHandler(parser, cancellation);

        handler.handle("body", NOW);

        assertThat(handler.eventType()).isEqualTo("ContractTaskCancelled");
        verify(cancellation).apply(parsed, NOW);
    }
}
