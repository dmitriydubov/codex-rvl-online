package ru.projectx.contracts.service.outbox;

import java.util.UUID;
import ru.projectx.contracts.entity.PrintedFormType;
import ru.projectx.contracts.entity.StoredFile;

public record OutboundAttachment(UUID documentId, PrintedFormType docType, StoredFile file) {
}
