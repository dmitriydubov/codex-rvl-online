package ru.projectx.contracts.service.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class ParticipantRecordsTest {

    private final Contract earlier = Issuances.contract("01960000-0000-7000-8000-000000000001", Tasks.SPOUSE,
            "DKM-2026-000001");
    private final Contract later = Issuances.contract("01960000-0000-7000-8000-000000000002", Tasks.SPOUSE,
            "DKM-2026-000002");
    private final Contract ofAnother = Issuances.contract("01960000-0000-7000-8000-000000000003", Tasks.INSURED,
            "DKM-2026-000003");

    @Test
    void latestRecordOfEachParticipant_isTheOneWithTheGreatestIdentifierWhateverTheOrderOfTheList() {
        Map<UUID, Contract> expected = Map.of(Tasks.SPOUSE, later, Tasks.INSURED, ofAnother);

        assertThat(ParticipantRecords.latestBy(List.of(later, ofAnother, earlier), IssuanceRecord::participantId,
                IssuanceRecord::id)).isEqualTo(expected);
        assertThat(ParticipantRecords.latestBy(List.of(earlier, later, ofAnother), IssuanceRecord::participantId,
                IssuanceRecord::id)).isEqualTo(expected);
    }

    @Test
    void recordOfAParticipant_isTakenFromTheMap() {
        assertThat(ParticipantRecords.latest(Map.of(Tasks.SPOUSE, later), Tasks.SPOUSE, "договора КМ")).isSameAs(later);
    }

    @Test
    void participantWithoutARecord_isAnIllegalStateNamingTheParticipantAndTheRecord() {
        assertThatThrownBy(() -> ParticipantRecords.latest(Map.of(Tasks.SPOUSE, later), Tasks.CHILD, "полиса ЛМ"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("у участника " + Tasks.CHILD + " нет полиса ЛМ");
    }
}
