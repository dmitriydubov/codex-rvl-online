package ru.projectx.contracts.service.reference;

import ru.projectx.contracts.dto.ReferenceRecord;
import ru.projectx.contracts.exception.ReferenceArchivedException;
import ru.projectx.contracts.exception.ReferenceKind;

public final class LiveRecords {

    private LiveRecords() {
    }

    public static <T extends ReferenceRecord> T requireLive(ReferenceKind kind, T record) {
        if (record.isArchive()) {
            throw new ReferenceArchivedException(kind, record.id());
        }
        return record;
    }
}
