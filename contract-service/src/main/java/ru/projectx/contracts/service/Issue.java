package ru.projectx.contracts.service;

import ru.projectx.contracts.entity.RequestReference;
import ru.projectx.contracts.entity.TripPeriod;
import ru.projectx.contracts.message.ContractTask;
import ru.projectx.contracts.message.TaskParticipant;

public record Issue(ContractTask task, TaskParticipant participant) {

    public RequestReference request() {
        return new RequestReference(task.individualRequestId(), task.insuranceContractId(), task.insurerPolicyId());
    }

    public TripPeriod period() {
        return new TripPeriod(task.dateFrom(), task.dateTo());
    }
}
