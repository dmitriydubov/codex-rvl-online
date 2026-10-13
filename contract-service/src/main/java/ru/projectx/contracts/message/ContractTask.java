package ru.projectx.contracts.message;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ContractTask(
        int entityVersion,
        UUID individualRequestId,
        UUID insuranceContractId,
        UUID insurerPolicyId,
        LocalDate dateFrom,
        LocalDate dateTo,
        List<TaskParticipant> participants) {

    public TaskParticipant insured() {
        return participants.stream()
                .filter(participant -> !participant.relative())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("в составе поездки нет застрахованного лица"));
    }
}
