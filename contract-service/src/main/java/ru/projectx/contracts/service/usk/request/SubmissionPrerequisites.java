package ru.projectx.contracts.service.usk.request;

import ru.projectx.contracts.entity.issuance.IssuanceRecord;

@FunctionalInterface
public interface SubmissionPrerequisites<R extends IssuanceRecord> {

    Prerequisites check(R record);

    static <R extends IssuanceRecord> SubmissionPrerequisites<R> none() {
        return record -> new Prerequisites.Met();
    }
}
