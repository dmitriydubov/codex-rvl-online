package ru.projectx.contracts.service;

import java.util.Optional;
import java.util.UUID;
import ru.projectx.contracts.entity.issuance.IssuanceTerms;
import ru.projectx.contracts.entity.issuance.RequestReference;
import ru.projectx.contracts.entity.issuance.TripPeriod;
import ru.projectx.contracts.message.task.ContractTask;
import ru.projectx.contracts.message.task.TaskParticipant;

public record Issue(ContractTask task, TaskParticipant participant) {

    public RequestReference request() {
        return new RequestReference(task.individualRequestId(), task.insuranceContractId(), task.insurerPolicyId());
    }

    public TripPeriod period() {
        return new TripPeriod(task.dateFrom(), task.dateTo());
    }

    public IssuanceTerms terms() {
        return new IssuanceTerms(request(), task.insured().id(), participant.programId(), period());
    }

    public UUID program() {
        return Optional.ofNullable(participant.programId())
                .orElseThrow(() -> IssueErrors.programRequired(participant.path()));
    }
}
