package ru.projectx.contracts.service.inbox;

import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.message.ContractTask;
import ru.projectx.contracts.message.TaskEvents;
import ru.projectx.contracts.service.IssuanceService;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;

@Component
public class ContractTaskUpsertedHandler implements InboxEventHandler {

    private final ContractTaskParser parser;
    private final IssuanceService issuance;
    private final RequestStatusPublication statuses;

    public ContractTaskUpsertedHandler(ContractTaskParser parser, IssuanceService issuance,
            RequestStatusPublication statuses) {
        this.parser = parser;
        this.issuance = issuance;
        this.statuses = statuses;
    }

    @Override
    public String eventType() {
        return TaskEvents.CONTRACT_TASK_UPSERTED;
    }

    @Override
    public void handle(String payload, Instant now) {
        ContractTask task = parser.parse(payload);
        IssuanceSnapshot before = statuses.watch(task.individualRequestId());
        issuance.apply(task, now);
        statuses.afterTask(before, task, now);
    }
}
