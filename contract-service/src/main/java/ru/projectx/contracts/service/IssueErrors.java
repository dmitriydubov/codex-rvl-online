package ru.projectx.contracts.service;

import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.exception.ParticipantDataException;

public final class IssueErrors {

    static final String PROGRAM_REQUIRED = "PROGRAM_REQUIRED";

    private IssueErrors() {
    }

    static ParticipantDataException programRequired(String participantPath) {
        return new ParticipantDataException(PROGRAM_REQUIRED,
                "поле " + participantPath + ".program_id обязательно для участника, которому выпускается документ");
    }

    public static IssueError programMissing() {
        return new IssueError(PROGRAM_REQUIRED, "у записи о выпуске нет программы страхования");
    }
}
