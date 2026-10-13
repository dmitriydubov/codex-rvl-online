package ru.projectx.contracts.service.reference;

import java.util.UUID;
import java.util.function.Function;
import ru.projectx.contracts.dto.ReferenceRecord;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.exception.ReferenceUnavailableException;

public final class ReferenceRead {

    private ReferenceRead() {
    }

    public static <T extends ReferenceRecord> ReferenceOutcome read(ReferenceKind kind, UUID id, Function<UUID, T> reader) {
        try {
            return new ReferenceOutcome.Found(reader.apply(id).externalId());
        }
        catch (ReferenceUnavailableException unavailable) {
            return new ReferenceOutcome.Unavailable(kind);
        }
        catch (RuntimeException refusal) {
            return new ReferenceOutcome.Refused(ReferenceSubjects.refusal(kind, id, refusal));
        }
    }
}
