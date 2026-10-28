package ru.projectx.contracts.message.task;

import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record TaskParticipant(
        int index,
        UUID id,
        boolean relative,
        BigDecimal amountDue,
        @Nullable UUID programId,
        ParticipantPerson person) {

    public boolean pays() {
        return amountDue.signum() > 0;
    }

    public String path() {
        return "participants[" + index + "]";
    }
}
