package ru.projectx.contracts.service.inbox;

import org.springframework.stereotype.Component;
import ru.projectx.contracts.message.ContractTaskCancellation;
import ru.projectx.contracts.message.TaskEvents;
import ru.projectx.contracts.service.TaskCancellationService;
import ru.projectx.contracts.service.status.RequestStatusPublication;

@Component
public class ContractTaskCancelledHandler extends ParsedEventHandler<ContractTaskCancellation> {

    private final TaskCancellationService cancellation;
    private final RequestStatusPublication statuses;

    public ContractTaskCancelledHandler(TaskCancellationParser parser, TaskCancellationService cancellation,
            RequestStatusPublication statuses) {
        super(parser);
        this.cancellation = cancellation;
        this.statuses = statuses;
    }

    @Override
    public String eventType() {
        return TaskEvents.CONTRACT_TASK_CANCELLED;
    }

    @Override
    protected InboxWork work(ContractTaskCancellation cancelled) {
        return now -> {
            cancellation.apply(cancelled, now);
            statuses.afterCancellation(cancelled.individualRequestId(), now);
        };
    }
}
