package ru.projectx.contracts.service.storage;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentNotFoundException;
import ru.projectx.contracts.exception.CertificateMissingException;
import ru.projectx.contracts.exception.StorageUnavailableException;

@Service
public class AttachmentDownloads implements AttachmentContentReads {

    private static final Logger log = LoggerFactory.getLogger(AttachmentDownloads.class);

    private final AttachmentRegistry registry;
    private final AttachmentObjectStore objects;
    private final AttachmentMetrics metrics;

    public AttachmentDownloads(AttachmentRegistry registry, AttachmentObjectStore objects, AttachmentMetrics metrics) {
        this.registry = registry;
        this.objects = objects;
        this.metrics = metrics;
    }

    @Override
    public AttachmentContent open(UUID attachmentId) {
        StoredFile file = registry.active(attachmentId).flatMap(PrintedForm::storedFile)
                .orElseThrow(AttachmentNotFoundException::new);
        try {
            return new AttachmentContent(objects.open(file.key()), file.contentType(), file.size(), file.hash());
        } catch (CertificateMissingException missing) {
            throw missingObject(attachmentId, missing);
        } catch (StorageUnavailableException unavailable) {
            metrics.countStorageUnavailable();
            throw unavailable;
        }
    }

    private static AttachmentNotFoundException missingObject(UUID attachmentId, CertificateMissingException missing) {
        log.warn("в хранилище отсутствует объект вложения {}", attachmentId);
        AttachmentNotFoundException failure = new AttachmentNotFoundException();
        failure.setStackTrace(missing.getStackTrace());
        return failure;
    }
}
