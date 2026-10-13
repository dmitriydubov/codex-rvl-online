package ru.projectx.contracts.service.inbox;

import org.springframework.stereotype.Component;
import ru.projectx.contracts.message.ContractTask;
import ru.projectx.contracts.message.TaskEvents;
import ru.projectx.contracts.service.IssuanceService;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;

@Component
public class ContractTaskUpsertedHandler extends ParsedEventHandler<ContractTask> {

    private final IssuanceService issuance;
    private final RequestStatusPublication statuses;

    public ContractTaskUpsertedHandler(ContractTaskParser parser, IssuanceService issuance,
            RequestStatusPublication statuses) {
        super(parser);
        this.issuance = issuance;
        this.statuses = statuses;
    }

    @Override
    public String eventType() {
        return TaskEvents.CONTRACT_TASK_UPSERTED;
    }

    @Override
    protected InboxWork work(ContractTask task) {
        return now -> {
            IssuanceSnapshot before = statuses.watch(task.individualRequestId());
            issuance.apply(task, now);
            statuses.afterTask(before, task, now);
        };
    }
}
