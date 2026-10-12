package ru.projectx.datareference.service.integration;

enum IngestOutcome {

    STORED("stored"),

    DUPLICATE("duplicate"),

    QUARANTINED("quarantined"),

    UNIDENTIFIED("unidentified"),

    RETURNED("returned"),

    STORAGE_OUTAGE("storage_outage");

    private final String tagValue;

    IngestOutcome(String tagValue) {
        this.tagValue = tagValue;
    }

    String tagValue() {
        return tagValue;
    }
}
