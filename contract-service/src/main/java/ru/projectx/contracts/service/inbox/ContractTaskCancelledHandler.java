package ru.projectx.contracts.service.inbox;

import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.message.ContractTaskCancellation;
import ru.projectx.contracts.message.TaskEvents;
import ru.projectx.contracts.service.TaskCancellationService;
import ru.projectx.contracts.service.status.RequestStatusPublication;

@Component
public class ContractTaskCancelledHandler implements InboxEventHandler {

    private final TaskCancellationParser parser;
    private final TaskCancellationService cancellation;
    private final RequestStatusPublication statuses;

    public ContractTaskCancelledHandler(TaskCancellationParser parser, TaskCancellationService cancellation,
            RequestStatusPublication statuses) {
        this.parser = parser;
        this.cancellation = cancellation;
        this.statuses = statuses;
    }

    @Override
    public String eventType() {
        return TaskEvents.CONTRACT_TASK_CANCELLED;
    }

    @Override
    public void handle(String payload, Instant now) {
        ContractTaskCancellation parsed = parser.parse(payload);
        cancellation.apply(parsed, now);
        statuses.afterCancellation(parsed.individualRequestId(), now);
    }
}
