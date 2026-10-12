package ru.projectx.contracts.message;

import java.math.BigDecimal;
import java.util.UUID;

public record TaskParticipant(
        int index,
        UUID id,
        boolean relative,
        BigDecimal amountDue,
        ParticipantPerson person) {

    public boolean pays() {
        return amountDue.signum() > 0;
    }

    public String path() {
        return "participants[" + index + "]";
    }
}
