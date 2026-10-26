package ru.projectx.contracts.service;

import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.exception.ParticipantDataException;

public final class IssueErrors {

    static final String PROGRAM_REQUIRED = "PROGRAM_REQUIRED";
    static final String LM_POLICY_REQUEST_UNDELIVERED = "LM_POLICY_REQUEST_UNDELIVERED";
    static final String KM_CONTRACT_SUBMISSION_UNDELIVERED = "KM_CONTRACT_SUBMISSION_UNDELIVERED";
    static final String KM_CONTRACT_MESSAGE_TOO_LARGE = "KM_CONTRACT_MESSAGE_TOO_LARGE";

    private IssueErrors() {
    }

    static ParticipantDataException programRequired(String participantPath) {
        return new ParticipantDataException(PROGRAM_REQUIRED,
                "поле " + participantPath + ".program_id обязательно для участника, которому выпускается документ");
    }

    public static IssueError programMissing() {
        return new IssueError(PROGRAM_REQUIRED, "у записи о выпуске нет программы страхования");
    }

    public static IssueError lmPolicyRequestUndelivered() {
        return new IssueError(LM_POLICY_REQUEST_UNDELIVERED, "запрос на выпуск полиса ЛМ не доставлен в 1С:УСК");
    }

    public static IssueError kmContractSubmissionUndelivered() {
        return new IssueError(KM_CONTRACT_SUBMISSION_UNDELIVERED, "договор КМ не передан в 1С:УСК");
    }

    public static IssueError kmContractMessageTooLarge(long limit, int certificates) {
        return new IssueError(KM_CONTRACT_MESSAGE_TOO_LARGE,
                "сообщение о договоре КМ больше предела " + limit + " байт, справок — " + certificates);
    }
}
