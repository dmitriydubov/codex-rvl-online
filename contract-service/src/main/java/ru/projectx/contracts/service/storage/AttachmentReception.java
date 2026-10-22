package ru.projectx.contracts.service.storage;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.entity.StoredFile;
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
    public AttachmentResponse receive(CertificateUpload upload) {
        AttachmentFiles.AcceptedFile accepted = files.accept(upload);
        String fileHash = ContentDigests.sha256(upload.content());
        return registry.accepted(upload, fileHash).orElseGet(() -> stored(upload, new StoredFile(
                keys.next(upload.attachmentId(), accepted.extension()), fileHash, upload.size(), accepted.contentType())));
    }

    private AttachmentResponse stored(CertificateUpload upload, StoredFile file) {
        try {
            store.store(file, upload.content());
        } catch (StorageUnavailableException unavailable) {
            metrics.countStorageUnavailable();
            throw unavailable;
        }
        return registered(upload, file);
    }

    private AttachmentResponse registered(CertificateUpload upload, StoredFile file) {
        try {
            AttachmentResponse response = registry.register(upload, file);
            metrics.countStored();
            return response;
        } catch (DataIntegrityViolationException concurrentUpload) {
            return registry.accepted(upload, file.hash()).orElseThrow(() -> concurrentUpload);
        }
    }
}
