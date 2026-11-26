package ru.projectx.contracts.service.storage;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.dto.AttachmentReceipt;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.StorageUnavailableException;

@Service
public class AttachmentReception implements AttachmentUploads {

    private final AttachmentFiles files;
    private final AttachmentRegistry registry;
    private final ObjectKeys keys;
    private final CertificateStore store;
    private final AttachmentMetrics metrics;

    public AttachmentReception(AttachmentFiles files, AttachmentRegistry registry, ObjectKeys keys,
            CertificateStore store, AttachmentMetrics metrics) {
        this.files = files;
        this.registry = registry;
        this.keys = keys;
        this.store = store;
        this.metrics = metrics;
    }

    @Override
    public AttachmentReceipt receive(AttachmentUpload upload) {
        registry.ensureOpenFor(upload);
        AttachmentContent file = upload.file();
        AttachmentFiles.AcceptedFile accepted = files.accept(file);
        String fileHash = ContentDigests.sha256(file.source());
        return registry.accepted(upload, fileHash).orElseGet(() -> stored(upload, new StoredFile(
                keys.next(upload.owner().attachmentId(), accepted.extension()), fileHash, file.size(), accepted.contentType())));
    }

    private AttachmentReceipt stored(AttachmentUpload upload, StoredFile file) {
        try {
            store.store(file, upload.file().source());
        } catch (StorageUnavailableException unavailable) {
            metrics.countStorageUnavailable();
            throw unavailable;
        }
        return registered(upload, file);
    }

    private AttachmentReceipt registered(AttachmentUpload upload, StoredFile file) {
        try {
            AttachmentReceipt response = registry.register(upload, file);
            metrics.countStored();
            return response;
        } catch (DataIntegrityViolationException concurrentUpload) {
            return registry.accepted(upload, file.hash()).orElseThrow(() -> concurrentUpload);
        }
    }
}
