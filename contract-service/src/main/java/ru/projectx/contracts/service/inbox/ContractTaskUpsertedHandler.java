package ru.projectx.contracts.service.inbox;

import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.message.TaskEvents;
import ru.projectx.contracts.service.IssuanceService;

@Component
public class ContractTaskUpsertedHandler implements InboxEventHandler {

    private final ContractTaskParser parser;
    private final IssuanceService issuance;

    public ContractTaskUpsertedHandler(ContractTaskParser parser, IssuanceService issuance) {
        this.parser = parser;
        this.issuance = issuance;
    }

    @Override
    public String eventType() {
        return TaskEvents.CONTRACT_TASK_UPSERTED;
    }

    @Override
    public void handle(String payload, Instant now) {
        issuance.apply(parser.parse(payload), now);
    }
}
