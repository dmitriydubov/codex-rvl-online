package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Следующее сообщение договора с санаторием замещает поля целиком, включая санаторий: договор отделения может
 * перейти к другому учреждению. Архивность приходит только из пометки удаления в 1С.
 */
class LpuContractTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-10-02T10:30:00Z");
    private static final UUID EXTERNAL_ID = UUID.fromString("1c000000-0000-4000-8000-000000000031");
    private static final UUID MAIN_CONTRACT = UUID.fromString("1c000000-0000-4000-8000-000000000032");
    private static final LpuContract.Details DETAILS = new LpuContract.Details("Д-17", "Договор на лечение",
            MAIN_CONTRACT, true, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), false);

    @Test
    void create_keepsEveryFieldOfTheMessageAndTheSanatorium() {
        Lpu lpu = lpu("Берёзка");

        LpuContract contract = LpuContract.create(EXTERNAL_ID, lpu, DETAILS, CREATED);

        assertThat(contract.externalId()).isEqualTo(EXTERNAL_ID);
        assertThat(contract.id()).isNotNull().isNotEqualTo(EXTERNAL_ID);
        assertThat(contract.lpu()).isSameAs(lpu);
        assertThat(contract.number()).isEqualTo("Д-17");
        assertThat(contract.name()).isEqualTo("Договор на лечение");
        assertThat(contract.mainContractExternalId()).isEqualTo(MAIN_CONTRACT);
        assertThat(contract.usePricesMainContract()).isTrue();
        assertThat(contract.uploadedToSob()).isTrue();
        assertThat(contract.beginDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(contract.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(contract.isArchive()).isFalse();
        assertThat(contract.createdAt()).isEqualTo(CREATED);
        assertThat(contract.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void create_withDeleteMark_isArchiveFromTheStart() {
        var details = new LpuContract.Details(null, null, null, false, false, LocalDate.of(2026, 1, 1), null, true);

        assertThat(LpuContract.create(EXTERNAL_ID, lpu("Берёзка"), details, CREATED).isArchive()).isTrue();
    }

    @Test
    void applySnapshot_replacesEveryFieldAndTheSanatorium() {
        LpuContract contract = LpuContract.create(EXTERNAL_ID, lpu("Берёзка"), DETAILS, CREATED);
        UUID id = contract.id();
        Lpu branch = lpu("Берёзка, отделение");

        contract.applySnapshot(branch, new LpuContract.Details(null, null, null, false, false,
                LocalDate.of(2027, 1, 1), null, true), UPDATED);

        assertThat(contract.id()).isEqualTo(id);
        assertThat(contract.lpu()).isSameAs(branch);
        assertThat(contract.number()).isNull();
        assertThat(contract.name()).isNull();
        assertThat(contract.mainContractExternalId()).isNull();
        assertThat(contract.usePricesMainContract()).isFalse();
        assertThat(contract.uploadedToSob()).isFalse();
        assertThat(contract.beginDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(contract.endDate()).isNull();
        assertThat(contract.isArchive()).isTrue();
        assertThat(contract.createdAt()).isEqualTo(CREATED);
        assertThat(contract.updatedAt()).isEqualTo(UPDATED);
    }

    private static Lpu lpu(String name) {
        return Lpu.create(UUID.randomUUID(), new Lpu.Details(name, null, null, null, null, null, false), CREATED);
    }
}
