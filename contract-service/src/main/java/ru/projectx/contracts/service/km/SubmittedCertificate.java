package ru.projectx.contracts.service.km;

import java.util.UUID;
import ru.projectx.contracts.entity.PrintedFormType;
import ru.projectx.contracts.entity.StoredFile;

public record SubmittedCertificate(UUID documentId, PrintedFormType docType, StoredFile file) {
}
