package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LpuTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-10-02T10:30:00Z");
    private static final UUID EXTERNAL_ID = UUID.fromString("1c000000-0000-4000-8000-000000000021");
    private static final Lpu.Details DETAILS = new Lpu.Details("Санаторий «Берёзка»", "ООО «Санаторий Берёзка»",
            "7701234567", "770101001", "+7 495 000-00-00", "info@example.org", false);
    private static final Lpu.Details NEW_DETAILS = new Lpu.Details("Берёзка", null, null, null, null, null, false);

    @Test
    void create_keepsEveryRequisiteAndLeavesContactsEmpty() {
        Lpu lpu = Lpu.create(EXTERNAL_ID, DETAILS, CREATED);

        assertThat(lpu.externalId()).isEqualTo(EXTERNAL_ID);
        assertThat(lpu.id()).isNotNull().isNotEqualTo(EXTERNAL_ID);
        assertThat(lpu.name()).isEqualTo("Санаторий «Берёзка»");
        assertThat(lpu.fullName()).isEqualTo("ООО «Санаторий Берёзка»");
        assertThat(lpu.inn()).isEqualTo("7701234567");
        assertThat(lpu.kpp()).isEqualTo("770101001");
        assertThat(lpu.phone()).isEqualTo("+7 495 000-00-00");
        assertThat(lpu.email()).isEqualTo("info@example.org");
        assertThat(lpu.address()).isNull();
        assertThat(lpu.region()).isNull();
        assertThat(lpu.isArchive()).isFalse();
        assertThat(lpu.createdAt()).isEqualTo(CREATED);
        assertThat(lpu.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void create_withDeleteMark_isArchiveFromTheStart() {
        Lpu lpu = Lpu.create(EXTERNAL_ID, new Lpu.Details("Берёзка", null, null, null, null, null, true), CREATED);

        assertThat(lpu.isArchive()).isTrue();
    }

    @Test
    void applySnapshot_replacesRequisitesAndKeepsContacts() {
        Lpu lpu = Lpu.create(EXTERNAL_ID, DETAILS, CREATED);
        UUID id = lpu.id();
        lpu.applyContacts("Москва, ул. Лесная, 1", "Москва", CREATED);

        lpu.applySnapshot(NEW_DETAILS, UPDATED);

        assertThat(lpu.id()).isEqualTo(id);
        assertThat(lpu.name()).isEqualTo("Берёзка");
        assertThat(lpu.fullName()).isNull();
        assertThat(lpu.inn()).isNull();
        assertThat(lpu.kpp()).isNull();
        assertThat(lpu.phone()).isNull();
        assertThat(lpu.email()).isNull();
        assertThat(lpu.address()).isEqualTo("Москва, ул. Лесная, 1");
        assertThat(lpu.region()).isEqualTo("Москва");
        assertThat(lpu.createdAt()).isEqualTo(CREATED);
        assertThat(lpu.updatedAt()).isEqualTo(UPDATED);
    }

    @Test
    void applySnapshot_withoutDeleteMark_returnsSanatoriumFromArchive() {
        Lpu lpu = Lpu.create(EXTERNAL_ID, new Lpu.Details("Берёзка", null, null, null, null, null, true), CREATED);

        lpu.applySnapshot(NEW_DETAILS, UPDATED);

        assertThat(lpu.isArchive()).isFalse();
    }

    @Test
    void applyContacts_replacesAddressAndRegionAndKeepsRequisites() {
        Lpu lpu = Lpu.create(EXTERNAL_ID, DETAILS, CREATED);
        lpu.applyContacts("Москва, ул. Лесная, 1", "Москва", CREATED);

        lpu.applyContacts(null, null, UPDATED);

        assertThat(lpu.address()).isNull();
        assertThat(lpu.region()).isNull();
        assertThat(lpu.name()).isEqualTo("Санаторий «Берёзка»");
        assertThat(lpu.inn()).isEqualTo("7701234567");
        assertThat(lpu.updatedAt()).isEqualTo(UPDATED);
    }

    @Test
    void applyContacts_keepsArchivedSanatoriumArchived() {
        Lpu lpu = Lpu.create(EXTERNAL_ID, new Lpu.Details("Берёзка", null, null, null, null, null, true), CREATED);

        lpu.applyContacts("Москва, ул. Лесная, 1", "Москва", UPDATED);

        assertThat(lpu.isArchive()).isTrue();
        assertThat(lpu.address()).isEqualTo("Москва, ул. Лесная, 1");
    }

    @Test
    void applyContacts_keepsActiveSanatoriumActive() {
        Lpu lpu = Lpu.create(EXTERNAL_ID, DETAILS, CREATED);

        lpu.applyContacts("Москва, ул. Лесная, 1", "Москва", UPDATED);

        assertThat(lpu.isArchive()).isFalse();
    }

    @Test
    void archiveAfterLosingRvl_archivesAndStillAppliesFreshRequisites() {
        Lpu lpu = Lpu.create(EXTERNAL_ID, DETAILS, CREATED);

        lpu.archiveAfterLosingRvl(NEW_DETAILS, UPDATED);

        assertThat(lpu.isArchive()).isTrue();
        assertThat(lpu.name()).isEqualTo("Берёзка");
        assertThat(lpu.inn()).isNull();
        assertThat(lpu.updatedAt()).isEqualTo(UPDATED);
    }
}
