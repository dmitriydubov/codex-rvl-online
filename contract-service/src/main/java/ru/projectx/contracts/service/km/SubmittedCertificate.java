package ru.projectx.contracts.service.km;

import java.util.UUID;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.StoredFile;

public record SubmittedCertificate(UUID documentId, PrintedFormType docType, StoredFile file) {
}
