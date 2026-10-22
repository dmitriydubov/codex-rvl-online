package ru.projectx.contracts.exception;

import java.util.UUID;

public class ParticipantNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ParticipantNotFoundException(UUID individualRequestId, UUID participantId) {
        super("участника " + participantId + " нет в составе заявки " + individualRequestId);
    }
}
