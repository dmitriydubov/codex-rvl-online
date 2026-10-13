package ru.projectx.contracts.service.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.dto.ReferencePolicy;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.exception.ReferenceArchivedException;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.exception.ReferenceNotFoundException;
import ru.projectx.contracts.exception.ReferenceRejectedException;
import ru.projectx.contracts.exception.ReferenceUnavailableException;

class ReferenceReadTest {

    private static final UUID ID = UUID.fromString("a23ba061-f742-4b20-8b95-36a0e525c087");
    private static final UUID EXTERNAL_ID = UUID.fromString("1d9a6e77-4c02-4b88-a15e-30f7b2c9de41");

    @Test
    void liveRecord_yieldsItsExternalId() {
        ReferenceOutcome outcome = ReferenceRead.read(ReferenceKind.POLICY, ID,
                id -> new ReferencePolicy(id, EXTERNAL_ID, "СТЕНД-0001", null, null, false));

        assertThat(outcome).isEqualTo(new ReferenceOutcome.Found(EXTERNAL_ID));
    }

    @Test
    void archivedRecord_isRefusedWithArchivedCodeNamingTheRecord() {
        ReferenceOutcome outcome = ReferenceRead.read(ReferenceKind.PROGRAM, ID, id -> {
            throw new ReferenceArchivedException(ReferenceKind.PROGRAM, id);
        });

        assertThat(outcome).isEqualTo(new ReferenceOutcome.Refused(new IssueError("PROGRAM_ARCHIVED",
                "программа страхования " + ID + " в архиве справочного сервиса")));
    }

    @Test
    void missingRecord_isRefusedWithTheReferenceNotFoundCode() {
        ReferenceOutcome outcome = ReferenceRead.read(ReferenceKind.INSURANCE_CONTRACT, ID, id -> {
            throw new ReferenceNotFoundException(ReferenceKind.INSURANCE_CONTRACT);
        });

        assertThat(outcome).isEqualTo(new ReferenceOutcome.Refused(new IssueError("INSURANCE_CONTRACT_NOT_FOUND",
                "договора страхования " + ID + " нет у справочного сервиса")));
    }

    @Test
    void rejectedRequest_isRefusedWithTheReferenceCode() {
        ReferenceOutcome outcome = ReferenceRead.read(ReferenceKind.POLICY, ID, id -> {
            throw new ReferenceRejectedException(400, "VALIDATION_ERROR");
        });

        assertThat(outcome).isEqualTo(new ReferenceOutcome.Refused(new IssueError("VALIDATION_ERROR",
                "справочный сервис отклонил запрос: полиса страхователя " + ID)));
    }

    @Test
    void unavailableReference_isReportedWithItsKind() {
        ReferenceOutcome outcome = ReferenceRead.read(ReferenceKind.POLICY, ID, id -> {
            throw new ReferenceUnavailableException(ReferenceUnavailableException.Reason.TIMEOUT);
        });

        assertThat(outcome).isEqualTo(new ReferenceOutcome.Unavailable(ReferenceKind.POLICY));
    }

    @Test
    void otherFailures_propagate() {
        assertThatThrownBy(() -> ReferenceRead.read(ReferenceKind.POLICY, ID, id -> {
            throw new IllegalStateException("сбой");
        })).isInstanceOf(IllegalStateException.class);
    }
}
