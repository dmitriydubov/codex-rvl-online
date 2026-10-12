package ru.projectx.contracts.service.inbox;

import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.message.TaskEvents;
import ru.projectx.contracts.service.TaskCancellationService;

@Component
public class ContractTaskCancelledHandler implements InboxEventHandler {

    private final TaskCancellationParser parser;
    private final TaskCancellationService cancellation;

    public ContractTaskCancelledHandler(TaskCancellationParser parser, TaskCancellationService cancellation) {
        this.parser = parser;
        this.cancellation = cancellation;
    }

    @Override
    public String eventType() {
        return TaskEvents.CONTRACT_TASK_CANCELLED;
    }

    @Override
    public void handle(String payload, Instant now) {
        cancellation.apply(parser.parse(payload), now);
    }
}
