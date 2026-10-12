package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InsuranceContractTest {

    private static final UUID EXTERNAL_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-000000000001");
    private static final UUID INSURER_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-000000000002");
    private static final Instant CREATED = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-10-02T09:00:00Z");
    private static final LocalDate BEGIN = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Test
    void create_takesContractFieldsFromMessage_andKeepsExternalId() {
        InsuranceContract contract = InsuranceContract.create(EXTERNAL_ID,
                new InsuranceContract.Details("ДМС-0001", BEGIN, END, INSURER_ID, false), CREATED);

        assertThat(contract.externalId()).isEqualTo(EXTERNAL_ID);
        assertThat(contract.number()).isEqualTo("ДМС-0001");
        assertThat(contract.beginDate()).isEqualTo(BEGIN);
        assertThat(contract.endDate()).isEqualTo(END);
        assertThat(contract.externalInsurerId()).isEqualTo(INSURER_ID);
        assertThat(contract.isArchive()).isFalse();
        assertThat(contract.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void create_withNotActive_isArchivedFromTheStart() {
        InsuranceContract contract = InsuranceContract.create(EXTERNAL_ID,
                new InsuranceContract.Details("ДМС-0001", BEGIN, null, null, true), CREATED);

        assertThat(contract.isArchive()).isTrue();
    }

    @Test
    void applySnapshot_replacesFields_archivesByNotActive_andMarksUpdateTime() {
        InsuranceContract contract = InsuranceContract.create(EXTERNAL_ID,
                new InsuranceContract.Details("ДМС-0001", BEGIN, END, INSURER_ID, false), CREATED);
        LocalDate newBegin = LocalDate.of(2027, 1, 1);

        contract.applySnapshot(new InsuranceContract.Details("ДМС-0002", newBegin, null, null, true), UPDATED);

        assertThat(contract.number()).isEqualTo("ДМС-0002");
        assertThat(contract.beginDate()).isEqualTo(newBegin);
        assertThat(contract.endDate()).isNull();
        assertThat(contract.externalInsurerId()).isNull();
        assertThat(contract.isArchive()).isTrue();
        assertThat(contract.updatedAt()).isEqualTo(UPDATED);
        assertThat(contract.createdAt()).isEqualTo(CREATED);
    }

    @ParameterizedTest
    @CsvSource({
        "2025-12-31, false",
        "2026-01-01, true",
        "2026-12-31, true",
        "2027-01-01, false"})
    void isActiveOn_coversTheTermIncludingFirstAndLastDay(LocalDate day, boolean active) {
        InsuranceContract contract = InsuranceContract.create(EXTERNAL_ID,
                new InsuranceContract.Details("ДМС-0001", BEGIN, END, INSURER_ID, false), CREATED);

        assertThat(contract.isActiveOn(day)).isEqualTo(active);
    }

    @Test
    void isActiveOn_withOpenEndDate_lastsIndefinitelyFromBegin() {
        InsuranceContract contract = InsuranceContract.create(EXTERNAL_ID,
                new InsuranceContract.Details("ДМС-0001", BEGIN, null, INSURER_ID, false), CREATED);

        assertThat(contract.isActiveOn(LocalDate.of(2999, 12, 31))).isTrue();
        assertThat(contract.isActiveOn(BEGIN.minusDays(1))).isFalse();
    }

    @Test
    void isActiveOn_archivedContract_isNotActiveWithinItsTerm() {
        InsuranceContract contract = InsuranceContract.create(EXTERNAL_ID,
                new InsuranceContract.Details("ДМС-0001", BEGIN, END, INSURER_ID, true), CREATED);

        assertThat(contract.isActiveOn(BEGIN)).isFalse();
    }
}
