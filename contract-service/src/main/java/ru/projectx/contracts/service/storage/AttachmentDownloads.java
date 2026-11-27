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
    private final AttachmentObjectReads objects;
    private final AttachmentMetrics metrics;

    public AttachmentDownloads(AttachmentRegistry registry, AttachmentObjectReads objects, AttachmentMetrics metrics) {
        this.registry = registry;
        this.objects = objects;
        this.metrics = metrics;
    }

    @Override
    public AttachmentContent open(UUID attachmentId) {
        StoredFile file = registry.active(attachmentId).flatMap(PrintedForm::storedFile)
                .orElseThrow(AttachmentNotFoundException::new);
        try {
            var stream = new AttachmentDownloadStream(objects.open(file.key()), metrics);
            return new AttachmentContent(stream, file.contentType(), file.size(), file.hash());
        } catch (CertificateMissingException missing) {
            throw missingObject(attachmentId, missing);
        } catch (StorageUnavailableException unavailable) {
            metrics.countStorageUnavailable();
            throw unavailable;
        }
    }

    private static AttachmentNotFoundException missingObject(UUID attachmentId, CertificateMissingException missing) {
        log.atWarn().addKeyValue("failure_type", missing.getClass().getSimpleName())
                .log("в хранилище отсутствует объект вложения {}", attachmentId);
        return new AttachmentNotFoundException();
    }
}
