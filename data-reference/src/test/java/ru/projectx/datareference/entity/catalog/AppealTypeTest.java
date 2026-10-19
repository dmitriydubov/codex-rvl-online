package ru.projectx.datareference.entity.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppealTypeTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-10-02T10:30:00Z");
    private static final UUID EXTERNAL_ID = UUID.fromString("1c000000-0000-4000-8000-000000000001");
    private static final UUID PARENT = UUID.fromString("1c000000-0000-4000-8000-000000000002");

    @Test
    void create_keepsEveryFieldOfTheMessageAndStampsCreation() {
        var details = new AppealType.Details(PARENT, "АМБ", "Амбулаторная помощь", "Лечение", "SYS-1", 2,
                true, false, false);

        AppealType appealType = AppealType.create(EXTERNAL_ID, details, CREATED);

        assertThat(appealType.externalId()).isEqualTo(EXTERNAL_ID);
        assertThat(appealType.id()).isNotNull().isNotEqualTo(EXTERNAL_ID);
        assertThat(appealType.externalParentId()).isEqualTo(PARENT);
        assertThat(appealType.externalCode()).isEqualTo("АМБ");
        assertThat(appealType.name()).isEqualTo("Амбулаторная помощь");
        assertThat(appealType.category()).isEqualTo("Лечение");
        assertThat(appealType.systemCode()).isEqualTo("SYS-1");
        assertThat(appealType.hierarchyLevel()).isEqualTo(2);
        assertThat(appealType.directAttachment()).isTrue();
        assertThat(appealType.nonDirectAttachment()).isFalse();
        assertThat(appealType.isArchive()).isFalse();
        assertThat(appealType.createdAt()).isEqualTo(CREATED);
        assertThat(appealType.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void create_withDeleteMark_isArchiveFromTheStart() {
        var details = new AppealType.Details(null, null, "Стационар", null, null, null, false, true, true);

        AppealType appealType = AppealType.create(EXTERNAL_ID, details, CREATED);

        assertThat(appealType.isArchive()).isTrue();
        assertThat(appealType.nonDirectAttachment()).isTrue();
        assertThat(appealType.directAttachment()).isFalse();
    }

    @Test
    void applySnapshot_replacesEveryFieldAndKeepsIdentityAndCreation() {
        AppealType appealType = AppealType.create(EXTERNAL_ID, new AppealType.Details(PARENT, "АМБ",
                "Амбулаторная помощь", "Лечение", "SYS-1", 2, true, false, false), CREATED);
        UUID id = appealType.id();

        appealType.applySnapshot(new AppealType.Details(null, null, "Стационар", null, null, null,
                false, true, true), UPDATED);

        assertThat(appealType.id()).isEqualTo(id);
        assertThat(appealType.externalParentId()).isNull();
        assertThat(appealType.externalCode()).isNull();
        assertThat(appealType.name()).isEqualTo("Стационар");
        assertThat(appealType.category()).isNull();
        assertThat(appealType.systemCode()).isNull();
        assertThat(appealType.hierarchyLevel()).isNull();
        assertThat(appealType.directAttachment()).isFalse();
        assertThat(appealType.nonDirectAttachment()).isTrue();
        assertThat(appealType.isArchive()).isTrue();
        assertThat(appealType.createdAt()).isEqualTo(CREATED);
        assertThat(appealType.updatedAt()).isEqualTo(UPDATED);
    }
}
