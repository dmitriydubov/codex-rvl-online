package ru.projectx.contracts.service;

import java.util.Optional;
import java.util.UUID;
import ru.projectx.contracts.entity.IssuanceTerms;
import ru.projectx.contracts.entity.RequestReference;
import ru.projectx.contracts.entity.TripPeriod;
import ru.projectx.contracts.exception.ParticipantDataException;
import ru.projectx.contracts.message.ContractTask;
import ru.projectx.contracts.message.TaskParticipant;

public record Issue(ContractTask task, TaskParticipant participant) {

    static final String PROGRAM_REQUIRED = "PROGRAM_REQUIRED";

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
        return Optional.ofNullable(participant.programId()).orElseThrow(() -> new ParticipantDataException(
                PROGRAM_REQUIRED,
                "поле " + participant.path() + ".program_id обязательно для участника, которому выпускается документ"));
    }
}
