package ru.projectx.datareference.entity.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContragentServiceTest {

    private static final UUID EXTERNAL_ID = UUID.fromString("3e4f5a6b-7c8d-4e9f-a0b1-000000000001");
    private static final UUID APPEAL_TYPE_ID = UUID.fromString("3e4f5a6b-7c8d-4e9f-a0b1-000000000002");
    private static final Instant CREATED = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-10-02T09:00:00Z");

    @Test
    void create_takesServiceFieldsAndContractFromMessage() {
        LpuContract contract = contract();

        ContragentService service = ContragentService.create(EXTERNAL_ID, contract,
                new ContragentService.Details(APPEAL_TYPE_ID, "У-01", "Лечебный массаж", false), CREATED);

        assertThat(service.externalId()).isEqualTo(EXTERNAL_ID);
        assertThat(service.contract()).isSameAs(contract);
        assertThat(service.externalAppealTypeId()).isEqualTo(APPEAL_TYPE_ID);
        assertThat(service.code()).isEqualTo("У-01");
        assertThat(service.name()).isEqualTo("Лечебный массаж");
        assertThat(service.isArchive()).isFalse();
    }

    @Test
    void create_withDeleteMark_isArchivedFromTheStart() {
        ContragentService service = ContragentService.create(EXTERNAL_ID, contract(),
                new ContragentService.Details(null, null, "Лечебный массаж", true), CREATED);

        assertThat(service.isArchive()).isTrue();
    }

    @Test
    void applySnapshot_movesServiceToNewContract_replacesFields_andMarksUpdateTime() {
        ContragentService service = ContragentService.create(EXTERNAL_ID, contract(),
                new ContragentService.Details(APPEAL_TYPE_ID, "У-01", "Лечебный массаж", false), CREATED);
        LpuContract newContract = contract();

        service.applySnapshot(newContract, new ContragentService.Details(null, null, "Грязелечение", true), UPDATED);

        assertThat(service.contract()).isSameAs(newContract);
        assertThat(service.externalAppealTypeId()).isNull();
        assertThat(service.code()).isNull();
        assertThat(service.name()).isEqualTo("Грязелечение");
        assertThat(service.isArchive()).isTrue();
        assertThat(service.updatedAt()).isEqualTo(UPDATED);
        assertThat(service.createdAt()).isEqualTo(CREATED);
    }

    private static LpuContract contract() {
        Lpu lpu = Lpu.create(UUID.randomUUID(),
                new Lpu.Details("Санаторий", null, null, null, null, null, false), CREATED);
        return LpuContract.create(UUID.randomUUID(), lpu, new LpuContract.Details("Д-01", null, null, false, false,
                LocalDate.of(2026, 1, 1), null, false), CREATED);
    }
}
