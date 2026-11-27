package ru.projectx.contracts.service.storage;

import java.util.UUID;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.exception.AttachmentBusyException;
import ru.projectx.contracts.exception.AttachmentNotFoundException;
import ru.projectx.contracts.exception.StorageUnavailableException;

@Service
public class AttachmentDeletion implements AttachmentDeletes {

    private final AttachmentDeletionTransactions transactions;
    private final AttachmentObjectStore objects;
    private final AttachmentMetrics metrics;

    public AttachmentDeletion(AttachmentDeletionTransactions transactions, AttachmentObjectStore objects,
            AttachmentMetrics metrics) {
        this.transactions = transactions;
        this.objects = objects;
        this.metrics = metrics;
    }

    @Override
    public void delete(UUID attachmentId) {
        try {
            transactions.prepare(attachmentId).ifPresent(attachment -> remove(attachmentId, attachment));
        } catch (PessimisticLockingFailureException busy) {
            throw new AttachmentBusyException(busy);
        }
    }

    private void remove(UUID attachmentId, PrintedForm attachment) {
        String key = attachment.storedFile().orElseThrow(AttachmentNotFoundException::new).key();
        try {
            objects.remove(key);
        } catch (StorageUnavailableException unavailable) {
            metrics.countStorageUnavailable();
            throw unavailable;
        }
        transactions.complete(attachmentId, attachment);
    }
}
