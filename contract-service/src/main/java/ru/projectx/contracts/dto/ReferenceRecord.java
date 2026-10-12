package ru.projectx.contracts.dto;

import java.util.UUID;

public interface ReferenceRecord {

    UUID id();

    UUID externalId();

    boolean isArchive();
}
