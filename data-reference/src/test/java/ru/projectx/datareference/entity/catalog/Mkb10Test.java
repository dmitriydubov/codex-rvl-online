package ru.projectx.datareference.entity.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Mkb10Test {

    private static final Instant CREATED = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-10-02T10:30:00Z");
    private static final UUID EXTERNAL_ID = UUID.fromString("1c000000-0000-4000-8000-000000000011");
    private static final UUID PARENT = UUID.fromString("1c000000-0000-4000-8000-000000000012");

    @Test
    void create_keepsEveryFieldOfTheMessageAndStampsCreation() {
        var details = new Mkb10.Details(PARENT, "J45", "Астма", "SYS-J45", 3, false);

        Mkb10 code = Mkb10.create(EXTERNAL_ID, details, CREATED);

        assertThat(code.externalId()).isEqualTo(EXTERNAL_ID);
        assertThat(code.id()).isNotNull().isNotEqualTo(EXTERNAL_ID);
        assertThat(code.externalParentId()).isEqualTo(PARENT);
        assertThat(code.code()).isEqualTo("J45");
        assertThat(code.name()).isEqualTo("Астма");
        assertThat(code.systemCode()).isEqualTo("SYS-J45");
        assertThat(code.hierarchyLevel()).isEqualTo(3);
        assertThat(code.isArchive()).isFalse();
        assertThat(code.createdAt()).isEqualTo(CREATED);
        assertThat(code.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void create_withDeleteMark_isArchiveFromTheStart() {
        Mkb10 code = Mkb10.create(EXTERNAL_ID, new Mkb10.Details(null, "J45", "Астма", null, null, true), CREATED);

        assertThat(code.isArchive()).isTrue();
    }

    @Test
    void applySnapshot_replacesEveryFieldAndKeepsIdentityAndCreation() {
        Mkb10 code = Mkb10.create(EXTERNAL_ID, new Mkb10.Details(PARENT, "J45", "Астма", "SYS-J45", 3, false),
                CREATED);
        UUID id = code.id();

        code.applySnapshot(new Mkb10.Details(null, "J45.0", "Аллергическая астма", null, null, true), UPDATED);

        assertThat(code.id()).isEqualTo(id);
        assertThat(code.externalParentId()).isNull();
        assertThat(code.code()).isEqualTo("J45.0");
        assertThat(code.name()).isEqualTo("Аллергическая астма");
        assertThat(code.systemCode()).isNull();
        assertThat(code.hierarchyLevel()).isNull();
        assertThat(code.isArchive()).isTrue();
        assertThat(code.createdAt()).isEqualTo(CREATED);
        assertThat(code.updatedAt()).isEqualTo(UPDATED);
    }
}
