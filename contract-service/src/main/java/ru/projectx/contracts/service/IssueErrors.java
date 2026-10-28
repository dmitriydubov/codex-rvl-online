package ru.projectx.contracts.service;

import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.exception.ParticipantDataException;

public final class IssueErrors {

    static final String PROGRAM_REQUIRED = "PROGRAM_REQUIRED";
    static final String LM_POLICY_REQUEST_UNDELIVERED = "LM_POLICY_REQUEST_UNDELIVERED";
    static final String KM_CONTRACT_SUBMISSION_UNDELIVERED = "KM_CONTRACT_SUBMISSION_UNDELIVERED";
    static final String KM_CONTRACT_SUBMISSION_PREPARATION_FAILED = "KM_CONTRACT_SUBMISSION_PREPARATION_FAILED";
    static final String LM_POLICY_REQUEST_PREPARATION_FAILED = "LM_POLICY_REQUEST_PREPARATION_FAILED";
    static final String INTERNAL_FAILURE = ": внутренняя ошибка сервиса Договоров";

    private IssueErrors() {
    }

    public static ParticipantDataException programRequired(String participantPath) {
        return new ParticipantDataException(PROGRAM_REQUIRED,
                "поле " + participantPath + ".program_id обязательно для участника, которому выпускается документ");
    }

    public static IssueError programMissing() {
        return new IssueError(PROGRAM_REQUIRED, "у записи о выпуске нет программы страхования");
    }

    public static IssuanceErrors kmContractSubmission() {
        return new IssuanceErrors(kmContractSubmissionUndelivered(), kmContractSubmissionPreparationFailed());
    }

    public static IssuanceErrors lmPolicyRequest() {
        return new IssuanceErrors(lmPolicyRequestUndelivered(), lmPolicyRequestPreparationFailed());
    }

    public static IssueError lmPolicyRequestUndelivered() {
        return new IssueError(LM_POLICY_REQUEST_UNDELIVERED, "запрос на выпуск полиса ЛМ не доставлен в 1С:УСК");
    }

    public static IssueError kmContractSubmissionUndelivered() {
        return new IssueError(KM_CONTRACT_SUBMISSION_UNDELIVERED, "договор КМ не передан в 1С:УСК");
    }

    public static IssueError kmContractSubmissionPreparationFailed() {
        return new IssueError(KM_CONTRACT_SUBMISSION_PREPARATION_FAILED,
                "передача договора КМ в 1С:УСК не подготовлена" + INTERNAL_FAILURE);
    }

    public static IssueError lmPolicyRequestPreparationFailed() {
        return new IssueError(LM_POLICY_REQUEST_PREPARATION_FAILED,
                "запрос на выпуск полиса ЛМ в 1С:УСК не подготовлен" + INTERNAL_FAILURE);
    }
}
