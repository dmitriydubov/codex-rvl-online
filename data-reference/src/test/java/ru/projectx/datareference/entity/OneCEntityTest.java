package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OneCEntityTest {

    private static final UUID EXTERNAL_ID = UUID.fromString("0b0c1d2e-3f40-4a51-8b62-000000000001");
    private static final Instant CREATED = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-10-02T09:00:00Z");

    @Test
    void newRecord_keepsExternalId_isActive_andIsCreatedAndUpdatedAtTheSameTime() {
        OneCEntity record = record(CREATED);

        assertThat(record.externalId()).isEqualTo(EXTERNAL_ID);
        assertThat(record.isArchive()).isFalse();
        assertThat(record.createdAt()).isEqualTo(CREATED);
        assertThat(record.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void newRecords_getTheirOwnIds_independentOfExternalId() {
        OneCEntity first = record(CREATED);
        OneCEntity second = record(CREATED);

        assertThat(first.id()).isNotNull().isNotEqualTo(second.id()).isNotEqualTo(EXTERNAL_ID);
    }

    @Test
    void applyCommon_withDeleteMark_archives_andMarksOnlyUpdateTime() {
        OneCEntity record = record(CREATED);

        record.applyCommon(true, UPDATED);

        assertThat(record.isArchive()).isTrue();
        assertThat(record.updatedAt()).isEqualTo(UPDATED);
        assertThat(record.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void applyCommon_withoutDeleteMark_returnsArchivedRecordToActive() {
        OneCEntity record = record(CREATED);
        record.applyCommon(true, CREATED);

        record.applyCommon(false, UPDATED);

        assertThat(record.isArchive()).isFalse();
    }

    @Test
    void archiveCommon_archivesActiveRecord_andMarksUpdateTime() {
        OneCEntity record = record(CREATED);

        record.archiveCommon(UPDATED);

        assertThat(record.isArchive()).isTrue();
        assertThat(record.updatedAt()).isEqualTo(UPDATED);
    }

    private static OneCEntity record(Instant now) {
        return new OneCEntity(EXTERNAL_ID, now) {
        };
    }
}
